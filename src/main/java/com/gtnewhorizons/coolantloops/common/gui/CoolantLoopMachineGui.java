package com.gtnewhorizons.coolantloops.common.gui;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
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

    private static final Pattern COORD_PATTERN = Pattern.compile("\\((-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)");

    @Override
    protected boolean shouldDisplayRecipeLock() {
        return false;
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);

        if (multiblock instanceof MTECoolantPump pump) {
            BooleanSyncValue loopFormedSyncer = new BooleanSyncValue(pump::isLoopFormed);
            StringSyncValue loopStatusSyncer = new StringSyncValue(pump::getLoopStatus);
            DoubleSyncValue pumpPressureSyncer = new DoubleSyncValue(pump::getPumpMechanicalPressureBar);
            DoubleSyncValue pumpFlowSyncer = new DoubleSyncValue(pump::getFlowRateLitersPerSecond);
            DoubleSyncValue pumpTempSyncer = new DoubleSyncValue(pump::getPumpFluidTemperatureCelsius);
            syncManager.syncValue("loopFormed", loopFormedSyncer);
            syncManager.syncValue("loopStatus", loopStatusSyncer);
            syncManager.syncValue("pumpPressure", pumpPressureSyncer);
            syncManager.syncValue("pumpFlow", pumpFlowSyncer);
            syncManager.syncValue("pumpTemp", pumpTempSyncer);

            IntSyncValue errorIDSyncer = syncManager.findSyncHandler("errorID", IntSyncValue.class);

            Supplier<Boolean> showLoopDiagnostic = () -> {
                boolean structureComplete = (errorIDSyncer == null) || ((errorIDSyncer.getValue() & 64) == 0);
                return structureComplete && !loopFormedSyncer.getValue();
            };

            // Incomplete coolant loop header (styled like ShutDownReasonRegistry.STRUCTURE_INCOMPLETE)
            list.child(
                IKey.lang("coolantloops.gui.text.loop_incomplete")
                    .asWidget()
                    .fullWidth()
                    .marginBottom(2)
                    .setEnabledIf(w -> showLoopDiagnostic.get()));

            DynamicSyncHandler loopDiagSyncer = new DynamicSyncHandler().widgetProvider((sm, packet) -> {
                String status = loopStatusSyncer.getValue();
                if (status == null || status.isEmpty()) {
                    status = StatCollector.translateToLocal("coolantloops.pump.status.waiting");
                }

                Flow row = Flow.row()
                    .fullWidth()
                    .coverChildrenHeight(0)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .marginBottom(2);

                Matcher m = COORD_PATTERN.matcher(status);
                if (m.find()) {
                    try {
                        int hx = Integer.parseInt(m.group(1));
                        int hy = Integer.parseInt(m.group(2));
                        int hz = Integer.parseInt(m.group(3));
                        row.child(createHighlightButton(hx, hy, hz));
                    } catch (NumberFormatException ignored) {}
                }

                row.child(
                    IKey.str("• " + status)
                        .color(0xFFE0E0E0)
                        .alignment(Alignment.TopLeft)
                        .asWidget()
                        .expanded());

                return row.setEnabledIf(w -> showLoopDiagnostic.get());
            })
                .allowC2S();

            loopStatusSyncer.setChangeListener(() -> loopDiagSyncer.notifyUpdate(packet -> {}));
            loopFormedSyncer.setChangeListener(() -> loopDiagSyncer.notifyUpdate(packet -> {}));
            if (errorIDSyncer != null) {
                errorIDSyncer.setChangeListener(() -> loopDiagSyncer.notifyUpdate(packet -> {}));
            }

            list.child(
                new DynamicSyncedWidget<>().widthRel(0.95f)
                    .coverChildrenHeight(0)
                    .syncHandler(loopDiagSyncer)
                    .setEnabledIf(w -> showLoopDiagnostic.get()));

            Supplier<Boolean> showLoopStats = () -> {
                boolean structureComplete = (errorIDSyncer == null) || ((errorIDSyncer.getValue() & 64) == 0);
                return structureComplete && loopFormedSyncer.getValue();
            };

            list.child(
                IKey.dynamic(
                    () -> String
                        .format(Locale.US, "Pump mechanical pressure: %.2f bar", pumpPressureSyncer.getDoubleValue()))
                    .color(Color.rgb(255, 255, 85))
                    .asWidget()
                    .textAlign(Alignment.CenterLeft)
                    .setEnabledIf(w -> showLoopStats.get()));

            list.child(
                IKey.dynamic(() -> String.format(Locale.US, "Flow rate: %.1f L/s", pumpFlowSyncer.getDoubleValue()))
                    .color(Color.rgb(85, 255, 85))
                    .asWidget()
                    .textAlign(Alignment.CenterLeft)
                    .setEnabledIf(w -> showLoopStats.get()));

            list.child(
                IKey.dynamic(
                    () -> String.format(Locale.US, "Coolant temperature: %.1f °C", pumpTempSyncer.getDoubleValue()))
                    .color(Color.rgb(255, 85, 85))
                    .asWidget()
                    .textAlign(Alignment.CenterLeft)
                    .setEnabledIf(w -> showLoopStats.get()));
        }

        return list;
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
