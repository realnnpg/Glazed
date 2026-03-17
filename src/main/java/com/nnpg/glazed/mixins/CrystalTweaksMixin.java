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

/**
 * Two injections into ClientPlayerInteractionManager:
 *
 *  1. clickSlot    — HEAD, cancellable
 *     Handles: Cursor Guard, Hotbar Lock, Totem Slot Protection
 *     Fires before Minecraft applies the inventory change client-side
 *     AND before the ClickSlotC2SPacket is sent.
 *
 *  2. interactBlock — HEAD, cancellable
 *     Handles: Glowstone Block, Anchor Max Fill
 *     Fires before the block interaction is processed client-side
 *     AND before PlayerInteractBlockC2SPacket is sent.
 *     Returns ActionResult.FAIL to abort cleanly.
 *
 * ⚠ IMPORTANT — Register in glazed.mixins.json:
 *   "mixins": [ "CrystalTweaksMixin", ... ]
 */
@Mixin(ClientPlayerInteractionManager.class)
public class CrystalTweaksMixin {

    // -------------------------------------------------------------------------
    // 1. clickSlot
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 2. interactBlock
    // -------------------------------------------------------------------------

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
