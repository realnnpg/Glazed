package com.nnpg.glazed.gui.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.MathHelper;

public class WRandomBetweenSlider extends WWidget implements MeteorWidget {
    public Runnable action;
    public Runnable actionOnRelease;

    private double valueMin, valueMax;
    private final double min, max;
    private final int decimalPlaces;

    private boolean draggingMin = false;
    private boolean draggingMax = false;
    private boolean minHandleMouseOver = false;
    private boolean maxHandleMouseOver = false;
    private double valueMinAtDragStart;
    private double valueMaxAtDragStart;

    public WRandomBetweenSlider(double valueMin, double valueMax, double min, double max, int decimalPlaces) {
        this.valueMin = MathHelper.clamp(valueMin, min, max);
        this.valueMax = MathHelper.clamp(valueMax, min, max);
        this.min = min;
        this.max = max;
        this.decimalPlaces = decimalPlaces;
    }

    protected double handleSize() {
        return theme.textHeight();
    }

    @Override
    protected void onCalculateSize() {
        double s = handleSize();
        width = s * 6;
        height = s;
    }

    public void setMin(double min) {
        if (min > valueMax) min = valueMax;
        if (min < this.min) min = this.min;
        this.valueMin = min;
    }

    public void setMax(double max) {
        if (max < valueMin) max = valueMin;
        if (max > this.max) max = this.max;
        this.valueMax = max;
    }

    public void set(double min, double max) {
        setMin(min);
        setMax(max);
    }

    public double getMin() {
        return valueMin;
    }

    public double getMax() {
        return valueMax;
    }

    private double getMinHandlePos() {
        double valuePercentage = (valueMin - min) / (max - min);
        return valuePercentage * (width - handleSize());
    }

    private double getMaxHandlePos() {
        double valuePercentage = (valueMax - min) / (max - min);
        return valuePercentage * (width - handleSize());
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        if (mouseOver && !used) {
            valueMinAtDragStart = valueMin;
            valueMaxAtDragStart = valueMax;

            double handleSize = handleSize();
            double minHandleX = x + handleSize / 2 + getMinHandlePos();
            double maxHandleX = x + handleSize / 2 + getMaxHandlePos();

            double distToMin = Math.abs(mouseX - minHandleX);
            double distToMax = Math.abs(mouseX - maxHandleX);

            if (distToMin <= distToMax && distToMin <= handleSize / 2) {
                draggingMin = true;
                return true;
            } else if (distToMax <= handleSize / 2) {
                draggingMax = true;
                return true;
            } else {
                double clickValue = ((mouseX - (x + handleSize / 2)) / (width - handleSize)) * (max - min) + min;
                if (Math.abs(clickValue - valueMin) <= Math.abs(clickValue - valueMax)) {
                    setMin(clickValue);
                    draggingMin = true;
                } else {
                    setMax(clickValue);
                    draggingMax = true;
                }
                if (action != null) action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        double handleSize = handleSize();
        double minHandleX = x + handleSize / 2 + getMinHandlePos();
        double maxHandleX = x + handleSize / 2 + getMaxHandlePos();

        minHandleMouseOver = Math.abs(mouseX - minHandleX) <= handleSize / 2 &&
                           mouseY >= y && mouseY <= y + height;
        maxHandleMouseOver = Math.abs(mouseX - maxHandleX) <= handleSize / 2 &&
                           mouseY >= y && mouseY <= y + height;

        boolean mouseOverX = mouseX >= x + handleSize / 2 && mouseX <= x + handleSize / 2 + width - handleSize;
        mouseOver = mouseOverX && mouseY >= y && mouseY <= y + height;

        if (draggingMin || draggingMax) {
            if (mouseOverX) {
                double valueWidth = mouseX - (x + handleSize / 2);
                valueWidth = MathHelper.clamp(valueWidth, 0, width - handleSize);
                double newValue = (valueWidth / (width - handleSize)) * (max - min) + min;

                if (draggingMin) {
                    setMin(newValue);
                } else {
                    setMax(newValue);
                }
                if (action != null) action.run();
            }
        }
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingMin || draggingMax) {
            if ((valueMin != valueMinAtDragStart || valueMax != valueMaxAtDragStart) && actionOnRelease != null) {
                actionOnRelease.run();
            }
            draggingMin = false;
            draggingMax = false;
            return true;
        }
        return false;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double handleSize = handleSize();
        double minHandlePos = getMinHandlePos();
        double maxHandlePos = getMaxHandlePos();

        renderBar(renderer, minHandlePos, maxHandlePos, handleSize);
        renderHandles(renderer, minHandlePos, maxHandlePos, handleSize);
    }

    private void renderBar(GuiRenderer renderer, double minHandlePos, double maxHandlePos, double handleSize) {
        MeteorGuiTheme theme = theme();
        double s = theme.scale(3);
        double barX = x + handleSize / 2;
        double barY = y + height / 2 - s / 2;
        double barWidth = width - handleSize;

        if (minHandlePos > 0) {
            renderer.quad(barX, barY, minHandlePos, s, theme.sliderRight.get());
        }

        double activeWidth = maxHandlePos - minHandlePos;
        if (activeWidth > 0) {
            renderer.quad(barX + minHandlePos, barY, activeWidth, s, theme.sliderLeft.get());
        }

        double rightStart = maxHandlePos;
        double rightWidth = barWidth - rightStart;
        if (rightWidth > 0) {
            renderer.quad(barX + rightStart, barY, rightWidth, s, theme.sliderRight.get());
        }
    }

    private void renderHandles(GuiRenderer renderer, double minHandlePos, double maxHandlePos, double handleSize) {
        MeteorGuiTheme theme = theme();

        Color minColor = theme.sliderHandle.get(draggingMin, minHandleMouseOver);
        renderer.quad(x + minHandlePos, y, handleSize, handleSize, GuiRenderer.CIRCLE, minColor);

        Color maxColor = theme.sliderHandle.get(draggingMax, maxHandleMouseOver);
        renderer.quad(x + maxHandlePos, y, handleSize, handleSize, GuiRenderer.CIRCLE, maxColor);
    }

    @Override
    public String toString() {
        if (decimalPlaces == 0) {
            return String.format("%.0f - %.0f", valueMin, valueMax);
        } else {
            return String.format("%." + decimalPlaces + "f - %." + decimalPlaces + "f", valueMin, valueMax);
        }
    }
}