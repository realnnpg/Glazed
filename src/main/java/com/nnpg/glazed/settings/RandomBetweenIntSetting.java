package com.nnpg.glazed.settings;

import com.nnpg.glazed.utils.RandomBetweenInt;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

public class RandomBetweenIntSetting extends Setting<RandomBetweenInt> {
    public final int absoluteMin, absoluteMax;
    public final int sliderMin, sliderMax;
    public final boolean noSlider;

    public RandomBetweenIntSetting(String name, String description, RandomBetweenInt defaultValue, Consumer<RandomBetweenInt> onChanged, Consumer<Setting<RandomBetweenInt>> onModuleActivated, IVisible visible, int absoluteMin, int absoluteMax, int sliderMin, int sliderMax, boolean noSlider) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.noSlider = noSlider;
    }

    @Override
    public void resetImpl() {
        value = new RandomBetweenInt(defaultValue.min, defaultValue.max);
    }

    @Override
    protected RandomBetweenInt parseImpl(String str) {
        String[] parts = str.split("-");
        if (parts.length != 2) return null;

        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new RandomBetweenInt(min, max);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean set(RandomBetweenInt value) {
        if (value.min < absoluteMin || value.max > absoluteMax || value.min > value.max) return false;
        return super.set(value);
    }

    @Override
    protected boolean isValueValid(RandomBetweenInt value) {
        return value.min >= absoluteMin && value.max <= absoluteMax && value.min <= value.max;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        tag.putInt("min", get().min);
        tag.putInt("max", get().max);
        return tag;
    }

    @Override
    public RandomBetweenInt load(NbtCompound tag) {
        int min = tag.contains("min") ? tag.getInt("min") : defaultValue.min;
        int max = tag.contains("max") ? tag.getInt("max") : defaultValue.max;
        return new RandomBetweenInt(min, max);
    }

    public static class Builder extends SettingBuilder<Builder, RandomBetweenInt, RandomBetweenIntSetting> {
        private int absoluteMin = Integer.MIN_VALUE, absoluteMax = Integer.MAX_VALUE;
        private int sliderMin = 0, sliderMax = 100;
        private boolean noSlider = false;

        public Builder() {
            super(new RandomBetweenInt(0, 10));
        }

        public Builder min(int min) {
            this.absoluteMin = min;
            return this;
        }

        public Builder max(int max) {
            this.absoluteMax = max;
            return this;
        }

        public Builder range(int min, int max) {
            this.absoluteMin = min;
            this.absoluteMax = max;
            return this;
        }

        public Builder sliderRange(int min, int max) {
            this.sliderMin = min;
            this.sliderMax = max;
            return this;
        }

        public Builder defaultMin(int min) {
            if (defaultValue == null) defaultValue = new RandomBetweenInt(min, 10);
            else defaultValue = new RandomBetweenInt(min, defaultValue.max);
            return this;
        }

        public Builder defaultMax(int max) {
            if (defaultValue == null) defaultValue = new RandomBetweenInt(0, max);
            else defaultValue = new RandomBetweenInt(defaultValue.min, max);
            return this;
        }

        public Builder defaultRange(int min, int max) {
            defaultValue = new RandomBetweenInt(min, max);
            return this;
        }

        public Builder noSlider() {
            this.noSlider = true;
            return this;
        }

        @Override
        public RandomBetweenIntSetting build() {
            return new RandomBetweenIntSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, absoluteMin, absoluteMax, sliderMin, sliderMax, noSlider);
        }
    }
}