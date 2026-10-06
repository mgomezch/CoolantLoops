package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static Block creativeHeatReservoir;

    public static void init() {
        creativeHeatReservoir = new BlockCreativeHeatReservoir();
        GameRegistry.registerBlock(creativeHeatReservoir, "creative_heat_reservoir");
        GameRegistry.registerTileEntity(TileEntityCreativeHeatReservoir.class, "coolantloops.creative_heat_reservoir");
    }
}
