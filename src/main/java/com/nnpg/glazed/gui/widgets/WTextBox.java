package com.nnpg.glazed.gui.widgets;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;

public class WTextBox extends WWidget {
    private final String text;
    private final GuiTheme theme;
    private List<String> wrappedLines;

    public WTextBox(GuiTheme theme, String text) {
        this.text = text;
        this.theme = theme;
        this.wrappedLines = new ArrayList<>();
        wrapText();
    }

    private void wrapText() {
        wrappedLines.clear();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        // Calculate available width based on parent container
        double availableWidth = getAvailableWidth();

        // If we can't determine width yet, don't wrap
        if (availableWidth <= 0) {
            wrappedLines.add(text);
            return;
        }

        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            double testWidth = theme.textWidth(testLine);

            if (testWidth > availableWidth) {
                if (currentLine.length() > 0) {
                    wrappedLines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    wrappedLines.add(word);
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            wrappedLines.add(currentLine.toString());
        }
    }

    private double getAvailableWidth() {
        // Use the top-level container (parent.parent.parent) which is the actual menu width
        if (parent != null && parent.parent != null && parent.parent.parent != null && parent.parent.parent.width > 0) {
            return parent.parent.parent.width * 0.995;
        }

        // If top-level not available, try the next level
        if (parent != null && parent.parent != null && parent.parent.width > 0) {
            return parent.parent.width * 0.995;
        }

        // If that's not available, try direct parent
        if (parent != null && parent.width > 0) {
            return parent.width * 0.995;
        }

        // No valid parent found
        return 0;
    }

    @Override
    protected void onCalculateSize() {
        // Re-wrap with current parent information if available
        double availableWidth = getAvailableWidth();
        if (availableWidth > 0) {
            int oldLineCount = wrappedLines.size();
            wrapText();

            // If line count changed, invalidate the layout
            if (oldLineCount != wrappedLines.size()) {
                invalidate();
            }
        }

        width = 0; // Don't set a width - let the table handle it
        height = theme.textHeight() * wrappedLines.size();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double currentY = y;
        // Calculate the parent table's left position to align with setting names
        double parentX = parent != null ? parent.x : x;

        for (String line : wrappedLines) {
            renderer.text(line, parentX - 5, currentY, Color.LIGHT_GRAY, false);
            currentY += theme.textHeight();
        }
    }
}
