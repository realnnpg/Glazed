package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class FreecamMining extends Module {
   private final Freecam freecam;
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final SettingGroup sgGeneral;
   private final Setting<Double> reach;

   public FreecamMining() {
      super(GlazedAddon.CATEGORY, "freecam-mining", "Freecam with real-position mining override.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.reach = this.sgGeneral.add(
         ((Builder)((Builder)(new Builder())
            .name("reach"))
            .description("Mining reach from real player position."))
            .defaultValue(5.0D)
            .min(1.0D)
            .max(6.0D)
            .sliderRange(1.0D, 6.0D)
            .build()
      );
      this.freecam = (Freecam)Modules.get().get(Freecam.class);
   }

   public void onActivate() {
      if (!this.freecam.isActive()) {
         this.freecam.toggle();
         this.info("Freecam activated for mining.", new Object[0]);
      }
      // Ensure attack key is not stuck when enabling
      KeyBinding.setKeyPressed(this.mc.options.attackKey.getDefaultKey(), false);
   }

   public void onDeactivate() {
      // Release attack key when module is disabled
      KeyBinding.setKeyPressed(this.mc.options.attackKey.getDefaultKey(), false);
      
      if (this.freecam.isActive()) {
         this.freecam.toggle();
         this.info("Freecam deactivated.", new Object[0]);
      }
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         ClientPlayerEntity player = this.mc.player;
         Vec3d eyePos = player.getEyePos();
         float yaw = player.getYaw();
         float pitch = player.getPitch();
         
         // Calculate look direction vector from yaw and pitch
         Vec3d lookVec = new Vec3d(
            -Math.sin(Math.toRadians((double)yaw)) * Math.cos(Math.toRadians((double)pitch)),
            -Math.sin(Math.toRadians((double)pitch)),
            Math.cos(Math.toRadians((double)yaw)) * Math.cos(Math.toRadians((double)pitch))
         );
         
         // Calculate target position based on reach
         Vec3d targetVec = eyePos.add(lookVec.multiply((Double)this.reach.get()));
         
         // Perform raycast from eye position to target
         HitResult ray = this.mc.world.raycast(
            new RaycastContext(eyePos, targetVec, ShapeType.OUTLINE, FluidHandling.NONE, player)
         );
         
         // If we hit a block, hold attack to mine it continuously
         if (ray.getType() == Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult)ray;
            BlockPos targetPos = blockHit.getBlockPos();
            
            // Hold attack key and update breaking progress for continuous mining
            KeyBinding.setKeyPressed(this.mc.options.attackKey.getDefaultKey(), true);
            this.mc.interactionManager.updateBlockBreakingProgress(targetPos, blockHit.getSide());
         } else {
            // Release attack when not targeting a block
            KeyBinding.setKeyPressed(this.mc.options.attackKey.getDefaultKey(), false);
         }
      }
   }
}
