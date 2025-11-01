package com.nnpg.glazed.gui.themes.meteor;

import com.nnpg.glazed.gui.widgets.WRandomBetweenSlider;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WMeteorRandomBetweenSlider extends WRandomBetweenSlider {
    protected final double min, max;
    protected final int decimalPlaces;
    protected boolean mouseOverMin = false;
    protected boolean mouseOverMax = false;
    protected boolean draggingMin = false;
    protected boolean draggingMax = false;

    public WMeteorRandomBetweenSlider(double valueMin, double valueMax, double min, double max, int decimalPlaces) {
        super(valueMin, valueMax, min, max, decimalPlaces);
        this.min = min;
        this.max = max;
        this.decimalPlaces = decimalPlaces;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color trackColor = new Color(100, 100, 100, 255);
        Color rangeColor = theme.textColor();
        Color handleColor = theme.textSecondaryColor();
        Color activeColor = theme.textColor();

        renderer.quad(x, y + height / 2 - 1, width, 2, trackColor);

        double minHandleX = getMinHandleX();
        double maxHandleX = getMaxHandleX();

        if (maxHandleX > minHandleX) {
            renderer.quad(x + minHandleX, y + height / 2 - 1, maxHandleX - minHandleX, 2, rangeColor);
        }

        Color minColor = mouseOverMin || draggingMin ? activeColor : handleColor;
        renderer.quad(x + minHandleX - 3, y + height / 2 - 6, 6, 12, minColor);

        Color maxColor = mouseOverMax || draggingMax ? activeColor : handleColor;
        renderer.quad(x + maxHandleX - 3, y + height / 2 - 6, 6, 12, maxColor);

        String minText = String.format("%." + decimalPlaces + "f", getMin());
        String maxText = String.format("%." + decimalPlaces + "f", getMax());

        double textY = y + height + 2;
        renderer.text(minText, x + minHandleX, textY, theme.textColor(), false);
        renderer.text(maxText, x + maxHandleX, textY, theme.textColor(), false);
    }

    private double getMinHandleX() {
        return (getMin() - min) / (max - min) * width;
    }

    private double getMaxHandleX() {
        return (getMax() - min) / (max - min) * width;
    }
}