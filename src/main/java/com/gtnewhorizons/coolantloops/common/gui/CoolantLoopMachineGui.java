package com.gtnewhorizons.coolantloops.common.gui;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphPanel;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Standard ModularUI GUI for Coolant Loop multiblocks.
 *
 * Inherits standard GT5U multiblock power panel, shutdown overlays,
 * and controls, while hiding the recipe locking button since coolant loop machines
 * do not run discrete recipes.
 *
 * If a Computer Monitor cover is installed on the Coolant Loop Pump controller,
 * adds a button that opens the interactive Coolant Loop Flow Graph panel.
 */
public class CoolantLoopMachineGui<T extends MTEMultiBlockBase> extends MTEMultiBlockBaseGui<T> {

    public CoolantLoopMachineGui(T multiblock) {
        super(multiblock);
    }

    @Override
    protected boolean shouldDisplayVoidExcess() {
        return false;
    }

    @Override
    protected boolean shouldDisplayInputSeparation() {
        return false;
    }

    @Override
    protected boolean shouldDisplayBatchMode() {
        return false;
    }

    @Override
    protected boolean shouldDisplayRecipeLock() {
        return false;
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        Flow row = super.createRightPanelGapRow(parent, syncManager);

        if (multiblock instanceof MTECoolantPump pump) {
            BooleanSyncValue monitorCoverSyncer = new BooleanSyncValue(pump::hasComputerMonitorCover);
            syncManager.syncValue("monitorCover", monitorCoverSyncer);

            row.childIf(pump.hasComputerMonitorCover(), () -> createLoopGraphButton(syncManager, parent, pump));
        }

        return row;
    }

    protected ButtonWidget<?> createLoopGraphButton(PanelSyncManager syncManager, ModularPanel parent,
        MTECoolantPump pump) {
        IPanelHandler graphPanel = syncManager.syncedPanel(
            CoolantLoopGraphPanel.PANEL_NAME,
            true,
            (psm, _) -> CoolantLoopGraphPanel.createPanel(psm, parent, pump));

        return new ButtonWidget<>().marginLeft(4)
            .overlay(new ItemDrawable(ItemList.Cover_Screen.get(1L)))
            .onMousePressed(d -> {
                if (!graphPanel.isPanelOpen()) {
                    graphPanel.openPanel();
                } else {
                    graphPanel.closePanel();
                }
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.lang("coolantloops.gui.button.loop_graph"));
                t.addLine(IKey.lang("coolantloops.gui.button.loop_graph.tooltip"));
            })
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }
}
