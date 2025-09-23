package com.nnpg.glazed.mixins;

import com.nnpg.glazed.gui.widgets.WRandomBetweenDoubleEdit;
import com.nnpg.glazed.gui.widgets.WRandomBetweenIntEdit;
import com.nnpg.glazed.gui.widgets.WTextBox;
import com.nnpg.glazed.settings.RandomBetweenDoubleSetting;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.settings.TextDisplaySetting;
import com.nnpg.glazed.utils.RandomBetweenDouble;
import com.nnpg.glazed.utils.RandomBetweenInt;
import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DefaultSettingsWidgetFactory.class)
public class DefaultSettingsWidgetFactoryMixin {

    @Inject(method = "<init>(Lmeteordevelopment/meteorclient/gui/GuiTheme;)V", at = @At("TAIL"))
    private void addRandomBetweenFactories(GuiTheme theme, CallbackInfo ci) {
        DefaultSettingsWidgetFactory factory = (DefaultSettingsWidgetFactory) (Object) this;
        DefaultSettingsWidgetFactoryAccessor accessor = (DefaultSettingsWidgetFactoryAccessor) factory;

        accessor.getFactories().put(RandomBetweenIntSetting.class,
            (table, setting) -> randomBetweenIntW(table, (RandomBetweenIntSetting) setting, theme));

        accessor.getFactories().put(RandomBetweenDoubleSetting.class,
            (table, setting) -> randomBetweenDoubleW(table, (RandomBetweenDoubleSetting) setting, theme));

        accessor.getFactories().put(TextDisplaySetting.class,
            (table, setting) -> textDisplayW(table, (TextDisplaySetting) setting, theme));
    }

    private void randomBetweenIntW(WTable table, RandomBetweenIntSetting setting, GuiTheme theme) {
        WRandomBetweenIntEdit edit = new WRandomBetweenIntEdit(
            theme,
            setting.get().min, setting.get().max,
            setting.absoluteMin, setting.absoluteMax,
            setting.sliderMin, setting.sliderMax,
            setting.noSlider
        );

        table.add(edit).expandX();

        edit.action = () -> {
            if (!setting.set(new RandomBetweenInt(edit.getMinInt(), edit.getMaxInt()))) {
                edit.set(setting.get().min, setting.get().max);
            }
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            edit.set(setting.get().min, setting.get().max);
        };
    }

    private void randomBetweenDoubleW(WTable table, RandomBetweenDoubleSetting setting, GuiTheme theme) {
        WRandomBetweenDoubleEdit edit = new WRandomBetweenDoubleEdit(
            theme,
            setting.get().min, setting.get().max,
            setting.absoluteMin, setting.absoluteMax,
            setting.sliderMin, setting.sliderMax,
            setting.noSlider
        );

        table.add(edit).expandX();

        edit.action = () -> {
            if (!setting.set(new RandomBetweenDouble(edit.getMin(), edit.getMax()))) {
                edit.set(setting.get().min, setting.get().max);
            }
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            edit.set(setting.get().min, setting.get().max);
        };
    }

    private void textDisplayW(WTable table, TextDisplaySetting setting, GuiTheme theme) {
        WTextBox textBox = new WTextBox(theme, setting.get());
        table.add(textBox).expandCellX();
        // Add empty cell for reset column
        table.add(theme.label(""));
    }
}
