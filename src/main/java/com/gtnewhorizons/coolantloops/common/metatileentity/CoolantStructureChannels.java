package com.gtnewhorizons.coolantloops.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;

import gregtech.api.structure.IStructureChannels;

public enum CoolantStructureChannels implements IStructureChannels {

    STRUCTURE_WIDTH("width", "Structure Width (3, 5, 7, 9)"),
    STRUCTURE_HEIGHT("height", "Structure Height (4 to 8)");

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
    }
}
