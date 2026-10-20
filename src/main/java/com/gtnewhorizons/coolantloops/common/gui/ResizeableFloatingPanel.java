package com.gtnewhorizons.coolantloops.common.gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.api.widget.ResizeDragArea;
import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.sizer.Area;

/**
 * A floating modular panel that provides enhanced draggable borders and visible resize indicators.
 * <p>
 * Increases the border drag hit zone to make resizing easier to grab.
 * Renders small triangular drag handles in the 4 corners, and short vertical/horizontal bars at
 * the centers of the 4 edges. When hovered or dragged, indicators expand perceptibly and highlight.
 * Hit detection strictly adheres to the original resize drag area without inflating to the expanded
 * visual indicator geometry.
 * </p>
 * <p>
 * Supports window maximizing on double-click, and modern desktop edge/corner snapping
 * (top edge maximize, left/right edges spanning to main multiblock GUI boundary, and 4 corner quarters).
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

    public enum SnapMode {
        NONE,
        MAXIMIZED,
        LEFT,
        RIGHT,
        TOP_LEFT,
        BOTTOM_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    private ResizeDragArea currentDragArea = null;
    private SnapMode currentSnap = SnapMode.NONE;
    private final Area normalArea = new Area();
    private boolean hasNormalArea = false;
    private ModularPanel mainPanel = null;

    public ResizeableFloatingPanel(String name) {
        super(name);
    }

    public ResizeableFloatingPanel(String name, ModularPanel mainPanel) {
        super(name);
        this.mainPanel = mainPanel;
    }

    public ResizeableFloatingPanel setMainPanel(ModularPanel mainPanel) {
        this.mainPanel = mainPanel;
        return this;
    }

    public ModularPanel getMainPanel() {
        return this.mainPanel;
    }

    public SnapMode getCurrentSnap() {
        return this.currentSnap;
    }

    public Area getNormalArea() {
        if (!this.hasNormalArea || this.normalArea.width <= 0 || this.normalArea.height <= 0) {
            Area area = getArea();
            if (area != null && area.width > 0 && area.height > 0) {
                this.normalArea.set(area);
                this.hasNormalArea = true;
            } else {
                this.normalArea.set(0, 0, 380, 230);
            }
        }
        return this.normalArea;
    }

    public void setNormalArea(int x, int y, int width, int height) {
        this.normalArea.set(x, y, width, height);
        this.hasNormalArea = true;
    }

    private int lastKnownScreenWidth = -1;
    private int lastKnownScreenHeight = -1;
    private java.util.function.Supplier<Area> screenAreaSupplier;

    public ResizeableFloatingPanel screenAreaSupplier(java.util.function.Supplier<Area> supplier) {
        this.screenAreaSupplier = supplier;
        return this;
    }

    public static Area getClientScreenArea() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.displayWidth > 0 && mc.displayHeight > 0) {
                net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(
                    mc,
                    mc.displayWidth,
                    mc.displayHeight);
                int sw = sr.getScaledWidth();
                int sh = sr.getScaledHeight();
                if (sw > 0 && sh > 0) {
                    return new Area(0, 0, sw, sh);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public Area getSafeScreenArea() {
        if (this.screenAreaSupplier != null) {
            Area a = this.screenAreaSupplier.get();
            if (a != null && a.width > 0 && a.height > 0) return a;
        }
        Area clientArea = getClientScreenArea();
        if (clientArea != null) {
            return clientArea;
        }
        ModularScreen screen = getSafeScreen();
        if (screen != null) {
            Area area = screen.getScreenArea();
            if (area != null && area.width > 0 && area.height > 0) {
                return area;
            }
        }
        if (this.mainPanel != null) {
            try {
                if (this.mainPanel.isValid() && this.mainPanel.getScreen() != null) {
                    Area area = this.mainPanel.getScreen()
                        .getScreenArea();
                    if (area != null && area.width > 0 && area.height > 0) {
                        return area;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return new Area(0, 0, 480, 270);
    }

    private ModularScreen getSafeScreen() {
        try {
            if (isValid()) {
                return getScreen();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public Area getMainGuiArea() {
        if (this.mainPanel != null) {
            Area a = this.mainPanel.getArea();
            if (a != null && a.width > 0) return a;
        }
        ModularScreen screen = getSafeScreen();
        if (screen != null) {
            ModularPanel main = screen.getMainPanel();
            if (main != null && main != this) {
                Area a = main.getArea();
                if (a != null && a.width > 0) return a;
            }
        }
        return null;
    }

    public Area getSnapArea(SnapMode mode) {
        return getSnapArea(mode, null);
    }

    public Area getSnapArea(SnapMode mode, Area screenArea) {
        if (mode == null || mode == SnapMode.NONE) {
            return null;
        }
        Area safeScreen = (screenArea != null && screenArea.width > 0 && screenArea.height > 0) ? screenArea
            : getSafeScreenArea();
        int scrW = safeScreen.width;
        int scrH = safeScreen.height;

        Area mainArea = getMainGuiArea();
        int defMainW = Math.min(200, scrW / 2);
        int mainX = (mainArea != null && mainArea.width > 0) ? mainArea.x : (scrW - defMainW) / 2;
        int mainW = (mainArea != null && mainArea.width > 0) ? mainArea.width : defMainW;

        int minW = getMinDragWidth();
        int leftW = Math.max(minW, mainX);
        leftW = Math.min(scrW, leftW);

        int rightX = mainX + mainW;
        int rightW = Math.max(minW, scrW - rightX);
        if (rightX + rightW > scrW) {
            rightX = Math.max(0, scrW - rightW);
        }

        int halfH = scrH / 2;
        int botH = scrH - halfH;

        switch (mode) {
            case MAXIMIZED:
                return new Area(0, 0, scrW, scrH);
            case LEFT:
                return new Area(0, 0, leftW, scrH);
            case RIGHT:
                return new Area(rightX, 0, rightW, scrH);
            case TOP_LEFT:
                return new Area(0, 0, leftW, halfH);
            case BOTTOM_LEFT:
                return new Area(0, halfH, leftW, botH);
            case TOP_RIGHT:
                return new Area(rightX, 0, rightW, halfH);
            case BOTTOM_RIGHT:
                return new Area(rightX, halfH, rightW, botH);
            default:
                return null;
        }
    }

    public SnapMode detectSnapMode(int mouseX, int mouseY, Area screenArea) {
        Area safeScreen = (screenArea != null && screenArea.width > 0 && screenArea.height > 0) ? screenArea
            : getSafeScreenArea();
        int scrW = safeScreen.width;
        int scrH = safeScreen.height;
        int edgeThreshold = 5;
        int cornerZone = Math.max(32, scrH / 6);

        boolean isTop = (mouseY <= edgeThreshold);
        boolean isBottom = (mouseY >= scrH - 1 - edgeThreshold);
        boolean isLeft = (mouseX <= edgeThreshold);
        boolean isRight = (mouseX >= scrW - 1 - edgeThreshold);

        if (isLeft) {
            if (isTop || mouseY <= cornerZone) return SnapMode.TOP_LEFT;
            if (isBottom || mouseY >= scrH - cornerZone) return SnapMode.BOTTOM_LEFT;
            return SnapMode.LEFT;
        }
        if (isRight) {
            if (isTop || mouseY <= cornerZone) return SnapMode.TOP_RIGHT;
            if (isBottom || mouseY >= scrH - cornerZone) return SnapMode.BOTTOM_RIGHT;
            return SnapMode.RIGHT;
        }
        if (isTop) {
            if (mouseX <= cornerZone) return SnapMode.TOP_LEFT;
            if (mouseX >= scrW - cornerZone) return SnapMode.TOP_RIGHT;
            return SnapMode.MAXIMIZED;
        }
        if (isBottom) {
            if (mouseX <= cornerZone) return SnapMode.BOTTOM_LEFT;
            if (mouseX >= scrW - cornerZone) return SnapMode.BOTTOM_RIGHT;
            return SnapMode.NONE;
        }
        return SnapMode.NONE;
    }

    public void applySnap(SnapMode mode) {
        applySnap(mode, null);
    }

    public void applySnap(SnapMode mode, Area customScreenArea) {
        if (mode == null || mode == SnapMode.NONE) {
            return;
        }
        Area screenArea = (customScreenArea != null && customScreenArea.width > 0 && customScreenArea.height > 0)
            ? customScreenArea
            : getSafeScreenArea();
        if (this.currentSnap == SnapMode.NONE) {
            Area cur = getArea();
            if (cur != null && cur.width > 0 && cur.height > 0) {
                this.normalArea.set(cur);
                this.hasNormalArea = true;
            }
        }
        Area target = getSnapArea(mode, screenArea);
        if (target != null) {
            this.lastKnownScreenWidth = screenArea.width;
            this.lastKnownScreenHeight = screenArea.height;
            resizer().resetPosition();
            resizer().relativeToScreen();
            resizer().left(target.x);
            resizer().top(target.y);
            resizer().width(target.width);
            resizer().height(target.height);
            scheduleResize();
            this.currentSnap = mode;
        }
    }

    public void maximize() {
        applySnap(SnapMode.MAXIMIZED);
    }

    public void maximize(Area customScreenArea) {
        applySnap(SnapMode.MAXIMIZED, customScreenArea);
    }

    @Override
    public void beforeResize(boolean full) {
        super.beforeResize(full);
        checkScreenDimensions();
    }

    @Override
    public void onResized() {
        super.onResized();
        checkScreenDimensions();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        checkScreenDimensions();
    }

    public void checkScreenDimensions() {
        Area screenArea = getSafeScreenArea();
        if (screenArea != null && screenArea.width > 0 && screenArea.height > 0) {
            if (this.lastKnownScreenWidth < 0 || this.lastKnownScreenHeight < 0) {
                this.lastKnownScreenWidth = screenArea.width;
                this.lastKnownScreenHeight = screenArea.height;
                return;
            }
            if (screenArea.width != this.lastKnownScreenWidth || screenArea.height != this.lastKnownScreenHeight) {
                this.lastKnownScreenWidth = screenArea.width;
                this.lastKnownScreenHeight = screenArea.height;
                onScreenSizeChanged(screenArea);
            }
        }
    }

    public void onScreenSizeChanged(Area screenArea) {
        if (this.currentSnap != SnapMode.NONE) {
            applySnap(this.currentSnap, screenArea);
        } else {
            if (this.hasNormalArea) {
                int maxW = Math.max(getMinDragWidth(), screenArea.width);
                int maxH = Math.max(getMinDragHeight(), screenArea.height);
                this.normalArea.width = Math.min(this.normalArea.width, maxW);
                this.normalArea.height = Math.min(this.normalArea.height, maxH);
                this.normalArea.x = Math.max(0, Math.min(screenArea.width - this.normalArea.width, this.normalArea.x));
                this.normalArea.y = Math
                    .max(0, Math.min(screenArea.height - this.normalArea.height, this.normalArea.y));
            }
            Area cur = getArea();
            if (cur != null && cur.width > 0 && cur.height > 0) {
                int curW = Math.min(cur.width, screenArea.width);
                int curH = Math.min(cur.height, screenArea.height);
                int curX = Math.max(0, Math.min(screenArea.width - curW, cur.x));
                int curY = Math.max(0, Math.min(screenArea.height - curH, cur.y));
                resizer().resetPosition();
                resizer().relativeToScreen();
                resizer().left(curX);
                resizer().top(curY);
                resizer().width(curW);
                resizer().height(curH);
                scheduleResize();
            }
        }
    }

    public void restore(boolean restorePosition, int mouseX, int mouseY) {
        restore(restorePosition, mouseX, mouseY, null);
    }

    public void restore(boolean restorePosition, int mouseX, int mouseY, Area customScreenArea) {
        if (this.currentSnap == SnapMode.NONE) {
            return;
        }
        Area screenArea = (customScreenArea != null && customScreenArea.width > 0 && customScreenArea.height > 0)
            ? customScreenArea
            : getSafeScreenArea();
        int maxScrW = screenArea.width;
        int maxScrH = screenArea.height;

        Area norm = getNormalArea();
        int targetW = norm.width > 0 ? norm.width : 380;
        int targetH = norm.height > 0 ? norm.height : 230;
        targetW = Math.min(targetW, maxScrW);
        targetH = Math.min(targetH, maxScrH);

        int targetX;
        int targetY;

        if (restorePosition) {
            targetX = norm.x;
            targetY = norm.y;
            targetX = Math.max(0, Math.min(Math.max(0, maxScrW - targetW), targetX));
            targetY = Math.max(0, Math.min(Math.max(0, maxScrH - targetH), targetY));

            int availW = maxScrW - targetW;
            int availH = maxScrH - targetH;
            float leftRel = availW > 0 ? (float) targetX / (float) availW : 0f;
            float topRel = availH > 0 ? (float) targetY / (float) availH : 0f;
            leftRel = Math.max(0f, Math.min(1f, leftRel));
            topRel = Math.max(0f, Math.min(1f, topRel));

            resizer().resetPosition();
            resizer().relativeToScreen();
            resizer().topRelAnchor(topRel, topRel);
            resizer().leftRelAnchor(leftRel, leftRel);
            resizer().width(targetW);
            resizer().height(targetH);
        } else {
            targetX = mouseX - targetW / 2;
            targetY = mouseY - 10;
            targetX = Math.max(0, Math.min(Math.max(0, maxScrW - targetW), targetX));
            targetY = Math.max(0, Math.min(Math.max(0, maxScrH - targetH), targetY));

            resizer().resetPosition();
            resizer().relativeToScreen();
            resizer().left(targetX);
            resizer().top(targetY);
            resizer().width(targetW);
            resizer().height(targetH);
        }

        scheduleResize();
        this.currentSnap = SnapMode.NONE;
    }

    public void onNormalDrop(int x, int y, int width, int height) {
        this.normalArea.set(x, y, width, height);
        this.hasNormalArea = true;
        this.currentSnap = SnapMode.NONE;
    }

    @Override
    public boolean isCurrentlyResizable() {
        return super.isCurrentlyResizable() && this.currentSnap == SnapMode.NONE;
    }

    @Override
    public void onDragResize() {
        super.onDragResize();
        if (this.currentSnap == SnapMode.NONE) {
            Area cur = getArea();
            if (cur != null && cur.width > 0 && cur.height > 0) {
                this.normalArea.set(cur);
                this.hasNormalArea = true;
            }
        }
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

    private Area movingArea = null;

    public void setMovingArea(Area movingArea) {
        this.movingArea = movingArea;
    }

    public Area getEffectiveArea() {
        if (this.movingArea != null && this.movingArea.width > 0 && this.movingArea.height > 0) {
            return this.movingArea;
        }
        return getArea();
    }

    @Override
    public boolean canClickThrough() {
        return false;
    }

    @Override
    public boolean disablePanelsBelow() {
        return this.currentSnap == SnapMode.MAXIMIZED;
    }

    @Override
    public void onOpen(ModularScreen screen) {
        super.onOpen(screen);
        FloatingPanelNEIHandler.register();
    }

    @Override
    public void onClose() {
        this.currentDragArea = null;
        this.movingArea = null;
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
