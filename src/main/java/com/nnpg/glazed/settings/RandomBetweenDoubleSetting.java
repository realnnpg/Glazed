package com.nnpg.glazed.settings;

import com.nnpg.glazed.utils.RandomBetweenDouble;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

public class RandomBetweenDoubleSetting extends Setting<RandomBetweenDouble> {
    public final double absoluteMin, absoluteMax;
    public final double sliderMin, sliderMax;
    public final boolean noSlider;

    public RandomBetweenDoubleSetting(String name, String description, RandomBetweenDouble defaultValue, Consumer<RandomBetweenDouble> onChanged, Consumer<Setting<RandomBetweenDouble>> onModuleActivated, IVisible visible, double absoluteMin, double absoluteMax, double sliderMin, double sliderMax, boolean noSlider) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.noSlider = noSlider;
    }

    @Override
    public void resetImpl() {
        value = new RandomBetweenDouble(defaultValue.min, defaultValue.max);
    }

    @Override
    protected RandomBetweenDouble parseImpl(String str) {
        String[] parts = str.split("-");
        if (parts.length != 2) return null;

        try {
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            return new RandomBetweenDouble(min, max);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean set(RandomBetweenDouble value) {
        if (value.min < absoluteMin || value.max > absoluteMax || value.min > value.max) return false;
        return super.set(value);
    }

    @Override
    protected boolean isValueValid(RandomBetweenDouble value) {
        return value.min >= absoluteMin && value.max <= absoluteMax && value.min <= value.max;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        tag.putDouble("min", get().min);
        tag.putDouble("max", get().max);
        return tag;
    }

    @Override
    public RandomBetweenDouble load(NbtCompound tag) {
        double min = tag.contains("min") ? tag.getDouble("min") : defaultValue.min;
        double max = tag.contains("max") ? tag.getDouble("max") : defaultValue.max;
        return new RandomBetweenDouble(min, max);
    }

    public static class Builder extends SettingBuilder<Builder, RandomBetweenDouble, RandomBetweenDoubleSetting> {
        private double absoluteMin = Double.NEGATIVE_INFINITY, absoluteMax = Double.POSITIVE_INFINITY;
        private double sliderMin = 0.0, sliderMax = 10.0;
        private boolean noSlider = false;

        public Builder() {
            super(new RandomBetweenDouble(0.0, 1.0));
        }

        public Builder min(double min) {
            this.absoluteMin = min;
            return this;
        }

        public Builder max(double max) {
            this.absoluteMax = max;
            return this;
        }

        public Builder range(double min, double max) {
            this.absoluteMin = min;
            this.absoluteMax = max;
            return this;
        }

        public Builder sliderRange(double min, double max) {
            this.sliderMin = min;
            this.sliderMax = max;
            return this;
        }

        public Builder defaultMin(double min) {
            if (defaultValue == null) defaultValue = new RandomBetweenDouble(min, 1.0);
            else defaultValue = new RandomBetweenDouble(min, defaultValue.max);
            return this;
        }

        public Builder defaultMax(double max) {
            if (defaultValue == null) defaultValue = new RandomBetweenDouble(0.0, max);
            else defaultValue = new RandomBetweenDouble(defaultValue.min, max);
            return this;
        }

        public Builder defaultRange(double min, double max) {
            defaultValue = new RandomBetweenDouble(min, max);
            return this;
        }

        public Builder noSlider() {
            this.noSlider = true;
            return this;
        }

        @Override
        public RandomBetweenDoubleSetting build() {
            return new RandomBetweenDoubleSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, absoluteMin, absoluteMax, sliderMin, sliderMax, noSlider);
        }
    }
}