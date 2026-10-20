package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.gtnewhorizons.coolantloops.common.gui.PanelDragHandle;
import com.gtnewhorizons.coolantloops.common.gui.ResizeableFloatingPanel;
import com.gtnewhorizons.coolantloops.common.gui.ResizeableFloatingPanel.SnapMode;

public class FloatingPanelSnapAndMaximizeTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = mock(cpw.mods.fml.common.Loader.class);
            when(mockLoader.getCallableCrashInformation()).thenReturn(mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}
    }

    @Test
    void testDetectSnapModeEdgesAndCorners() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("test_panel");
        Area screenArea = new Area(0, 0, 800, 600);

        // 1. Top edge -> MAXIMIZED
        assertEquals(SnapMode.MAXIMIZED, panel.detectSnapMode(400, 2, screenArea));
        assertEquals(SnapMode.MAXIMIZED, panel.detectSnapMode(200, 0, screenArea));

        // 2. Left edge -> LEFT
        assertEquals(SnapMode.LEFT, panel.detectSnapMode(2, 300, screenArea));
        assertEquals(SnapMode.LEFT, panel.detectSnapMode(0, 250, screenArea));

        // 3. Right edge -> RIGHT
        assertEquals(SnapMode.RIGHT, panel.detectSnapMode(798, 300, screenArea));
        assertEquals(SnapMode.RIGHT, panel.detectSnapMode(799, 250, screenArea));

        // 4. Corners
        // Top-left
        assertEquals(SnapMode.TOP_LEFT, panel.detectSnapMode(2, 2, screenArea));
        assertEquals(SnapMode.TOP_LEFT, panel.detectSnapMode(0, 0, screenArea));
        assertEquals(SnapMode.TOP_LEFT, panel.detectSnapMode(20, 2, screenArea)); // top near corner
        assertEquals(SnapMode.TOP_LEFT, panel.detectSnapMode(2, 20, screenArea)); // left near corner

        // Bottom-left
        assertEquals(SnapMode.BOTTOM_LEFT, panel.detectSnapMode(2, 598, screenArea));
        assertEquals(SnapMode.BOTTOM_LEFT, panel.detectSnapMode(0, 599, screenArea));
        assertEquals(SnapMode.BOTTOM_LEFT, panel.detectSnapMode(20, 598, screenArea));

        // Top-right
        assertEquals(SnapMode.TOP_RIGHT, panel.detectSnapMode(798, 2, screenArea));
        assertEquals(SnapMode.TOP_RIGHT, panel.detectSnapMode(799, 0, screenArea));
        assertEquals(SnapMode.TOP_RIGHT, panel.detectSnapMode(780, 2, screenArea));

        // Bottom-right
        assertEquals(SnapMode.BOTTOM_RIGHT, panel.detectSnapMode(798, 598, screenArea));
        assertEquals(SnapMode.BOTTOM_RIGHT, panel.detectSnapMode(799, 599, screenArea));
        assertEquals(SnapMode.BOTTOM_RIGHT, panel.detectSnapMode(780, 598, screenArea));

        // 5. Center of screen -> NONE
        assertEquals(SnapMode.NONE, panel.detectSnapMode(400, 300, screenArea));
        assertEquals(SnapMode.NONE, panel.detectSnapMode(100, 100, screenArea));
    }

    @Test
    void testSnapAreaCalculationsWithMainGui() {
        ModularPanel mainPanel = new ModularPanel("main_gui");
        mainPanel.getArea()
            .set(300, 200, 200, 200); // Center: x=300 to 500, y=200 to 400

        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("floating_panel", mainPanel);
        Area screenArea = new Area(0, 0, 800, 600);

        // 1. MAXIMIZED: covers full screen 800x600
        Area maxArea = panel.getSnapArea(SnapMode.MAXIMIZED, screenArea);
        assertNotNull(maxArea);
        assertEquals(0, maxArea.x);
        assertEquals(0, maxArea.y);
        assertEquals(800, maxArea.width);
        assertEquals(600, maxArea.height);

        // 2. LEFT: vertically maximum (600), horizontally from 0 to 300 (beginning of main GUI)
        Area leftArea = panel.getSnapArea(SnapMode.LEFT, screenArea);
        assertNotNull(leftArea);
        assertEquals(0, leftArea.x);
        assertEquals(0, leftArea.y);
        assertEquals(300, leftArea.width);
        assertEquals(600, leftArea.height);

        // 3. RIGHT: vertically maximum (600), horizontally from 500 (end of main GUI) to 800 (right edge: width = 300)
        Area rightArea = panel.getSnapArea(SnapMode.RIGHT, screenArea);
        assertNotNull(rightArea);
        assertEquals(500, rightArea.x);
        assertEquals(0, rightArea.y);
        assertEquals(300, rightArea.width);
        assertEquals(600, rightArea.height);

        // 4. TOP_LEFT: top half (0 to 300), horizontally 0 to 300
        Area tlArea = panel.getSnapArea(SnapMode.TOP_LEFT, screenArea);
        assertNotNull(tlArea);
        assertEquals(0, tlArea.x);
        assertEquals(0, tlArea.y);
        assertEquals(300, tlArea.width);
        assertEquals(300, tlArea.height);

        // 5. BOTTOM_LEFT: bottom half (300 to 600), horizontally 0 to 300
        Area blArea = panel.getSnapArea(SnapMode.BOTTOM_LEFT, screenArea);
        assertNotNull(blArea);
        assertEquals(0, blArea.x);
        assertEquals(300, blArea.y);
        assertEquals(300, blArea.width);
        assertEquals(300, blArea.height);

        // 6. TOP_RIGHT: top half (0 to 300), horizontally 500 to 800
        Area trArea = panel.getSnapArea(SnapMode.TOP_RIGHT, screenArea);
        assertNotNull(trArea);
        assertEquals(500, trArea.x);
        assertEquals(0, trArea.y);
        assertEquals(300, trArea.width);
        assertEquals(300, trArea.height);

        // 7. BOTTOM_RIGHT: bottom half (300 to 600), horizontally 500 to 800
        Area brArea = panel.getSnapArea(SnapMode.BOTTOM_RIGHT, screenArea);
        assertNotNull(brArea);
        assertEquals(500, brArea.x);
        assertEquals(300, brArea.y);
        assertEquals(300, brArea.width);
        assertEquals(300, brArea.height);
    }

    @Test
    void testMaximizeAndRestoreTransitions() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel");
        panel.resizeableOnDrag(true);
        Area screenArea = new Area(0, 0, 800, 600);

        panel.getArea()
            .set(50, 40, 380, 230);
        assertEquals(SnapMode.NONE, panel.getCurrentSnap());
        assertTrue(panel.isCurrentlyResizable());

        // Maximize
        panel.maximize(screenArea);
        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap());
        assertFalse(panel.isCurrentlyResizable(), "Resize indicators must be disabled while maximized");

        // Double-click restore (restorePosition = true)
        panel.restore(true, 0, 0, screenArea);
        assertEquals(SnapMode.NONE, panel.getCurrentSnap());
        assertTrue(panel.isCurrentlyResizable(), "Resize indicators must be restored in normal mode");
        assertEquals(380, panel.getNormalArea().width);
        assertEquals(230, panel.getNormalArea().height);
        assertEquals(50, panel.getNormalArea().x);
        assertEquals(40, panel.getNormalArea().y);

        // Left snap
        panel.applySnap(SnapMode.LEFT, screenArea);
        assertEquals(SnapMode.LEFT, panel.getCurrentSnap());
        assertFalse(panel.isCurrentlyResizable());

        // Drag restore (restorePosition = false, cursor at 400, 100)
        panel.restore(false, 400, 100, screenArea);
        assertEquals(SnapMode.NONE, panel.getCurrentSnap());
        assertEquals(380, panel.getNormalArea().width);
        assertEquals(230, panel.getNormalArea().height);
    }

    @Test
    void testPanelDragHandleDoubleClickHandler() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel");
        Area screenArea = new Area(0, 0, 800, 600);
        panel.getArea()
            .set(100, 100, 380, 230);

        ModularGuiContext ctx = mock(ModularGuiContext.class);
        when(ctx.getAbsMouseX()).thenReturn(150);
        when(ctx.getAbsMouseY()).thenReturn(110);

        PanelDragHandle handle = new PanelDragHandle(panel).contextSupplier(() -> ctx)
            .screenAreaSupplier(() -> screenArea);
        handle.onInit();

        assertEquals(SnapMode.NONE, panel.getCurrentSnap());

        // First click: returns true (normal drag start)
        boolean start1 = handle.onDragStart(0);
        assertTrue(start1);
        handle.onDragEnd(true);

        // Second click immediately after at same location: triggers double-click maximize!
        boolean start2 = handle.onDragStart(0);
        assertFalse(start2, "Double-click must consume event and not start dragging");
        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap(), "Panel must be maximized on double-click");

        // Third click on maximized panel:
        boolean start3 = handle.onDragStart(0);
        assertTrue(start3);
        handle.onDragEnd(true);

        // Fourth click immediately after: triggers double-click restore!
        boolean start4 = handle.onDragStart(0);
        assertFalse(start4);
        assertEquals(SnapMode.NONE, panel.getCurrentSnap(), "Panel must be restored on second double-click");
    }

    @Test
    void testDynamicScreenSizeChangeWhileMaximized() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel");
        Area screenArea = new Area(0, 0, 800, 600);
        panel.screenAreaSupplier(() -> screenArea);
        panel.getArea()
            .set(50, 40, 380, 230);

        panel.maximize(screenArea);
        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap());
        Area initialSnap = panel.getSnapArea(panel.getCurrentSnap());
        assertEquals(800, initialSnap.width);
        assertEquals(600, initialSnap.height);

        // GUI scale change: screen expands to 1200x900
        screenArea.set(0, 0, 1200, 900);
        panel.checkScreenDimensions();

        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap());
        Area expandedSnap = panel.getSnapArea(panel.getCurrentSnap());
        assertEquals(1200, expandedSnap.width);
        assertEquals(900, expandedSnap.height);

        // GUI scale change: screen shrinks to 480x270
        screenArea.set(0, 0, 480, 270);
        panel.checkScreenDimensions();

        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap());
        Area shrunkSnap = panel.getSnapArea(panel.getCurrentSnap());
        assertEquals(480, shrunkSnap.width);
        assertEquals(270, shrunkSnap.height);
    }

    @Test
    void testDynamicScreenSizeChangeWhileSnappedLeft() {
        ModularPanel mainPanel = new ModularPanel("main");
        mainPanel.getArea()
            .set(300, 200, 200, 200); // Main GUI at x=300..500
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel", mainPanel);
        Area screenArea = new Area(0, 0, 800, 600);
        panel.screenAreaSupplier(() -> screenArea);

        panel.applySnap(SnapMode.LEFT, screenArea);
        assertEquals(SnapMode.LEFT, panel.getCurrentSnap());
        Area snap1 = panel.getSnapArea(SnapMode.LEFT, screenArea);
        assertEquals(300, snap1.width);
        assertEquals(600, snap1.height);

        // Screen changes to 1200x800, main GUI moves to x=500..700
        screenArea.set(0, 0, 1200, 800);
        mainPanel.getArea()
            .set(500, 300, 200, 200);
        panel.checkScreenDimensions();

        assertEquals(SnapMode.LEFT, panel.getCurrentSnap());
        Area snap2 = panel.getSnapArea(SnapMode.LEFT, screenArea);
        assertEquals(500, snap2.width);
        assertEquals(800, snap2.height);
    }

    @Test
    void testRestoreAfterScreenShrink() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel");
        Area screenArea = new Area(0, 0, 800, 600);
        panel.screenAreaSupplier(() -> screenArea);
        panel.getArea()
            .set(100, 100, 380, 230);

        panel.maximize(screenArea);
        assertEquals(SnapMode.MAXIMIZED, panel.getCurrentSnap());

        // Screen shrinks dramatically to 300x200 (smaller than normal 380x230 panel)
        screenArea.set(0, 0, 300, 200);
        panel.checkScreenDimensions();

        // Restore should clamp dimensions within the smaller 300x200 screen
        panel.restore(true, 0, 0, screenArea);
        assertEquals(SnapMode.NONE, panel.getCurrentSnap());
        assertTrue(panel.getNormalArea().width <= 380);
    }

    @Test
    void testPanelAndHandleClickPrecedence() {
        ResizeableFloatingPanel panel = new ResizeableFloatingPanel("panel");
        assertFalse(panel.canClickThrough(), "Floating panel must not allow clicks through to widgets underneath");

        assertFalse(panel.disablePanelsBelow(), "Non-maximized floating panel should not disable panels below");
        panel.maximize(new Area(0, 0, 800, 600));
        assertTrue(panel.disablePanelsBelow(), "Maximized floating panel must disable panels below");

        PanelDragHandle handle = new PanelDragHandle(panel);
        assertFalse(handle.canClickThrough(), "PanelDragHandle must not allow clicks through");
        assertEquals(
            com.cleanroommc.modularui.api.widget.Interactable.Result.STOP,
            handle.onMousePressed(0),
            "Left click on handle must STOP propagation to lower layers");
        assertEquals(
            com.cleanroommc.modularui.api.widget.Interactable.Result.IGNORE,
            handle.onMousePressed(1),
            "Right click should not stop interaction");
    }

    @Test
    void testNEIHandlerIntersectsAndMaximize() {
        Area panelArea = new Area(10, 10, 100, 50);
        assertTrue(
            com.gtnewhorizons.coolantloops.common.gui.FloatingPanelNEIHandler.intersects(panelArea, 20, 20, 10, 10));
        assertFalse(
            com.gtnewhorizons.coolantloops.common.gui.FloatingPanelNEIHandler.intersects(panelArea, 200, 200, 50, 50));
        assertFalse(com.gtnewhorizons.coolantloops.common.gui.FloatingPanelNEIHandler.intersects(null, 0, 0, 10, 10));
    }
}
