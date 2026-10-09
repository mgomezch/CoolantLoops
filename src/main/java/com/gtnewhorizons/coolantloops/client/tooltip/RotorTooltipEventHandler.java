package com.gtnewhorizons.coolantloops.client.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.gtnewhorizons.coolantloops.common.util.CoolantLocalization;
import com.gtnewhorizons.coolantloops.common.util.RotorThermalHelper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Materials;

@SideOnly(Side.CLIENT)
public class RotorTooltipEventHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.itemStack;
        if (stack == null || stack.getItem() == null) {
            return;
        }

        if (RotorThermalHelper.isTurbineRotor(stack)) {
            Materials mat = RotorThermalHelper.getRotorMaterial(stack);
            if (mat != null) {
                double meltC = RotorThermalHelper.getMeltingPointCelsius(mat);
                double softenC = RotorThermalHelper.getThermalSofteningCelsius(mat);

                event.toolTip
                    .add(EnumChatFormatting.GRAY + CoolantLocalization.get("coolantloops.tooltip.rotor.header"));
                event.toolTip.add(
                    EnumChatFormatting.DARK_GRAY + "  "
                        + CoolantLocalization.format("coolantloops.tooltip.rotor.softening", softenC));
                event.toolTip.add(
                    EnumChatFormatting.DARK_GRAY + "  "
                        + CoolantLocalization.format("coolantloops.tooltip.rotor.melting", meltC));
            }
        }
    }
}
