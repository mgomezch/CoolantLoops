package com.gtnewhorizons.coolantloops.common.gui;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Standard ModularUI GUI for Coolant Loop multiblocks.
 *
 * Inherits standard GT5U multiblock power panel, shutdown overlays,
 * and controls, while hiding the recipe locking button since coolant loop machines
 * do not run discrete recipes.
 */
public class CoolantLoopMachineGui<T extends MTEMultiBlockBase> extends MTEMultiBlockBaseGui<T> {

    public CoolantLoopMachineGui(T multiblock) {
        super(multiblock);
    }

    @Override
    protected boolean shouldDisplayRecipeLock() {
        return false;
    }
}
