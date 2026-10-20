package com.gtnewhorizons.coolantloops.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;

/**
 * Controller GUI for the Coolant Bath multiblock.
 * Displays the 2D grid of items exchanging heat with the loop coolant,
 * along with real-time tooltips and click-to-eject interactions.
 */
public class CoolantBathGui extends CoolantLoopMachineGui<MTECoolantBath> {

    public CoolantBathGui(MTECoolantBath multiblock) {
        super(multiblock);
    }

    @Override
    protected int getBasePanelWidth() {
        return multiblock.mMachine ? 200 : super.getBasePanelWidth();
    }

    @Override
    protected int getBasePanelHeight() {
        return multiblock.mMachine ? 235 : super.getBasePanelHeight();
    }

    @Override
    public Flow createMainColumn(ModularPanel panel, PanelSyncManager syncManager) {
        if (!multiblock.mMachine) {
            return super.createMainColumn(panel, syncManager);
        }
        return Flow.column()
            .padding(4)
            .child(new CoolantBathGridWidget(multiblock))
            .child(createPanelGap(panel, syncManager))
            .childIf(multiblock.supportsInventoryRow(), () -> createInventoryRow(panel, syncManager));
    }
}
