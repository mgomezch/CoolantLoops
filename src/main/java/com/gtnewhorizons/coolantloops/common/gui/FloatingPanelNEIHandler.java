package com.gtnewhorizons.coolantloops.common.gui;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiContainerAccessor;
import com.cleanroommc.modularui.screen.ClientScreenHandler;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.sizer.Area;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Bridges ModularUI floating panels (such as Coolant Loop Pump graphs) with Not Enough Items (NEI).
 * <p>
 * Ensures floating panels take precedence over NEI widgets:
 * 1. Suppresses NEI widgets (utility buttons, bookmark panel, item panel, subset dropdown, search section)
 * when covered by an open or maximized floating panel, preventing NEI from rendering over floating panel controls.
 * 2. Captures mouse clicks, dragging, and scrolling inside floating panel bounds with higher priority so NEI
 * never intercepts clicks intended for floating panel controls.
 * 3. Suppresses NEI tooltips and hotkeys when hovering over floating panels.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class FloatingPanelNEIHandler implements INEIGuiHandler, IContainerInputHandler, IContainerTooltipHandler {

    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) return;
        try {
            if (FMLCommonHandler.instance() == null || FMLCommonHandler.instance()
                .getSide() == null
                || !FMLCommonHandler.instance()
                    .getSide()
                    .isClient()) {
                return;
            }
            if (!Loader.isModLoaded("NotEnoughItems")) {
                return;
            }
            registered = true;
            FloatingPanelNEIHandler handler = new FloatingPanelNEIHandler();
            API.registerNEIGuiHandler(handler);
            if (!GuiContainerManager.inputHandlers.contains(handler)) {
                GuiContainerManager.inputHandlers.addFirst(handler);
            }
            GuiContainerManager.addTooltipHandler(handler);
        } catch (Throwable ignored) {}
    }

    public static ModularScreen getModularScreen(GuiContainer gui) {
        try {
            if (FMLCommonHandler.instance() != null && FMLCommonHandler.instance()
                .getSide() != null
                && FMLCommonHandler.instance()
                    .getSide()
                    .isClient()) {
                return ClientScreenHandler.getMuiScreen();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getGuiLeft(GuiContainer gui) {
        if (gui instanceof GuiContainerAccessor) {
            return ((GuiContainerAccessor) gui).getGuiLeft();
        }
        return (gui.width - 176) / 2;
    }

    public static int getGuiTop(GuiContainer gui) {
        if (gui instanceof GuiContainerAccessor) {
            return ((GuiContainerAccessor) gui).getGuiTop();
        }
        return (gui.height - 166) / 2;
    }

    public static int getXSize(GuiContainer gui) {
        if (gui instanceof GuiContainerAccessor) {
            return ((GuiContainerAccessor) gui).getXSize();
        }
        return 176;
    }

    public static int getYSize(GuiContainer gui) {
        if (gui instanceof GuiContainerAccessor) {
            return ((GuiContainerAccessor) gui).getYSize();
        }
        return 166;
    }

    public static boolean isFloatingPanel(ModularPanel panel) {
        if (panel == null) return false;
        if (panel instanceof ResizeableFloatingPanel) return true;
        return "ResizeableFloatingPanel".equals(
            panel.getClass()
                .getSimpleName());
    }

    public static Area getPanelArea(ModularPanel panel) {
        if (panel instanceof ResizeableFloatingPanel) {
            return ((ResizeableFloatingPanel) panel).getEffectiveArea();
        }
        return panel.getArea();
    }

    public static boolean isMaximized(ModularPanel panel, GuiContainer gui) {
        if (panel instanceof ResizeableFloatingPanel) {
            if (((ResizeableFloatingPanel) panel).getCurrentSnap() == ResizeableFloatingPanel.SnapMode.MAXIMIZED) {
                return true;
            }
        }
        Area a = panel.getArea();
        return a != null && a.width >= gui.width - 4 && a.height >= gui.height - 4;
    }

    public static boolean intersects(Area a, int x, int y, int w, int h) {
        if (a == null || a.width <= 0 || a.height <= 0 || w <= 0 || h <= 0) {
            return false;
        }
        return x < a.x + a.width && x + w > a.x && y < a.y + a.height && y + h > a.y;
    }

    public static boolean isInsideFloatingPanel(GuiContainer gui, int mouseX, int mouseY) {
        ModularScreen screen = getModularScreen(gui);
        if (screen == null || screen.getPanelManager() == null) {
            return false;
        }

        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) {
            if (isFloatingPanel(panel) && panel.isOpen()) {
                Area area = getPanelArea(panel);
                if (area != null && area.width > 0 && area.height > 0) {
                    if (mouseX >= area.x && mouseX < area.x + area.width
                        && mouseY >= area.y
                        && mouseY < area.y + area.height) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData visiblity) {
        ModularScreen screen = getModularScreen(gui);
        if (screen == null || screen.getPanelManager() == null) {
            return visiblity;
        }

        int guiLeft = getGuiLeft(gui);
        int guiTop = getGuiTop(gui);
        int xSize = getXSize(gui);
        int ySize = getYSize(gui);

        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) {
            if (isFloatingPanel(panel) && panel.isOpen()) {
                Area area = getPanelArea(panel);
                if (area == null || area.width <= 0 || area.height <= 0) {
                    continue;
                }

                // If maximized or covering full screen: completely hide NEI
                if (isMaximized(panel, gui)) {
                    visiblity.showNEI = false;
                    visiblity.showUtilityButtons = false;
                    visiblity.showBookmarkPanel = false;
                    visiblity.showItemPanel = false;
                    visiblity.showSubsetDropdown = false;
                    visiblity.showSearchSection = false;
                    visiblity.showWidgets = false;
                    return visiblity;
                }

                // 1. Top-left utility buttons (x from 0 to guiLeft or 120, y from 0 to 120)
                int utilW = Math.max(120, guiLeft);
                int utilH = Math.max(120, guiTop + 80);
                if (intersects(area, 0, 0, utilW, utilH)) {
                    visiblity.showUtilityButtons = false;
                    visiblity.showWidgets = false;
                }

                // 2. Bookmarks panel on left
                int bookW = Math.max(30, guiLeft);
                if (intersects(area, 0, 0, bookW, gui.height)) {
                    visiblity.showBookmarkPanel = false;
                }

                // 3. Item panel on right
                int itemX = guiLeft + xSize;
                int itemW = Math.max(30, gui.width - itemX);
                if (intersects(area, itemX, 0, itemW, gui.height)) {
                    visiblity.showItemPanel = false;
                }

                // 4. Subset dropdown at top center
                int dropH = Math.max(25, guiTop);
                if (intersects(area, guiLeft, 0, xSize, dropH)) {
                    visiblity.showSubsetDropdown = false;
                }

                // 5. Search section at bottom center
                int searchY = guiTop + ySize;
                int searchH = Math.max(25, gui.height - searchY);
                if (intersects(area, guiLeft, searchY, xSize, searchH)) {
                    visiblity.showSearchSection = false;
                }
            }
        }

        return visiblity;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.emptyList();
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack item, int button) {
        return isInsideFloatingPanel(gui, mouseX, mouseY);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        ModularScreen screen = getModularScreen(gui);
        if (screen == null || screen.getPanelManager() == null) {
            return false;
        }

        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) {
            if (isFloatingPanel(panel) && panel.isOpen()) {
                Area area = getPanelArea(panel);
                if (area != null && intersects(area, x, y, w, h)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {
        return isInsideFloatingPanel(gui, mouseX, mouseY);
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {
        return isInsideFloatingPanel(gui, mouseX, mouseY);
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mouseX, int mouseY, int button, long heldTime) {}

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mouseX, int mouseY, List<String> currenttip) {
        if (isInsideFloatingPanel(gui, mouseX, mouseY)) {
            return Collections.emptyList();
        }
        return currenttip;
    }

    @Override
    public List<String> handleItemDisplayName(GuiContainer gui, ItemStack itemstack, List<String> currenttip) {
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mouseX, int mouseY,
        List<String> currenttip) {
        if (isInsideFloatingPanel(gui, mouseX, mouseY)) {
            return Collections.emptyList();
        }
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mouseX, int mouseY, Map<String, String> hotkeys) {
        if (isInsideFloatingPanel(gui, mouseX, mouseY)) {
            return Collections.emptyMap();
        }
        return hotkeys;
    }
}
