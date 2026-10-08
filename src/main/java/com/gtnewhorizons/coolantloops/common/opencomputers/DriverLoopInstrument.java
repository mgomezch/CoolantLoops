package com.gtnewhorizons.coolantloops.common.opencomputers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;

/**
 * OpenComputers Driver for the Pressurized Instrumentation Computer.
 */
public class DriverLoopInstrument extends DriverSidedTileEntity {

    @Override
    public Class<?> getTileEntityClass() {
        return TileEntityLoopInstrument.class;
    }

    @Override
    public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityLoopInstrument;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument inst) {
            return new LoopInstrumentEnvironment(inst);
        }
        return null;
    }
}
