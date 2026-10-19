package com.gtnewhorizons.coolantloops.common.gui.graph;

import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.CoolantLoopGraphData.GraphNode;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutEdge;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.LayoutNode;
import com.gtnewhorizons.coolantloops.common.gui.graph.SugiyamaLayout.Point2D;
import com.gtnewhorizons.modularui.api.GlStateManager;

/**
 * Interactive canvas widget providing pan, zoom, and rich telemetry hover for the coolant loop graph.
 */
public class CoolantLoopGraphWidget extends Widget<CoolantLoopGraphWidget> implements Interactable {

    private final SugiyamaLayout layout = new SugiyamaLayout();
    private CoolantLoopGraphData graphData;

    private float panX = 20.0f;
    private float panY = 20.0f;
    private float zoom = 1.0f;
    private boolean hasInitialFit = false;

    private boolean isDragging = false;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;
    private float dragStartPanX = 0.0f;
    private float dragStartPanY = 0.0f;

    private LayoutNode hoveredNode = null;
    private LayoutEdge hoveredEdge = null;

    public CoolantLoopGraphWidget() {
        size(370, 220);
        tooltipDynamic(this::buildTooltip);
        tooltipAutoUpdate(true);
        tooltipShowUpTimer(0);
    }

    public void setGraphData(CoolantLoopGraphData data) {
        this.graphData = data;
        layout.computeLayout(data);
        if (getArea() != null && getArea().w() > 0 && getArea().h() > 0) {
            fitToView();
            hasInitialFit = true;
        } else {
            hasInitialFit = false;
        }
    }

    public CoolantLoopGraphData getGraphData() {
        return graphData;
    }

    public SugiyamaLayout getLayout() {
        return layout;
    }

    public float getZoom() {
        return zoom;
    }

    public int getZoomPercent() {
        return Math.round(zoom * 100.0f);
    }

    public void zoomIn() {
        setZoom(Math.min(2.5f, zoom * 1.2f));
    }

    public void zoomOut() {
        setZoom(Math.max(0.25f, zoom / 1.2f));
    }

    public void resetZoom() {
        this.zoom = 1.0f;
        this.panX = 20.0f;
        this.panY = 20.0f;
    }

    public void fitToView() {
        int vw = getArea().w();
        int vh = getArea().h();
        if (vw <= 0 || vh <= 0) return;

        double gw = layout.getBoundsWidth();
        double gh = layout.getBoundsHeight();
        if (gw <= 0 || gh <= 0) return;

        double scaleX = (vw - 32.0) / gw;
        double scaleY = (vh - 32.0) / gh;
        double fitScale = Math.max(0.3, Math.min(1.8, Math.min(scaleX, scaleY)));

        this.zoom = (float) fitScale;
        this.panX = (float) ((vw - gw * zoom) / 2.0);
        this.panY = (float) ((vh - gh * zoom) / 2.0);
    }

    private void setZoom(float newZoom) {
        int vw = getArea().w();
        int vh = getArea().h();
        float centerX = vw / 2.0f;
        float centerY = vh / 2.0f;

        float graphCenterX = (centerX - panX) / zoom;
        float graphCenterY = (centerY - panY) / zoom;

        this.zoom = newZoom;
        this.panX = centerX - graphCenterX * zoom;
        this.panY = centerY - graphCenterY * zoom;
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) { // Left click
            isDragging = true;
            ModularGuiContext ctx = getContext();
            dragStartMouseX = ctx != null ? ctx.getAbsMouseX() : 0;
            dragStartMouseY = ctx != null ? ctx.getAbsMouseY() : 0;
            dragStartPanX = panX;
            dragStartPanY = panY;
            return Interactable.Result.SUCCESS;
        }
        return Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (mouseButton == 0) {
            isDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public void onMouseDrag(int mouseButton, long time) {
        if (isDragging && mouseButton == 0) {
            ModularGuiContext ctx = getContext();
            if (ctx != null) {
                int mx = ctx.getAbsMouseX();
                int my = ctx.getAbsMouseY();
                panX = dragStartPanX + (mx - dragStartMouseX);
                panY = dragStartPanY + (my - dragStartMouseY);
            }
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown scroll, int delta) {
        if (scroll == UpOrDown.UP) {
            zoomIn();
            return true;
        } else if (scroll == UpOrDown.DOWN) {
            zoomOut();
            return true;
        }
        return false;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int rw = getArea().w();
        int rh = getArea().h();
        if (rw <= 0 || rh <= 0) return;

        // Auto-fit on first layout once data is ready
        if (!hasInitialFit && graphData != null && graphData.isFormed) {
            fitToView();
            hasInitialFit = true;
        }

        // 1. Draw viewport background and border in widget-local coordinates [0, 0, rw, rh]
        GuiDraw.drawRect(0, 0, rw, rh, 0xEE12161E);
        GuiDraw.drawRect(0, 0, rw, 1, 0xFF2A3645);
        GuiDraw.drawRect(0, rh - 1, rw, 1, 0xFF2A3645);
        GuiDraw.drawRect(0, 0, 1, rh, 0xFF2A3645);
        GuiDraw.drawRect(rw - 1, 0, 1, rh, 0xFF2A3645);

        if (graphData == null || !graphData.isFormed) {
            String msg = (graphData != null && graphData.failureReason != null) ? graphData.failureReason
                : "Coolant Loop Not Formed";
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int sw = fr.getStringWidth(msg);
            fr.drawStringWithShadow(msg, (rw - sw) / 2, (rh - 8) / 2, 0xFF8888);
            return;
        }

        // 2. Perform hit-testing against graph in widget-local coordinates
        int mx = context.getMouseX();
        int my = context.getMouseY();
        double gmx = (mx - panX) / zoom;
        double gmy = (my - panY) / zoom;

        boolean mouseInside = (mx >= 0 && mx < rw && my >= 0 && my < rh);
        hoveredNode = mouseInside ? layout.findNodeAt(gmx, gmy) : null;
        hoveredEdge = (mouseInside && hoveredNode == null) ? layout.findEdgeAt(gmx, gmy, 6.0 / zoom) : null;

        // 3. Render graph with pan and zoom transform, guarded by scissor clipping to widget area
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = sr.getScaleFactor();

        int absX = (context != null) ? context.transformX(0, 0) : getArea().x;
        int absY = (context != null) ? context.transformY(0, 0) : getArea().y;
        int absEndX = (context != null) ? context.transformX(rw, rh) : (absX + rw);
        int absEndY = (context != null) ? context.transformY(rw, rh) : (absY + rh);
        int sx = absX * scale;
        int sy = mc.displayHeight - absEndY * scale;
        int sw = Math.max(0, (absEndX - absX) * scale);
        int sh = Math.max(0, (absEndY - absY) * scale);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);

        GL11.glPushMatrix();
        GL11.glTranslatef(panX, panY, 0.0f);
        GL11.glScalef(zoom, zoom, 1.0f);

        // Draw untextured geometry: grid dots
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawGridDots();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Draw edges (pipes & directional chevrons)
        drawPipes();
        drawChevrons();

        // Draw nodes (devices)
        for (LayoutNode node : layout.getNodes()) {
            if (node.isDummy) continue;
            boolean isHovered = (node == hoveredNode);
            drawDeviceNode(node, isHovered);
        }

        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Reset OpenGL state
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawGridDots() {
        double gw = layout.getBoundsWidth() + 100.0;
        double gh = layout.getBoundsHeight() + 100.0;
        int gridStep = 20;

        GL11.glPointSize(1.5f);
        GL11.glBegin(GL11.GL_POINTS);
        GL11.glColor4f(0.3f, 0.4f, 0.5f, 0.25f);
        for (double gx = 0; gx < gw; gx += gridStep) {
            for (double gy = 0; gy < gh; gy += gridStep) {
                GL11.glVertex2d(gx, gy);
            }
        }
        GL11.glEnd();
        GL11.glPointSize(1.0f);
    }

    private void drawPipes() {
        GL11.glDisable(GL11.GL_CULL_FACE);
        Platform.setupDrawColor();
        Platform.startDrawing(Platform.DrawMode.QUADS, Platform.VertexFormat.POS_COLOR, builder -> {
            for (LayoutEdge edge : layout.getEdges()) {
                if (edge.waypoints.size() < 2) continue;

                boolean isHovered = (edge == hoveredEdge);
                float r = 0.36f, g = 0.50f, b = 0.65f, a = 0.90f; // Default steel blue
                if (edge.data != null) {
                    double temp = edge.data.temperatureCelsius;
                    if (temp > 300.0) {
                        r = 0.88f;
                        g = 0.25f;
                        b = 0.20f; // Red hot
                    } else if (temp > 100.0) {
                        r = 0.88f;
                        g = 0.55f;
                        b = 0.20f; // Warm amber
                    }
                }
                if (isHovered) {
                    r = 0.35f;
                    g = 0.85f;
                    b = 1.0f;
                    a = 1.0f; // Glowing cyan
                }

                double thickness = isHovered ? 5.0 : 3.5;

                for (int i = 0; i < edge.waypoints.size() - 1; i++) {
                    Point2D p1 = edge.waypoints.get(i);
                    Point2D p2 = edge.waypoints.get(i + 1);

                    double dx = p2.x - p1.x;
                    double dy = p2.y - p1.y;
                    double len = Math.hypot(dx, dy);
                    if (len == 0.0) continue;

                    double nx = -dy / len * (thickness / 2.0);
                    double ny = dx / len * (thickness / 2.0);

                    // Outer dark border
                    float br = 0.08f, bg = 0.10f, bb = 0.14f, ba = 0.85f;
                    builder.pos(p1.x + nx * 1.5, p1.y + ny * 1.5, 0.0)
                        .color(br, bg, bb, ba)
                        .endVertex();
                    builder.pos(p1.x - nx * 1.5, p1.y - ny * 1.5, 0.0)
                        .color(br, bg, bb, ba)
                        .endVertex();
                    builder.pos(p2.x - nx * 1.5, p2.y - ny * 1.5, 0.0)
                        .color(br, bg, bb, ba)
                        .endVertex();
                    builder.pos(p2.x + nx * 1.5, p2.y + ny * 1.5, 0.0)
                        .color(br, bg, bb, ba)
                        .endVertex();

                    // Pipe body
                    builder.pos(p1.x + nx, p1.y + ny, 0.0)
                        .color(r, g, b, a)
                        .endVertex();
                    builder.pos(p1.x - nx, p1.y - ny, 0.0)
                        .color(r, g, b, a)
                        .endVertex();
                    builder.pos(p2.x - nx, p2.y - ny, 0.0)
                        .color(r, g, b, a)
                        .endVertex();
                    builder.pos(p2.x + nx, p2.y + ny, 0.0)
                        .color(r, g, b, a)
                        .endVertex();
                }
            }
        });
        Platform.endDrawColor();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void drawChevrons() {
        GL11.glDisable(GL11.GL_CULL_FACE);
        Platform.setupDrawColor();
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR, builder -> {
            for (LayoutEdge edge : layout.getEdges()) {
                if (edge.arrowPoints.size() < 3) continue;

                boolean isHovered = (edge == hoveredEdge);
                float ar = isHovered ? 1.0f : 0.85f;
                float ag = isHovered ? 1.0f : 0.95f;
                float ab = 1.0f;
                float aa = isHovered ? 1.0f : 0.9f;

                Point2D tip = edge.arrowPoints.get(0);
                Point2D left = edge.arrowPoints.get(1);
                Point2D right = edge.arrowPoints.get(2);

                builder.pos(tip.x, tip.y, 0.0)
                    .color(ar, ag, ab, aa)
                    .endVertex();
                builder.pos(left.x, left.y, 0.0)
                    .color(ar, ag, ab, aa)
                    .endVertex();
                builder.pos(right.x, right.y, 0.0)
                    .color(ar, ag, ab, aa)
                    .endVertex();
            }
        });
        Platform.endDrawColor();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void drawDeviceNode(LayoutNode node, boolean isHovered) {
        int nx = (int) node.x;
        int ny = (int) node.y;
        int nw = (int) node.width;
        int nh = (int) node.height;

        // Node card background
        int bgColor = isHovered ? 0xF0222F3E : 0xEE1A222C;
        int borderColor = isHovered ? 0xFF55DDFF : 0xFF3E5066;

        GuiDraw.drawRect(nx, ny, nw, nh, bgColor);
        GuiDraw.drawRect(nx, ny, nw, 1, borderColor);
        GuiDraw.drawRect(nx, ny + nh - 1, nw, 1, borderColor);
        GuiDraw.drawRect(nx, ny, 1, nh, borderColor);
        GuiDraw.drawRect(nx + nw - 1, ny, 1, nh, borderColor);

        // Render controller icon
        ItemStack stack = node.data != null ? node.data.getItemStack() : null;
        if (stack != null) {
            GuiDraw.drawItem(stack, nx + 3, ny + (nh - 16) / 2, 16, 16, 0);
        }

        // Render label text
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String title = node.data != null ? node.data.label : node.id;
        String subtitle = node.data != null ? String.format(Locale.US, "%.0f°C", node.data.temperature) : "";

        // Truncate title if needed
        if (title.length() > 9) {
            title = title.substring(0, 8) + "…";
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(nx + 21, ny + 4, 0);
        GlStateManager.scale(0.65f, 0.65f, 1.0f);
        fr.drawString(title, 0, 0, 0xFFFFFF, false);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(nx + 21, ny + 15, 0);
        GlStateManager.scale(0.60f, 0.60f, 1.0f);
        fr.drawString(subtitle, 0, 0, 0xFFCC44, false);
        GlStateManager.popMatrix();
    }

    public void buildTooltip(RichTooltip t) {
        if (hoveredNode != null && hoveredNode.data != null) {
            GraphNode gn = hoveredNode.data;
            t.addLine(EnumChatFormatting.AQUA + "" + EnumChatFormatting.BOLD + gn.label);
            t.addLine(
                EnumChatFormatting.GRAY + "Position: "
                    + EnumChatFormatting.WHITE
                    + String.format("(%d, %d, %d)", gn.xCoord, gn.yCoord, gn.zCoord));
            t.addLine(EnumChatFormatting.GRAY + "Status: " + EnumChatFormatting.GREEN + gn.status);
            t.addLine(
                EnumChatFormatting.GRAY + "Temperature: "
                    + EnumChatFormatting.GOLD
                    + String.format(Locale.US, "%.1f °C", gn.temperature));
            t.addLine(
                EnumChatFormatting.GRAY + "Flow Rate: "
                    + EnumChatFormatting.AQUA
                    + String.format(Locale.US, "%.1f L/s", gn.flowRateLPerSec));
            if (gn.pressureBar > 0) {
                t.addLine(
                    EnumChatFormatting.GRAY + "Pressure: "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + String.format(Locale.US, "%.2f bar", gn.pressureBar));
            }
            if (gn.extraStats != null && !gn.extraStats.isEmpty()) {
                t.addLine(EnumChatFormatting.DARK_GRAY + "--------------------");
                for (Map.Entry<String, String> entry : gn.extraStats.entrySet()) {
                    t.addLine(
                        EnumChatFormatting.GRAY + entry.getKey() + ": " + EnumChatFormatting.WHITE + entry.getValue());
                }
            }
        } else if (hoveredEdge != null && hoveredEdge.data != null) {
            GraphEdge ge = hoveredEdge.data;
            t.addLine(
                EnumChatFormatting.AQUA + ""
                    + EnumChatFormatting.BOLD
                    + ge.pipeMaterial
                    + " "
                    + ge.pipeSize
                    + " Coolant Pipe");
            t.addLine(
                EnumChatFormatting.GRAY + "Length: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f m", ge.lengthM)
                    + EnumChatFormatting.GRAY
                    + ", Diameter: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.0f mm", ge.diameterM * 1000.0));
            t.addLine(
                EnumChatFormatting.GRAY + "Flow Rate: "
                    + EnumChatFormatting.AQUA
                    + String.format(
                        Locale.US,
                        "%.1f L/s (%.2f L/t) [%.0f%%]",
                        ge.flowRateLPerSec,
                        ge.flowRateLPerTick,
                        ge.flowFraction * 100.0));
            t.addLine(
                EnumChatFormatting.GRAY + "Fluid Velocity: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.2f m/s", ge.velocityMPerSec));
            t.addLine(
                EnumChatFormatting.GRAY + "Temperature: "
                    + EnumChatFormatting.GOLD
                    + String
                        .format(Locale.US, "%.1f °C / %.0f °C max", ge.temperatureCelsius, ge.maxTemperatureCelsius));
            t.addLine(
                EnumChatFormatting.GRAY + "Pressure: "
                    + EnumChatFormatting.LIGHT_PURPLE
                    + String.format(Locale.US, "%.2f bar / %.1f bar max", ge.pressureBar, ge.maxPressureBar));
            t.addLine(
                EnumChatFormatting.GRAY + "Pressure Drop (ΔP): "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f mbar", ge.pressureDropMbar));
            t.addLine(
                EnumChatFormatting.GRAY + "Hydrodynamics: "
                    + EnumChatFormatting.YELLOW
                    + String.format(Locale.US, "Re %,d (%s)", (long) ge.reynolds, ge.flowRegime));
        }
    }
}
