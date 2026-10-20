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

    @Override
    public boolean onDragStart(int button) {
        if (button != 0 || this.panel == null) {
            return false;
        }
        ModularGuiContext ctx = this.panel.getContext();
        if (ctx == null) {
            return false;
        }
        Area panelArea = this.panel.getArea();
        this.movingArea.set(panelArea);
        this.relativeClickX = ctx.getAbsMouseX() - panelArea.x;
        this.relativeClickY = ctx.getAbsMouseY() - panelArea.y;
        return true;
    }

    @Override
    public void onDrag(int button, long time) {
        if (this.panel == null) return;
        ModularGuiContext ctx = this.panel.getContext();
        if (ctx == null) return;
        Area screenArea = this.panel.getScreen()
            .getScreenArea();
        int maxX = Math.max(0, screenArea.width - this.movingArea.width);
        int maxY = Math.max(0, screenArea.height - this.movingArea.height);
        int newX = ctx.getAbsMouseX() - this.relativeClickX;
        int newY = ctx.getAbsMouseY() - this.relativeClickY;
        this.movingArea.x = Math.max(0, Math.min(maxX, newX));
        this.movingArea.y = Math.max(0, Math.min(maxY, newY));
    }

    private Alignment alignment = Alignment.CenterLeft;

    public PanelDragHandle textAlign(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    @Override
    public void onDragEnd(boolean success) {
        if (success && this.panel != null) {
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

        // When WidgetTree.drawTree(this.panel) is invoked, panel.transform(ctx) translates by
        // panel.getArea().rx and ry (its static position on screen).
        // To position the panel at movingArea during drag, offset by (movingArea - staticArea).
        Area area = this.panel.getArea();
        ctx.translate(this.movingArea.x - area.rx, this.movingArea.y - area.ry);
        WidgetTree.drawTree(this.panel, ctx, true, true);
        ctx.popMatrix();
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
