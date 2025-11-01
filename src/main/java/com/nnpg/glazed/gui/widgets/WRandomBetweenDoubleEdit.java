package com.nnpg.glazed.gui.widgets;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

public class WRandomBetweenDoubleEdit extends WHorizontalList {
    public Runnable action;

    private final WTextBox minEdit, maxEdit;
    private final WRandomBetweenSlider slider;

    private final double absoluteMin, absoluteMax;
    private final double sliderMin, sliderMax;
    private final boolean noSlider;

    public WRandomBetweenDoubleEdit(GuiTheme theme, double min, double max, double absoluteMin, double absoluteMax, double sliderMin, double sliderMax, boolean noSlider) {
        super();
        this.theme = theme;
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.noSlider = noSlider;

        minEdit = add(theme.textBox(String.format("%.2f", min), this::filter)).minWidth(50).widget();

        if (!noSlider) {
            slider = add(new WRandomBetweenSlider(min, max, sliderMin, sliderMax, 2)).expandX().widget();
        } else {
            slider = null;
        }

        maxEdit = add(theme.textBox(String.format("%.2f", max), this::filter)).minWidth(50).widget();

        minEdit.action = () -> {
            try {
                double newMin = Double.parseDouble(minEdit.get());
                newMin = Math.max(absoluteMin, Math.min(absoluteMax, newMin));
                if (newMin > getMax()) {
                    maxEdit.set(String.format("%.2f", newMin));
                }
                if (!noSlider && slider != null) {
                    slider.set(newMin, getMax());
                }
                if (action != null) action.run();
            } catch (NumberFormatException e) {
                minEdit.set(String.format("%.2f", getMin()));
            }
        };

        maxEdit.action = () -> {
            try {
                double newMax = Double.parseDouble(maxEdit.get());
                newMax = Math.max(absoluteMin, Math.min(absoluteMax, newMax));
                if (newMax < getMin()) {
                    minEdit.set(String.format("%.2f", newMax));
                }
                if (!noSlider && slider != null) {
                    slider.set(getMin(), newMax);
                }
                if (action != null) action.run();
            } catch (NumberFormatException e) {
                maxEdit.set(String.format("%.2f", getMax()));
            }
        };

        if (slider != null) {
            slider.action = () -> {
                minEdit.set(String.format("%.2f", slider.getMin()));
                maxEdit.set(String.format("%.2f", slider.getMax()));
                if (action != null) action.run();
            };
        }
    }

    public void set(double min, double max) {
        minEdit.set(String.format("%.2f", min));
        maxEdit.set(String.format("%.2f", max));
        if (slider != null) {
            slider.set(min, max);
        }
    }

    public double getMin() {
        try {
            return Double.parseDouble(minEdit.get());
        } catch (NumberFormatException e) {
            return absoluteMin;
        }
    }

    public double getMax() {
        try {
            return Double.parseDouble(maxEdit.get());
        } catch (NumberFormatException e) {
            return absoluteMax;
        }
    }

    private boolean filter(String text, char c) {
        boolean good;
        boolean validate = true;

        if (c == '-' && !text.contains("-")) {
            good = true;
            validate = false;
        }
        else if (c == '.' && !text.contains(".")) good = true;
        else good = Character.isDigit(c);

        if (good && validate) {
            try {
                Double.parseDouble(text + c);
            } catch (NumberFormatException ignored) {
                good = false;
            }
        }

        return good;
    }
}