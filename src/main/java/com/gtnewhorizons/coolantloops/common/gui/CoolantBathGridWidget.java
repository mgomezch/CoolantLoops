package com.gtnewhorizons.coolantloops.common.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.gtnewhorizons.coolantloops.CoolantLoops;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantBathSlot;
import com.gtnewhorizons.coolantloops.network.PacketEjectCoolantBathSlot;
import com.gtnewhorizons.coolantloops.network.PacketSyncCoolantBath;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Grid widget displaying the 2D layout of items transforming inside the Coolant Bath.
 * Supports tooltip inspection and click-to-eject functionality.
 */
public class CoolantBathGridWidget extends Widget<CoolantBathGridWidget> implements Interactable {

    private final MTECoolantBath multiblock;
    private static final int CELL_SIZE = 18;

    public CoolantBathGridWidget(MTECoolantBath multiblock) {
        this.multiblock = multiblock;
        size(180, 130);
        tooltipDynamic(this::buildHoveredTooltip);
        tooltipAutoUpdate(true);
        tooltipShowUpTimer(0);
    }

    public int getGridCols() {
        return multiblock.mInteriorWidth > 0 ? multiblock.mInteriorWidth : 3;
    }

    public int getGridRows() {
        return multiblock.mInteriorLength > 0 ? multiblock.mInteriorLength : 5;
    }

    public int getSlotIndexUnderCursor() {
        ModularGuiContext context = getContext();
        if (context == null) return -1;
        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();
        Area area = getArea();
        if (area == null || !area.isInside(mouseX, mouseY)) return -1;

        int cols = getGridCols();
        int rows = getGridRows();
        int totalWidth = cols * CELL_SIZE;
        int totalHeight = rows * CELL_SIZE;
        int startX = area.x() + (area.w() - totalWidth) / 2;
        int startY = area.y() + (area.h() - totalHeight) / 2;

        int relX = mouseX - startX;
        int relY = mouseY - startY;

        if (relX < 0 || relY < 0 || relX >= totalWidth || relY >= totalHeight) return -1;

        int col = relX / CELL_SIZE;
        int row = relY / CELL_SIZE;
        return row * cols + col;
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        int slotIndex = getSlotIndexUnderCursor();
        if (slotIndex >= 0) {
            IGregTechTileEntity bte = multiblock.getBaseMetaTileEntity();
            if (bte != null) {
                CoolantLoops.network.sendToServer(
                    new PacketEjectCoolantBathSlot(bte.getXCoord(), bte.getYCoord(), bte.getZCoord(), slotIndex));
                Interactable.playButtonClickSound();
                return Interactable.Result.SUCCESS;
            }
        }
        return Interactable.Result.IGNORE;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        super.draw(context, theme);

        Area area = getArea();
        if (area == null) return;

        int cols = getGridCols();
        int rows = getGridRows();
        int totalWidth = cols * CELL_SIZE;
        int totalHeight = rows * CELL_SIZE;
        int startX = area.x() + (area.w() - totalWidth) / 2;
        int startY = area.y() + (area.h() - totalHeight) / 2;

        // Draw background container panel
        GuiDraw.drawRect(startX - 2, startY - 2, totalWidth + 4, totalHeight + 4, 0xFF22262B);
        GuiDraw.drawRect(startX - 1, startY - 1, totalWidth + 2, totalHeight + 2, 0xFF15181C);

        int hoveredSlot = getSlotIndexUnderCursor();
        int z = context != null ? context.getCurrentDrawingZ() : 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                int px = startX + c * CELL_SIZE;
                int py = startY + r * CELL_SIZE;

                // Slot border & background
                GuiDraw.drawRect(px, py, CELL_SIZE, CELL_SIZE, 0xFF374151);
                GuiDraw.drawRect(px + 1, py + 1, CELL_SIZE - 2, CELL_SIZE - 2, 0xFF1F2937);

                ItemStack stack = getSlotStack(idx);
                if (stack != null) {
                    GuiDraw.drawItem(stack, px + 1, py + 1, 16, 16, z + 1);

                    // Progress indicator bar along bottom of cell
                    float progress = getSlotProgress(idx);
                    if (progress > 0.0f) {
                        int barW = Math.max(1, Math.round((CELL_SIZE - 2) * Math.min(1.0f, progress)));
                        GuiDraw.drawRect(px + 1, py + CELL_SIZE - 3, barW, 2, 0xFF10B981);
                    }
                }

                // Highlight hovered slot
                if (idx == hoveredSlot) {
                    GuiDraw.drawRect(px + 1, py + 1, CELL_SIZE - 2, CELL_SIZE - 2, 0x55FFFFFF);
                }
            }
        }
    }

    private ItemStack getSlotStack(int index) {
        if (multiblock.clientSlots != null && index >= 0 && index < multiblock.clientSlots.length) {
            PacketSyncCoolantBath.SlotSyncData s = multiblock.clientSlots[index];
            return s != null ? s.stack : null;
        }
        if (multiblock.mSlots != null && index >= 0 && index < multiblock.mSlots.length) {
            CoolantBathSlot s = multiblock.mSlots[index];
            return s != null ? s.itemStack : null;
        }
        return null;
    }

    private float getSlotProgress(int index) {
        if (multiblock.clientSlots != null && index >= 0 && index < multiblock.clientSlots.length) {
            PacketSyncCoolantBath.SlotSyncData s = multiblock.clientSlots[index];
            return s != null ? s.progress : 0.0f;
        }
        if (multiblock.mSlots != null && index >= 0 && index < multiblock.mSlots.length) {
            CoolantBathSlot s = multiblock.mSlots[index];
            return s != null ? (float) s.getProgressFraction() : 0.0f;
        }
        return 0.0f;
    }

    private void buildHoveredTooltip(RichTooltip tooltip) {
        int slotIndex = getSlotIndexUnderCursor();
        if (slotIndex < 0) return;

        List<String> list = getHoveredTooltipLines(slotIndex);
        tooltip.addStringLines(list);
    }

    public List<String> getHoveredTooltipLines(int slotIndex) {
        List<String> list = new ArrayList<>();
        if (slotIndex < 0) return list;

        ItemStack stack = getSlotStack(slotIndex);
        if (stack == null) {
            list.add(EnumChatFormatting.DARK_GRAY + "Empty Bath Slot #" + (slotIndex + 1));
            return list;
        }

        list.add(EnumChatFormatting.WHITE + stack.getDisplayName());

        double currentTemp = 20.0;
        double targetTemp = 20.0;
        double progress = 0.0;
        boolean isCell = false;
        boolean cooling = true;

        if (multiblock.clientSlots != null && slotIndex < multiblock.clientSlots.length) {
            PacketSyncCoolantBath.SlotSyncData s = multiblock.clientSlots[slotIndex];
            if (s != null) {
                currentTemp = s.currentTemp;
                targetTemp = s.targetTemp;
                progress = s.progress;
                isCell = s.isCoolantCell;
                cooling = s.cooling;
            }
        } else if (multiblock.mSlots != null && slotIndex < multiblock.mSlots.length) {
            CoolantBathSlot s = multiblock.mSlots[slotIndex];
            if (s != null) {
                currentTemp = s.currentTemp;
                targetTemp = s.targetTemp;
                progress = s.getProgressFraction();
                isCell = s.isCoolantCell;
                cooling = s.cooling;
            }
        }

        String tempColor = currentTemp > 500.0 ? EnumChatFormatting.RED.toString()
            : (currentTemp > 100.0 ? EnumChatFormatting.YELLOW.toString() : EnumChatFormatting.AQUA.toString());

        list.add(EnumChatFormatting.GRAY + "Temperature: " + tempColor + String.format("%.1f \u00B0C", currentTemp));
        list.add(
            EnumChatFormatting.GRAY + "Target: " + EnumChatFormatting.GOLD + String.format("%.1f \u00B0C", targetTemp));
        list.add(
            EnumChatFormatting.GRAY + "Mode: "
                + (cooling ? EnumChatFormatting.BLUE + "Cooling Down" : EnumChatFormatting.GOLD + "Heating Up"));
        list.add(
            EnumChatFormatting.GRAY + "Progress: "
                + EnumChatFormatting.GREEN
                + String.format("%.1f%%", progress * 100.0));

        if (isCell) {
            list.add(
                EnumChatFormatting.DARK_AQUA + "Coolant Cell: "
                    + (stack.getMaxDamage() - stack.getItemDamage())
                    + " / "
                    + stack.getMaxDamage()
                    + " durability");
        }

        list.add("");
        list.add(EnumChatFormatting.DARK_GRAY + "" + EnumChatFormatting.ITALIC + "[Click to Eject to Output Bus]");
        return list;
    }
}
