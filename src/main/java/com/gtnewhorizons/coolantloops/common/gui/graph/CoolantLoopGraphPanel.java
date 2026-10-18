package com.gtnewhorizons.coolantloops.common.gui.graph;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.DragHandle;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

import gregtech.api.enums.ItemList;
import gregtech.api.modularui2.common.CommonButtons;

/**
 * Builds the floating, movable, resizable Coolant Loop Graph window.
 */
public class CoolantLoopGraphPanel {

    public static final String PANEL_NAME = "coolantLoopGraphPanel";

    public static ModularPanel createPanel(PanelSyncManager syncManager, ModularPanel parent, MTECoolantPump pump) {
        ModularPanel panel = new ModularPanel(PANEL_NAME).relativeToScreen()
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
            // Screen icon
            .child(
                new ItemDisplayWidget().item(ItemList.Cover_Screen.get(1L))
                    .size(16, 16)
                    .displayAmount(false)
                    .marginRight(4))
            // Title text
            .child(
                new TextWidget<>(
                    IKey.str(
                        EnumChatFormatting.BOLD + StatCollector.translateToLocal("coolantloops.gui.title.loop_graph")))
                            .color(Color.WHITE.main)
                            .height(14)
                            .marginRight(6))
            // Draggable header area
            .child(
                new DragHandle().expanded()
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
            // Zoom level display
            .child(
                new TextWidget<>(IKey.dynamic(() -> graphWidget.getZoomPercent() + "%")).color(Color.WHITE.main)
                    .textAlign(Alignment.Center)
                    .size(32, 14)
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
                    .child(
                        IKey.str("Fit")
                            .asWidget()
                            .center()
                            .scale(0.8f))
                    .tooltipBuilder(t -> t.addLine("Fit graph to window"))
                    .onMousePressed(d -> {
                        graphWidget.fitToView();
                        return true;
                    }));

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .child(headerRow.marginRight(18))
                .child(
                    graphWidget.fullWidth()
                        .expanded()));

        // Window close button (anchored top-right of panel)
        panel.child(
            CommonButtons.panelCloseButton()
                .size(14, 14));

        return panel;
    }
}
