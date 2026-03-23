package com.nnpg.glazed.mixins;

import com.nnpg.glazed.modules.pvp.CrystalTweaks;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class CrystalTweaksMixin {

    @Inject(
        method = "clickSlot",
        at = @At("HEAD"),
        cancellable = true
    )
    private void glazed$onClickSlot(
            int syncId,
            int slot,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        CrystalTweaks module = Modules.get().get(CrystalTweaks.class);
        if (module != null && module.isActive() && module.shouldBlockSlotClick(syncId, slot, button, actionType)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "interactBlock",
        at = @At("HEAD"),
        cancellable = true
    )
    private void glazed$onInteractBlock(
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        CrystalTweaks module = Modules.get().get(CrystalTweaks.class);
        if (module != null && module.isActive() && module.shouldBlockInteractBlock(player, hand, hitResult)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
