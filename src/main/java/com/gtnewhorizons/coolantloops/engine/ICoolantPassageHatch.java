package com.gtnewhorizons.coolantloops.engine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Interface for multiblock passage hatches (such as Nuclear Core High-Pressure Hatches)
 * that route coolant through a multiblock internal column/channel from an inlet to an outlet.
 */
public interface ICoolantPassageHatch extends ICoolantLoopDevice {

    /**
     * Gets the tile entity of the opposite paired hatch (e.g. bottom outlet for a top inlet).
     */
    IGregTechTileEntity getOppositeHatchTile();

    /**
     * Returns true if this hatch is the entry/inlet point of the passage, false if it is the exit/outlet.
     */
    boolean isPassageInlet();

    /**
     * Associates the active loop pump with this passage during loop graph crawling.
     */
    void setConnectedPump(ICoolantLoopPump pump);

    /**
     * Gets the active loop pump associated with this passage, or null if loop is disconnected.
     */
    ICoolantLoopPump getConnectedPump();
}
