package com.gtnewhorizons.coolantloops.common.gui.graph;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.coolantloops.common.gui.LockIconWidget;
import com.gtnewhorizons.coolantloops.common.gui.PanelDragHandle;
import com.gtnewhorizons.coolantloops.common.gui.ResizeableFloatingPanel;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

/**
 * Builds the floating, movable, resizable Coolant Loop Graph window.
 */
public class CoolantLoopGraphPanel {

    public static final String PANEL_NAME = "coolantLoopGraphPanel";

    public static ModularPanel createPanel(PanelSyncManager syncManager, ModularPanel parent, MTECoolantPump pump) {
        ModularPanel panel = new ResizeableFloatingPanel(PANEL_NAME, parent).relativeToScreen()
            .center()
            .size(380, 250)
            .resizeableOnDrag(true);

        CoolantLoopGraphWidget graphWidget = new CoolantLoopGraphWidget();

        // Server-client network sync of loop graph data JSON
        StringSyncValue graphDataSyncer = new StringSyncValue(pump::getLoopGraphDataJson, json -> {
            CoolantLoopGraphData data = CoolantLoopGraphData.fromJson(json);
            graphWidget.setGraphData(data);
        });
        syncManager.syncValue("loopGraphJson", graphDataSyncer);

        // Initial setup on opening
        graphWidget.setGraphData(CoolantLoopGraphData.fromJson(pump.getLoopGraphDataJson()));

        // Title bar header with drag handle and zoom controls
        Flow headerRow = Flow.row()
            .fullWidth()
            .height(18)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .marginBottom(3)
            // Draggable header area
            .child(
                new PanelDragHandle(panel).expanded()
                    .height(14))
            // Zoom Out button
            .child(
                new ButtonWidget<>().size(14, 14)
                    .marginRight(2)
                    .child(
                        IKey.str("-")
                            .asWidget()
                            .center())
                    .tooltipBuilder(t -> t.addLine("Zoom out"))
                    .onMousePressed(d -> {
                        graphWidget.zoomOut();
                        return true;
                    }))
            // Zoom level display (also draggable)
            .child(
                new PanelDragHandle(panel).title(IKey.dynamic(graphWidget::getZoomString))
                    .color(Color.WHITE.main)
                    .textAlign(Alignment.Center)
                    .size(36, 14)
                    .marginRight(2))
            // Zoom In button
            .child(
                new ButtonWidget<>().size(14, 14)
                    .marginRight(3)
                    .child(
                        IKey.str("+")
                            .asWidget()
                            .center())
                    .tooltipBuilder(t -> t.addLine("Zoom in"))
                    .onMousePressed(d -> {
                        graphWidget.zoomIn();
                        return true;
                    }))
            // Reset zoom button (1:1)
            .child(
                new ButtonWidget<>().size(18, 14)
                    .marginRight(3)
                    .child(
                        IKey.str("1:1")
                            .asWidget()
                            .center()
                            .scale(0.8f))
                    .tooltipBuilder(t -> t.addLine("Reset zoom (1:1)"))
                    .onMousePressed(d -> {
                        graphWidget.resetZoom();
                        return true;
                    }))
            // Fit to view button
            .child(
                new ButtonWidget<>().size(20, 14)
                    .marginRight(2)
                    .child(
                        IKey.str("Fit")
                            .asWidget()
                            .center()
                            .scale(0.8f))
                    .tooltipBuilder(t -> t.addLine("Fit graph to window"))
                    .onMousePressed(d -> {
                        graphWidget.fitToView();
                        return true;
                    }))
            // Auto-fit lock toggle button
            .child(
                new ButtonWidget<>().size(14, 14)
                    .marginRight(6)
                    .child(new LockIconWidget(graphWidget::isAutoFitLocked).center())
                    .tooltipBuilder(t -> {
                        if (graphWidget.isAutoFitLocked()) {
                            t.addLine(EnumChatFormatting.YELLOW + "Auto-fit on resize: locked");
                            t.addLine(EnumChatFormatting.GRAY + "Click to unlock and auto-fit on window resize");
                        } else {
                            t.addLine(EnumChatFormatting.GREEN + "Auto-fit on resize: unlocked");
                            t.addLine(EnumChatFormatting.GRAY + "Click to lock zoom and pan on resize");
                        }
                    })
                    .onMousePressed(d -> {
                        graphWidget.toggleAutoFitLocked();
                        return true;
                    }))
            // Close window button
            .child(
                new ButtonWidget<>().size(14, 14)
                    .child(
                        IKey.str("x")
                            .asWidget()
                            .center())
                    .tooltipBuilder(t -> t.addLine("Close window"))
                    .onMousePressed(d -> {
                        panel.closeIfOpen();
                        return true;
                    }));

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .child(headerRow)
                .child(
                    graphWidget.fullWidth()
                        .expanded()));

        return panel;
    }
}
