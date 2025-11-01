package com.nnpg.glazed.settings;

import meteordevelopment.meteorclient.settings.*;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

public class TextDisplaySetting extends Setting<String> {

    public TextDisplaySetting(String name, String description, String defaultValue, Consumer<String> onChanged, Consumer<Setting<String>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    public String getTitle() {
        // Return empty string so no title is shown
        return "";
    }

    @Override
    public void resetImpl() {
        value = defaultValue;
    }

    @Override
    protected String parseImpl(String str) {
        return str;
    }

    @Override
    protected boolean isValueValid(String value) {
        return true;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        tag.putString("value", get());
        return tag;
    }

    @Override
    public String load(NbtCompound tag) {
        return tag.contains("value") ? tag.getString("value") : defaultValue;
    }

    public static class Builder extends SettingBuilder<Builder, String, TextDisplaySetting> {
        public Builder() {
            super("");
        }

        @Override
        public TextDisplaySetting build() {
            return new TextDisplaySetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}