package com.gtnewhorizons.coolantloops.common.gui;

import java.util.function.IntSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;

/**
 * A robust, jump-free drag handle for floating ModularPanels.
 * <p>
 * Replaces ModularUI2's default {@link com.cleanroommc.modularui.widget.DragHandle} and
 * {@link com.cleanroommc.modularui.screen.DraggablePanelWrapper}, which suffer from an offset
 * calculation bug on drag start (evaluating {@code transformX/Y(0,0)} on an untransformed context,
 * causing the window to instantly snap to (0,0) / top-left on click).
 * </p>
 * <p>
 * Optionally supports rendering a title label left-aligned within the drag area, allowing the user
 * to drag the window by both the title text and the empty title-bar space without position jumps.
 * </p>
 */
public class PanelDragHandle extends Widget<PanelDragHandle> implements IDraggable, IViewport {

    private ModularPanel panel;
    private final Area movingArea = new Area();
    private int relativeClickX;
    private int relativeClickY;
    private boolean moving;

    private IKey titleKey;
    private IntSupplier textColor;

    public PanelDragHandle() {}

    public PanelDragHandle(ModularPanel panel) {
        this.panel = panel;
    }

    public PanelDragHandle panel(ModularPanel panel) {
        this.panel = panel;
        return this;
    }

    public PanelDragHandle title(IKey titleKey) {
        this.titleKey = titleKey;
        return this;
    }

    public PanelDragHandle color(int color) {
        this.textColor = () -> color;
        return this;
    }

    public PanelDragHandle color(IntSupplier color) {
        this.textColor = color;
        return this;
    }

    @Override
    public void onInit() {
        super.onInit();
        if (this.panel == null) {
            IWidget current = this.getParent();
            while (current != null) {
                if (current instanceof ModularPanel) {
                    this.panel = (ModularPanel) current;
                    break;
                }
                current = current.getParent();
            }
        }
    }

    private static final long DOUBLE_CLICK_TIME_MS = 320L;
    private static final int DOUBLE_CLICK_DIST_SQ = 36; // 6 pixels

    private long lastClickTime = 0L;
    private int lastClickX = 0;
    private int lastClickY = 0;

    private boolean wasSnappedOnDragStart = false;
    private boolean hasDragged = false;
    private int dragStartAbsX = 0;
    private int dragStartAbsY = 0;
    private ResizeableFloatingPanel.SnapMode pendingSnap = null;
    private java.util.function.Supplier<ModularGuiContext> contextSupplier;
    private java.util.function.Supplier<Area> screenAreaSupplier;

    public PanelDragHandle contextSupplier(java.util.function.Supplier<ModularGuiContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
        return this;
    }

    public PanelDragHandle screenAreaSupplier(java.util.function.Supplier<Area> screenAreaSupplier) {
        this.screenAreaSupplier = screenAreaSupplier;
        return this;
    }

    private ModularGuiContext getGuiContext() {
        if (this.contextSupplier != null) {
            ModularGuiContext ctx = this.contextSupplier.get();
            if (ctx != null) return ctx;
        }
        if (this.panel != null && this.panel.getScreen() != null) {
            ModularGuiContext ctx = this.panel.getScreen()
                .getContext();
            if (ctx != null) return ctx;
        }
        if (this.panel != null) {
            try {
                return this.panel.getContext();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Area getScreenArea() {
        if (this.screenAreaSupplier != null) {
            Area a = this.screenAreaSupplier.get();
            if (a != null) return a;
        }
        if (this.panel != null && this.panel.getScreen() != null) {
            return this.panel.getScreen()
                .getScreenArea();
        }
        return null;
    }

    @Override
    public boolean onDragStart(int button) {
        if (button != 0 || this.panel == null) {
            return false;
        }
        ModularGuiContext ctx = getGuiContext();
        if (ctx == null) {
            return false;
        }

        int absX = ctx.getAbsMouseX();
        int absY = ctx.getAbsMouseY();
        long now = System.currentTimeMillis();

        // 1. Double-click detection on draggable area
        long dt = now - this.lastClickTime;
        int dx = absX - this.lastClickX;
        int dy = absY - this.lastClickY;
        if (dt >= 0 && dt <= DOUBLE_CLICK_TIME_MS && (dx * dx + dy * dy) <= DOUBLE_CLICK_DIST_SQ) {
            this.lastClickTime = 0L;
            if (this.panel instanceof ResizeableFloatingPanel) {
                ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;
                if (rfp.getCurrentSnap() != ResizeableFloatingPanel.SnapMode.NONE) {
                    rfp.restore(true, 0, 0);
                } else {
                    rfp.maximize();
                }
                return false;
            }
        }

        this.lastClickTime = now;
        this.lastClickX = absX;
        this.lastClickY = absY;

        this.hasDragged = false;
        this.dragStartAbsX = absX;
        this.dragStartAbsY = absY;
        this.pendingSnap = null;

        Area panelArea = this.panel.getArea();
        this.movingArea.set(panelArea);
        this.relativeClickX = absX - panelArea.x;
        this.relativeClickY = absY - panelArea.y;

        if (this.panel instanceof ResizeableFloatingPanel) {
            ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;
            this.wasSnappedOnDragStart = (rfp.getCurrentSnap() != ResizeableFloatingPanel.SnapMode.NONE);
        } else {
            this.wasSnappedOnDragStart = false;
        }

        return true;
    }

    @Override
    public void onDrag(int button, long time) {
        if (this.panel == null) return;
        ModularGuiContext ctx = getGuiContext();
        if (ctx == null) return;

        int absX = ctx.getAbsMouseX();
        int absY = ctx.getAbsMouseY();
        int dx = absX - this.dragStartAbsX;
        int dy = absY - this.dragStartAbsY;

        if (!this.hasDragged && (dx * dx + dy * dy) > 9) {
            this.hasDragged = true;
        }

        // If dragging started on a snapped or maximized window, un-snap to cursor once moved
        if (this.wasSnappedOnDragStart && this.hasDragged) {
            if (this.panel instanceof ResizeableFloatingPanel) {
                ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;
                Area currentSnappedArea = this.panel.getArea();
                int currentW = currentSnappedArea != null && currentSnappedArea.width > 0 ? currentSnappedArea.width
                    : 480;
                float ratioX = currentW > 0 ? (float) (this.dragStartAbsX - currentSnappedArea.x) / (float) currentW
                    : 0.5f;

                rfp.restore(false, absX, absY);
                Area norm = rfp.getNormalArea();
                int normW = norm.width > 0 ? norm.width : 380;
                int normH = norm.height > 0 ? norm.height : 230;

                this.movingArea.width = normW;
                this.movingArea.height = normH;

                int titleRelX = Math.round(ratioX * normW);
                this.relativeClickX = Math.max(15, Math.min(normW - 15, titleRelX));
                this.relativeClickY = Math.max(0, Math.min(18, this.dragStartAbsY - currentSnappedArea.y));
            }
            this.wasSnappedOnDragStart = false;
        }

        Area screenArea = getScreenArea();
        int scrW = screenArea != null && screenArea.width > 0 ? screenArea.width : 1000;
        int scrH = screenArea != null && screenArea.height > 0 ? screenArea.height : 1000;
        int maxX = Math.max(0, scrW - this.movingArea.width);
        int maxY = Math.max(0, scrH - this.movingArea.height);

        int newX = absX - this.relativeClickX;
        int newY = absY - this.relativeClickY;
        this.movingArea.x = Math.max(0, Math.min(maxX, newX));
        this.movingArea.y = Math.max(0, Math.min(maxY, newY));

        // Screen edge and corner snapping detection
        if (this.panel instanceof ResizeableFloatingPanel && screenArea != null) {
            ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;
            this.pendingSnap = rfp.detectSnapMode(absX, absY, screenArea);
        } else {
            this.pendingSnap = null;
        }
    }

    private Alignment alignment = Alignment.CenterLeft;

    public PanelDragHandle textAlign(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    @Override
    public void onDragEnd(boolean success) {
        if (!success || this.panel == null) {
            this.pendingSnap = null;
            return;
        }

        if (this.panel instanceof ResizeableFloatingPanel) {
            ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;

            // If click ended without any dragging movement, leave current state intact
            if (!this.hasDragged) {
                this.pendingSnap = null;
                return;
            }

            // Snapping to screen edge or corner
            if (this.pendingSnap != null && this.pendingSnap != ResizeableFloatingPanel.SnapMode.NONE) {
                rfp.applySnap(this.pendingSnap);
                this.pendingSnap = null;
                return;
            }

            // Normal drop at dragged position
            Area screenArea = getScreenArea();
            int scrW = screenArea != null && screenArea.width > 0 ? screenArea.width : 1000;
            int scrH = screenArea != null && screenArea.height > 0 ? screenArea.height : 1000;
            int curW = this.movingArea.width > 0 ? this.movingArea.width : this.panel.getArea().width;
            int curH = this.movingArea.height > 0 ? this.movingArea.height : this.panel.getArea().height;

            int availW = scrW - curW;
            int availH = scrH - curH;
            float left = availW > 0 ? (float) this.movingArea.x / (float) availW : 0f;
            float top = availH > 0 ? (float) this.movingArea.y / (float) availH : 0f;
            left = Math.max(0f, Math.min(1f, left));
            top = Math.max(0f, Math.min(1f, top));

            this.panel.resizer()
                .resetPosition();
            this.panel.resizer()
                .relativeToScreen();
            this.panel.resizer()
                .topRelAnchor(top, top);
            this.panel.resizer()
                .leftRelAnchor(left, left);
            this.panel.resizer()
                .width(curW);
            this.panel.resizer()
                .height(curH);
            this.panel.scheduleResize();

            rfp.onNormalDrop(this.movingArea.x, this.movingArea.y, curW, curH);
            this.pendingSnap = null;
        } else {
            if (this.hasDragged) {
                Area screenArea = this.panel.getScreen()
                    .getScreenArea();
                int availW = screenArea.width - this.panel.getArea().width;
                int availH = screenArea.height - this.panel.getArea().height;
                float left = availW > 0 ? (float) this.movingArea.x / (float) availW : 0f;
                float top = availH > 0 ? (float) this.movingArea.y / (float) availH : 0f;
                left = Math.max(0f, Math.min(1f, left));
                top = Math.max(0f, Math.min(1f, top));
                this.panel.resizer()
                    .resetPosition();
                this.panel.resizer()
                    .relativeToScreen();
                this.panel.resizer()
                    .topRelAnchor(top, top);
                this.panel.resizer()
                    .leftRelAnchor(left, left);
                this.panel.scheduleResize();
            }
            this.pendingSnap = null;
        }
    }

    @Override
    public void drawMovingState(ModularGuiContext ctx, float partialTicks) {
        if (this.panel == null) return;
        ctx.pushMatrix();
        // ModularGuiContext.drawDraggable() applies this handle's TransformationMatrix.
        // Un-translate by current transform so we are back at root screen space (0, 0).
        int curX = ctx.transformX(0, 0);
        int curY = ctx.transformY(0, 0);
        ctx.translate(-curX, -curY);

        // Draw snap preview highlight if hovering over a snap zone
        if (this.panel instanceof ResizeableFloatingPanel && this.pendingSnap != null
            && this.pendingSnap != ResizeableFloatingPanel.SnapMode.NONE) {
            ResizeableFloatingPanel rfp = (ResizeableFloatingPanel) this.panel;
            Area snapArea = rfp.getSnapArea(this.pendingSnap);
            if (snapArea != null) {
                drawSnapPreview(snapArea);
            }
        }

        // When WidgetTree.drawTree(this.panel) is invoked, panel.transform(ctx) translates by
        // panel.getArea().rx and ry (its static position on screen).
        // To position the panel at movingArea during drag, offset by (movingArea - staticArea).
        Area area = this.panel.getArea();
        ctx.translate(this.movingArea.x - area.rx, this.movingArea.y - area.ry);
        WidgetTree.drawTree(this.panel, ctx, true, true);
        ctx.popMatrix();
    }

    private void drawSnapPreview(Area snapArea) {
        // Semi-transparent blue fill
        com.cleanroommc.modularui.drawable.GuiDraw
            .drawRect(snapArea.x, snapArea.y, snapArea.width, snapArea.height, 0x4055D8FF);
        // Bright cyan border (2px)
        com.cleanroommc.modularui.drawable.GuiDraw
            .drawBorderInsideXYWH(snapArea.x, snapArea.y, snapArea.width, snapArea.height, 2.0f, 0xCC55D8FF);
    }

    @Override
    public void draw(ModularGuiContext ctx, WidgetThemeEntry<?> entry) {
        super.draw(ctx, entry);
        if (this.titleKey != null) {
            WidgetTheme theme = getActiveWidgetTheme(entry, isHovering());
            TextRenderer renderer = TextRenderer.SHARED;
            int c = this.textColor != null ? this.textColor.getAsInt() : theme.getTextColor();
            renderer.setColor(c);
            renderer.setShadow(theme.getTextShadow());
            renderer.setAlignment(
                this.alignment != null ? this.alignment : Alignment.CenterLeft,
                getArea().paddedWidth(),
                getArea().paddedHeight());
            renderer.setScale(this.titleKey.getScale());
            renderer.setSimulate(false);
            renderer.draw(this.titleKey.getFormatted());
        }
    }

    @Override
    public boolean canDropHere(int x, int y, IWidget target) {
        return true;
    }

    @Override
    public Area getMovingArea() {
        return this.movingArea;
    }

    @Override
    public boolean isMoving() {
        return this.moving;
    }

    @Override
    public void setMoving(boolean moving) {
        this.moving = moving;
        if (this.panel != null) {
            this.panel.setEnabled(!moving);
        }
    }

    @Override
    public void transform(IViewportStack stack) {
        super.transform(stack);
    }
}
