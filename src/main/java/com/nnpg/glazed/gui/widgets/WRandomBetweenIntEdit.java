package com.nnpg.glazed.gui.widgets;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

public class WRandomBetweenIntEdit extends WHorizontalList {
    public Runnable action;

    private final WTextBox minEdit, maxEdit;
    private final WRandomBetweenSlider slider;

    private final int absoluteMin, absoluteMax;
    private final int sliderMin, sliderMax;
    private final boolean noSlider;

    public WRandomBetweenIntEdit(GuiTheme theme, int min, int max, int absoluteMin, int absoluteMax, int sliderMin, int sliderMax, boolean noSlider) {
        super();
        this.theme = theme;
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.noSlider = noSlider;

        minEdit = add(theme.textBox(String.valueOf(min), this::filter)).minWidth(40).widget();

        if (!noSlider) {
            slider = add(new WRandomBetweenSlider(min, max, sliderMin, sliderMax, 0)).expandX().widget();
        } else {
            slider = null;
        }

        maxEdit = add(theme.textBox(String.valueOf(max), this::filter)).minWidth(40).widget();

        minEdit.action = () -> {
            try {
                int newMin = Integer.parseInt(minEdit.get());
                newMin = Math.max(absoluteMin, Math.min(absoluteMax, newMin));
                if (newMin > getMaxInt()) {
                    maxEdit.set(String.valueOf(newMin));
                }
                if (!noSlider && slider != null) {
                    slider.set(newMin, getMaxInt());
                }
                if (action != null) action.run();
            } catch (NumberFormatException e) {
                minEdit.set(String.valueOf(getMinInt()));
            }
        };

        maxEdit.action = () -> {
            try {
                int newMax = Integer.parseInt(maxEdit.get());
                newMax = Math.max(absoluteMin, Math.min(absoluteMax, newMax));
                if (newMax < getMinInt()) {
                    minEdit.set(String.valueOf(newMax));
                }
                if (!noSlider && slider != null) {
                    slider.set(getMinInt(), newMax);
                }
                if (action != null) action.run();
            } catch (NumberFormatException e) {
                maxEdit.set(String.valueOf(getMaxInt()));
            }
        };

        if (slider != null) {
            slider.action = () -> {
                minEdit.set(String.valueOf((int) slider.getMin()));
                maxEdit.set(String.valueOf((int) slider.getMax()));
                if (action != null) action.run();
            };
        }
    }

    public void set(int min, int max) {
        minEdit.set(String.valueOf(min));
        maxEdit.set(String.valueOf(max));
        if (slider != null) {
            slider.set(min, max);
        }
    }

    public int getMinInt() {
        try {
            return Integer.parseInt(minEdit.get());
        } catch (NumberFormatException e) {
            return absoluteMin;
        }
    }

    public int getMaxInt() {
        try {
            return Integer.parseInt(maxEdit.get());
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
        else good = Character.isDigit(c);

        if (good && validate) {
            try {
                Integer.parseInt(text + c);
            } catch (NumberFormatException ignored) {
                good = false;
            }
        }

        return good;
    }
}