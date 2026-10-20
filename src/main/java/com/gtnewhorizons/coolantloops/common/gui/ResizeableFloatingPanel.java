package com.gtnewhorizons.coolantloops.common.gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.api.widget.ResizeDragArea;
import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;

/**
 * A floating modular panel that provides enhanced draggable borders and visible resize indicators.
 * <p>
 * Increases the border drag hit zone to make resizing easier to grab.
 * Renders small triangular drag handles in the 4 corners, and short vertical/horizontal bars at
 * the centers of the 4 edges. When hovered or dragged, indicators expand perceptibly and highlight.
 * Hit detection strictly adheres to the original resize drag area without inflating to the expanded
 * visual indicator geometry.
 * </p>
 */
public class ResizeableFloatingPanel extends ModularPanel {

    /** Draggable border width in screen pixels. */
    public static final int RESIZE_BORDER_WIDTH = 5;

    // Dimensions for corner triangles
    private static final float CORNER_SIZE_NORMAL = 7.0f;
    private static final float CORNER_SIZE_HOVERED = 10.0f;

    // Dimensions for edge center bars
    private static final float BAR_LENGTH_NORMAL = 20.0f;
    private static final float BAR_LENGTH_HOVERED = 26.0f;
    private static final float BAR_THICKNESS_NORMAL = 5.0f;
    private static final float BAR_THICKNESS_HOVERED = 7.0f;

    // Color palette
    private static final int COLOR_NORMAL_FILL = 0xAA707882;
    private static final int COLOR_NORMAL_LINE = 0xDD95A0AA;
    private static final int COLOR_HOVER_FILL = 0xFF55D8FF;
    private static final int COLOR_HOVER_LINE = 0xFFFFFFFF;

    private ResizeDragArea currentDragArea = null;

    public ResizeableFloatingPanel(String name) {
        super(name);
    }

    @Override
    public boolean isDraggable() {
        return false;
    }

    @Override
    public boolean keepPosOnDragResize() {
        return false;
    }

    @Override
    public int getDragAreaSize() {
        return RESIZE_BORDER_WIDTH;
    }

    @Override
    public boolean onMousePressed(int button) {
        if (button == 0 && this.isCurrentlyResizable()) {
            ModularGuiContext ctx = getContext();
            if (ctx != null) {
                this.currentDragArea = IDragResizeable
                    .getDragResizeCorner(this, getArea(), ctx, ctx.getAbsMouseX(), ctx.getAbsMouseY());
            }
        }
        return super.onMousePressed(button);
    }

    @Override
    public boolean onMouseRelease(int button) {
        if (button == 0) {
            this.currentDragArea = null;
        }
        return super.onMouseRelease(button);
    }

    @Override
    public void onClose() {
        this.currentDragArea = null;
        super.onClose();
    }

    /**
     * Determines which resize handle (if any) is currently hovered or actively dragged.
     * Evaluates strictly against the panel's original resize boundary, without considering
     * the expanded geometry of hovered visual indicators.
     */
    public ResizeDragArea getActiveResizeArea(ModularGuiContext ctx) {
        if (this.currentDragArea != null) {
            return this.currentDragArea;
        }
        if (!this.isCurrentlyResizable() || ctx == null || !this.isHovering()) {
            return null;
        }
        return IDragResizeable.getDragResizeCorner(this, getArea(), ctx, ctx.getAbsMouseX(), ctx.getAbsMouseY());
    }

    @Override
    public void drawForeground(ModularGuiContext ctx) {
        super.drawForeground(ctx);
        if (!this.isCurrentlyResizable()) return;

        drawResizeIndicators(ctx);
    }

    private void drawResizeIndicators(ModularGuiContext ctx) {
        ResizeDragArea activeArea = getActiveResizeArea(ctx);

        float width = getArea().width;
        float height = getArea().height;

        GL11.glPushMatrix();
        ctx.applyToOpenGl();

        // 1. Draw edge center bars
        drawEdgeBars(width, height, activeArea);

        // 2. Draw corner drag triangles
        drawCornerTriangles(width, height, activeArea);

        GL11.glPopMatrix();
    }

    private void drawEdgeBars(float width, float height, ResizeDragArea activeArea) {
        // Top edge center bar
        boolean topHover = (activeArea == ResizeDragArea.TOP);
        float topL = topHover ? BAR_LENGTH_HOVERED : BAR_LENGTH_NORMAL;
        float topT = topHover ? BAR_THICKNESS_HOVERED : BAR_THICKNESS_NORMAL;
        float topX = (width - topL) * 0.5f;
        int topFill = topHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int topLine = topHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        GuiDraw.drawRect(topX, 0, topL, topT, topFill);
        GuiDraw.drawRect(topX, topT - 1.0f, topL, 1.0f, topLine);

        // Bottom edge center bar
        boolean botHover = (activeArea == ResizeDragArea.BOTTOM);
        float botL = botHover ? BAR_LENGTH_HOVERED : BAR_LENGTH_NORMAL;
        float botT = botHover ? BAR_THICKNESS_HOVERED : BAR_THICKNESS_NORMAL;
        float botX = (width - botL) * 0.5f;
        int botFill = botHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int botLine = botHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        GuiDraw.drawRect(botX, height - botT, botL, botT, botFill);
        GuiDraw.drawRect(botX, height - botT, botL, 1.0f, botLine);

        // Left edge center bar
        boolean leftHover = (activeArea == ResizeDragArea.LEFT);
        float leftL = leftHover ? BAR_LENGTH_HOVERED : BAR_LENGTH_NORMAL;
        float leftT = leftHover ? BAR_THICKNESS_HOVERED : BAR_THICKNESS_NORMAL;
        float leftY = (height - leftL) * 0.5f;
        int leftFill = leftHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int leftLine = leftHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        GuiDraw.drawRect(0, leftY, leftT, leftL, leftFill);
        GuiDraw.drawRect(leftT - 1.0f, leftY, 1.0f, leftL, leftLine);

        // Right edge center bar
        boolean rightHover = (activeArea == ResizeDragArea.RIGHT);
        float rightL = rightHover ? BAR_LENGTH_HOVERED : BAR_LENGTH_NORMAL;
        float rightT = rightHover ? BAR_THICKNESS_HOVERED : BAR_THICKNESS_NORMAL;
        float rightY = (height - rightL) * 0.5f;
        int rightFill = rightHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int rightLine = rightHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        GuiDraw.drawRect(width - rightT, rightY, rightT, rightL, rightFill);
        GuiDraw.drawRect(width - rightT, rightY, 1.0f, rightL, rightLine);
    }

    private void drawCornerTriangles(float width, float height, ResizeDragArea activeArea) {
        boolean tlHover = (activeArea == ResizeDragArea.TOP_LEFT);
        boolean trHover = (activeArea == ResizeDragArea.TOP_RIGHT);
        boolean blHover = (activeArea == ResizeDragArea.BOTTOM_LEFT);
        boolean brHover = (activeArea == ResizeDragArea.BOTTOM_RIGHT);

        float tlS = tlHover ? CORNER_SIZE_HOVERED : CORNER_SIZE_NORMAL;
        float trS = trHover ? CORNER_SIZE_HOVERED : CORNER_SIZE_NORMAL;
        float blS = blHover ? CORNER_SIZE_HOVERED : CORNER_SIZE_NORMAL;
        float brS = brHover ? CORNER_SIZE_HOVERED : CORNER_SIZE_NORMAL;

        int tlFill = tlHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int trFill = trHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int blFill = blHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;
        int brFill = brHover ? COLOR_HOVER_FILL : COLOR_NORMAL_FILL;

        int tlLine = tlHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        int trLine = trHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        int blLine = blHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;
        int brLine = brHover ? COLOR_HOVER_LINE : COLOR_NORMAL_LINE;

        GL11.glDisable(GL11.GL_CULL_FACE);
        Platform.setupDrawColor();

        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR, builder -> {
            // Top-left triangle: (0, 0) -> (tlS, 0) -> (0, tlS)
            addTriangle(builder, 0, 0, tlS, 0, 0, tlS, tlFill);

            // Top-right triangle: (width, 0) -> (width - trS, 0) -> (width, trS)
            addTriangle(builder, width, 0, width - trS, 0, width, trS, trFill);

            // Bottom-left triangle: (0, height) -> (blS, height) -> (0, height - blS)
            addTriangle(builder, 0, height, blS, height, 0, height - blS, blFill);

            // Bottom-right triangle: (width, height) -> (width - brS, height) -> (width, height - brS)
            addTriangle(builder, width, height, width - brS, height, width, height - brS, brFill);
        });

        // Hypotenuse edge lines
        Platform.startDrawing(Platform.DrawMode.LINES, Platform.VertexFormat.POS_COLOR, builder -> {
            addLine(builder, tlS, 0, 0, tlS, tlLine);
            addLine(builder, width - trS, 0, width, trS, trLine);
            addLine(builder, blS, height, 0, height - blS, blLine);
            addLine(builder, width - brS, height, width, height - brS, brLine);
        });

        Platform.endDrawColor();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void addTriangle(BufferBuilder builder, float x1, float y1, float x2, float y2, float x3, float y3,
        int color) {
        int r = Color.getRed(color);
        int g = Color.getGreen(color);
        int b = Color.getBlue(color);
        int a = Color.getAlpha(color);

        builder.pos(x1, y1, 0.0)
            .color(r, g, b, a)
            .endVertex();
        builder.pos(x2, y2, 0.0)
            .color(r, g, b, a)
            .endVertex();
        builder.pos(x3, y3, 0.0)
            .color(r, g, b, a)
            .endVertex();
    }

    private void addLine(BufferBuilder builder, float x1, float y1, float x2, float y2, int color) {
        int r = Color.getRed(color);
        int g = Color.getGreen(color);
        int b = Color.getBlue(color);
        int a = Color.getAlpha(color);

        builder.pos(x1, y1, 0.0)
            .color(r, g, b, a)
            .endVertex();
        builder.pos(x2, y2, 0.0)
            .color(r, g, b, a)
            .endVertex();
    }
}
