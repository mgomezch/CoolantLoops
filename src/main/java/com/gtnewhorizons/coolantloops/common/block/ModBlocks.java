package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static Block creativeHeatReservoir;
    public static Block manifold;
    public static Block loopInstrument;

    public static void init() {
        creativeHeatReservoir = new BlockCreativeHeatReservoir();
        GameRegistry.registerBlock(creativeHeatReservoir, "creative_heat_reservoir");
        GameRegistry.registerTileEntity(TileEntityCreativeHeatReservoir.class, "coolantloops.creative_heat_reservoir");

        manifold = new BlockManifold();
        GameRegistry.registerBlock(manifold, "manifold");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold.class,
            "coolantloops.manifold");

        loopInstrument = new BlockLoopInstrument();
        GameRegistry.registerBlock(loopInstrument, "loop_instrument");
        GameRegistry.registerTileEntity(
            com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument.class,
            "coolantloops.loop_instrument");
    }
}
