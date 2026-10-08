package com.gtnewhorizons.coolantloops.common.projectred;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;

import cpw.mods.fml.common.Loader;
import mrtjp.projectred.api.IBundledTileInteraction;
import mrtjp.projectred.api.ProjectRedAPI;

public class ProjectRedIntegration {

    public static void register() {
        if (Loader.isModLoaded("ProjRed|Transmission") && ProjectRedAPI.transmissionAPI != null) {
            try {
                ProjectRedAPI.transmissionAPI.registerBundledTileInteraction(new IBundledTileInteraction() {

                    @Override
                    public boolean isValidInteractionFor(World world, int x, int y, int z) {
                        return world.getTileEntity(x, y, z) instanceof TileEntityLoopInstrument;
                    }

                    @Override
                    public boolean canConnectBundled(World world, int x, int y, int z, int side) {
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te instanceof TileEntityLoopInstrument inst) {
                            return side == inst.getFacing()
                                .ordinal();
                        }
                        return false;
                    }

                    @Override
                    public byte[] getBundledSignal(World world, int x, int y, int z, int side) {
                        TileEntity te = world.getTileEntity(x, y, z);
                        if (te instanceof TileEntityLoopInstrument inst) {
                            if (side == inst.getFacing()
                                .ordinal()) {
                                return inst.getBundledSignal(side);
                            }
                        }
                        return null;
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
