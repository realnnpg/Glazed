package com.nnpg.glazed.mixins;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenMixin {
    @Accessor("focusedSlot")
    Slot glazed$getFocusedSlot();
}
