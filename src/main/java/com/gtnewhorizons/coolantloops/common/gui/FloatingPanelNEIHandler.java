package com.gtnewhorizons.coolantloops.common.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiContainerAccessor;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ClientScreenHandler;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Bridges ModularUI floating panels (such as Coolant Loop Pump graphs) with Not Enough Items (NEI).
 * <p>
 * Ensures floating panels take precedence over NEI widgets:
 * 1. Renders floating panels on top of NEI widgets via {@link IContainerDrawHandler#postRenderObjects},
 * keeping NEI fully visible in the background without jarring visibility pop-in/pop-out.
 * 2. Captures mouse clicks, dragging, and scrolling inside floating panel bounds with higher priority
 * so NEI never intercepts clicks intended for floating panel controls.
 * 3. Suppresses NEI tooltips and hotkeys when hovering over floating panels.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class FloatingPanelNEIHandler
    implements INEIGuiHandler, IContainerInputHandler, IContainerTooltipHandler, IContainerDrawHandler {

    private static boolean registered = false;
    private static volatile boolean normalDrawingScreen = false;
    private static volatile boolean drawingOverNEI = false;
    private static long renderFrameId = 0;

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
            if (!GuiContainerManager.drawHandlers.contains(handler)) {
                GuiContainerManager.addDrawHandler(handler);
            }
            MinecraftForge.EVENT_BUS.register(handler);

            // Also register peer handler in modularnuclear package if present
            try {
                Class<?> peer = Class.forName("com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler");
                if (peer != FloatingPanelNEIHandler.class) {
                    peer.getMethod("register")
                        .invoke(null);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static boolean isNEIActive() {
        try {
            if (!Loader.isModLoaded("NotEnoughItems")) return false;
            return NEIClientConfig.isEnabled() && !NEIClientConfig.isHidden();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isRecipeViewerActive(ModularScreen screen) {
        if (screen == null) return false;
        try {
            ModularGuiContext ctx = screen.getContext();
            if (ctx != null && ctx.getRecipeViewerSettings() != null) {
                return ctx.getRecipeViewerSettings()
                    .isEnabled(screen);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean isNormalDrawingScreen() {
        return normalDrawingScreen;
    }

    public static boolean isDrawingOverNEI() {
        return drawingOverNEI;
    }

    public static boolean isSuppressingNormalRender() {
        if (!isNEIActive() || !normalDrawingScreen || drawingOverNEI) {
            return false;
        }
        ModularScreen screen = ClientScreenHandler.getMuiScreen();
        return isRecipeViewerActive(screen);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        renderFrameId++;
        normalDrawingScreen = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        normalDrawingScreen = false;
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
        try {
            java.lang.reflect.Method m = panel.getClass()
                .getMethod("getEffectiveArea");
            return (Area) m.invoke(panel);
        } catch (Throwable ignored) {}
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

    // --- IContainerDrawHandler implementation ---

    @Override
    public void onPreDraw(GuiContainer gui) {}

    @Override
    public void renderObjects(GuiContainer gui, int mouseX, int mouseY) {
        normalDrawingScreen = false;
    }

    @Override
    public void postRenderObjects(GuiContainer gui, int mouseX, int mouseY) {
        normalDrawingScreen = false;
        renderFloatingPanels(gui, mouseX, mouseY);
    }

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}

    @Override
    public void renderSlotOverlay(GuiContainer gui, Slot slot) {}

    public static void renderFloatingPanels(GuiContainer gui, int mouseX, int mouseY) {
        ModularScreen screen = getModularScreen(gui);
        if (screen == null || screen.getPanelManager() == null) {
            return;
        }

        List<ModularPanel> openPanels = screen.getPanelManager()
            .getOpenPanels();
        if (openPanels == null || openPanels.isEmpty()) {
            return;
        }

        boolean hasFloating = false;
        for (ModularPanel panel : openPanels) {
            if (isFloatingPanel(panel) && panel.isOpen()) {
                hasFloating = true;
                break;
            }
        }
        if (!hasFloating) {
            return;
        }

        ModularGuiContext context = screen.getContext();
        if (context == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        drawingOverNEI = true;
        try {
            GlStateManager.disableRescaleNormal();
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            context.reset();
            context.pushViewport(null, context.getScreenArea());

            for (ModularPanel panel : screen.getPanelManager()
                .getReverseOpenPanels()) {
                if (isFloatingPanel(panel) && panel.isOpen()) {
                    if (isAlreadyRenderedThisFrame(panel, renderFrameId)) {
                        continue;
                    }
                    markRenderedThisFrame(panel, renderFrameId);

                    context.updateZ(0);
                    if (panel.disablePanelsBelow()) {
                        float alpha = panel.getAlpha();
                        int color = Color.argb(16, 16, 16, (int) (125.0f * alpha));
                        GuiDraw.drawRect(
                            0,
                            0,
                            context.getScreenArea()
                                .w(),
                            context.getScreenArea()
                                .h(),
                            color);
                    }
                    WidgetTree.drawTree(panel, context);
                    GlStateManager.clearDepth(1.0);
                    GlStateManager.clear(256);
                }
            }

            context.updateZ(0);
            context.popViewport(null);

            if (context.postRenderCallbacks != null && !context.postRenderCallbacks.isEmpty()) {
                List<Consumer<ModularGuiContext>> callbacks = new ArrayList<>(context.postRenderCallbacks);
                context.postRenderCallbacks.clear();
                for (Consumer<ModularGuiContext> cb : callbacks) {
                    cb.accept(context);
                }
            }
        } catch (Throwable ignored) {} finally {
            drawingOverNEI = false;
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static boolean isAlreadyRenderedThisFrame(ModularPanel panel, long frameId) {
        if (panel instanceof ResizeableFloatingPanel) {
            return ((ResizeableFloatingPanel) panel).getLastRenderFrameId() == frameId;
        }
        try {
            java.lang.reflect.Method m = panel.getClass()
                .getMethod("getLastRenderFrameId");
            return ((Long) m.invoke(panel)) == frameId;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void markRenderedThisFrame(ModularPanel panel, long frameId) {
        if (panel instanceof ResizeableFloatingPanel) {
            ((ResizeableFloatingPanel) panel).setLastRenderFrameId(frameId);
            return;
        }
        try {
            java.lang.reflect.Method m = panel.getClass()
                .getMethod("setLastRenderFrameId", long.class);
            m.invoke(panel, frameId);
        } catch (Throwable ignored) {}
    }

    // --- INEIGuiHandler implementation ---

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData visiblity) {
        // Keep NEI visible in background; floating panels render over NEI cleanly.
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
        return false;
    }

    // --- IContainerInputHandler implementation ---

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
        ModularScreen screen = getModularScreen(gui);
        if (screen != null && screen.getContext() != null) {
            ModularGuiContext ctx = screen.getContext();
            if (ctx.isFocused()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    // --- IContainerTooltipHandler implementation ---

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
