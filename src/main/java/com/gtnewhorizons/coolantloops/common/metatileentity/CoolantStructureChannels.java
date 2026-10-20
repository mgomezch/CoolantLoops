package com.gtnewhorizons.coolantloops.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.structure.CoolantPumpStructure;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.structure.IStructureChannels;

public enum CoolantStructureChannels implements IStructureChannels {

    STRUCTURE_WIDTH("width", "Tank width (3, 5, 7, 9)"),
    STRUCTURE_HEIGHT("height", "Tank height (4 to 8)"),
    TANK_MATERIAL("tank_material", "Railcraft tank material");

    private final String channel;
    private final String defaultTooltip;

    CoolantStructureChannels(String aChannel, String defaultTooltip) {
        this.channel = aChannel;
        this.defaultTooltip = defaultTooltip;
    }

    @Override
    public String get() {
        return channel;
    }

    @Override
    public String getDefaultTooltip() {
        return defaultTooltip;
    }

    @Override
    public void registerAsIndicator(ItemStack indicator, int channelValue) {
        try {
            StructureLibAPI.registerChannelItem(get(), "coolantloops", channelValue, indicator);
        } catch (Throwable ignored) {}
    }

    @Override
    public int getValueClamped(ItemStack trigger, int min, int max) {
        if (trigger == null) {
            return min;
        }
        int raw = ChannelDataAccessor.getChannelData(trigger, get());
        return Math.max(min, Math.min(max, raw));
    }

    public static void register() {
        for (CoolantStructureChannels value : values()) {
            try {
                StructureLibAPI
                    .registerChannelDescription(value.get(), "coolantloops", "channels.coolantloops." + value.get());
            } catch (Throwable ignored) {}
        }

        // Register indicator items for STRUCTURE_WIDTH (3, 5, 7, 9)
        try {
            if (GregTechAPI.sBlockCasings2 != null) {
                for (int w : new int[] { 3, 5, 7, 9 }) {
                    STRUCTURE_WIDTH.registerAsIndicator(new ItemStack(GregTechAPI.sBlockCasings2, w, 15), w);
                }
            }
        } catch (Throwable ignored) {}

        // Register indicator items for STRUCTURE_HEIGHT (4 to 8)
        try {
            if (GregTechAPI.sBlockCasings2 != null) {
                for (int h = 4; h <= 8; h++) {
                    STRUCTURE_HEIGHT.registerAsIndicator(new ItemStack(GregTechAPI.sBlockCasings2, h, 15), h);
                }
            }
        } catch (Throwable ignored) {}

        // Register indicator items for TANK_MATERIAL (Cast Iron, Steel, Stainless, Titanium, Tungstensteel, Neutronium)
        try {
            for (int tier = 0; tier <= 5; tier++) {
                Materials mat = CoolantPumpStructure.getMaterialForTier(tier);
                ItemStack stack = CoolantPumpStructure.getTankWallItem(mat);
                if (stack != null && stack.getItem() != null) {
                    TANK_MATERIAL.registerAsIndicator(stack.copy(), tier);
                }
            }
        } catch (Throwable ignored) {}
    }
}
