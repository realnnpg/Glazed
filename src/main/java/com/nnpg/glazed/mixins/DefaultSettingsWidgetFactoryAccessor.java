package com.nnpg.glazed.mixins;

import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SettingsWidgetFactory.class)
public interface DefaultSettingsWidgetFactoryAccessor {
    @Accessor("factories")
    Map<Class<?>, SettingsWidgetFactory.Factory> getFactories();
}