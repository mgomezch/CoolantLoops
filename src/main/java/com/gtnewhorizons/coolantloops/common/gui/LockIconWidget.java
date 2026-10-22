package com.gtnewhorizons.coolantloops.common.gui;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/**
 * Renders a crisp vector lock icon (10x10) indicating whether auto-fit on window resize is locked or unlocked.
 */
public class LockIconWidget extends Widget<LockIconWidget> {

    private final BooleanSupplier lockedSupplier;

    public LockIconWidget(BooleanSupplier lockedSupplier) {
        this.lockedSupplier = lockedSupplier;
        size(10, 10);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        boolean locked = lockedSupplier != null && lockedSupplier.getAsBoolean();

        if (locked) {
            // Closed shackle arch over the body (bright gold / amber)
            GuiDraw.drawRect(3, 1, 4, 1, 0xFFE0E0E0); // Top bar
            GuiDraw.drawRect(2, 2, 1, 3, 0xFFE0E0E0); // Left upright
            GuiDraw.drawRect(7, 2, 1, 3, 0xFFE0E0E0); // Right upright

            // Padlock body
            GuiDraw.drawRect(1, 5, 8, 5, 0xFFFFB300);
            GuiDraw.drawRect(1, 5, 8, 1, 0xFFFFD54F); // Highlight

            // Keyhole
            GuiDraw.drawRect(4, 6, 2, 2, 0xFF1A1A1A);
            GuiDraw.drawRect(4, 8, 2, 1, 0xFF1A1A1A);
        } else {
            // Open shackle (raised, detached on right leaving a visible gap)
            GuiDraw.drawRect(3, 0, 4, 1, 0xFFB0B0B0); // Raised top bar
            GuiDraw.drawRect(2, 1, 1, 4, 0xFFB0B0B0); // Left upright (reaches body at y=5)
            GuiDraw.drawRect(7, 1, 1, 2, 0xFFB0B0B0); // Right upright (stops at y=3, 2px gap above body)

            // Padlock body (slate gray)
            GuiDraw.drawRect(1, 5, 8, 5, 0xFF78909C);
            GuiDraw.drawRect(1, 5, 8, 1, 0xFF90A4AE); // Highlight

            // Keyhole
            GuiDraw.drawRect(4, 6, 2, 2, 0xFF1A1A1A);
            GuiDraw.drawRect(4, 8, 2, 1, 0xFF1A1A1A);
        }
    }
}
