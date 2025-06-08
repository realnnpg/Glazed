/**
package com.nnpg.glazed.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;

import net.minecraft.block.entity.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import baritone.api.BaritoneAPI;
import net.minecraft.world.chunk.WorldChunk;





public class RTPBaseFinder extends Module {
    private boolean hasTeleported = false;
    private BlockPos startPos;

    public RTPBaseFinder() {
        super(Categories.World, "rtp-base-finder", "Uses /rtp and Baritone to find bases underground.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        // Send RTP command
        ChatUtils.sendPlayerMsg("/rtp eu central");
        hasTeleported = false;
        startPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Wait for teleport to finish
        if (!hasTeleported) {
            if (startPos == null || mc.player.age < 20) {  // Simple cooldown
                startPos = mc.player.getBlockPos();
                return;
            }

            // Start baritone pathing to Y=-5 at current X/Z
            BlockPos targetPos = new BlockPos(startPos.getX(), -5, startPos.getZ());
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("#goto " + targetPos.getX() + " -5 " + targetPos.getZ());
            hasTeleported = true;
        }


        for (WorldChunk chunk : ((ClientWorld) mc.world).getChunkManager().getLoadedChunks()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("YOU FOUND A BASE!")));
                    return;
                }
            }
        }

        // Check nearby block entities
        for (BlockEntity blockEntity : mc.world.getBlockEntities().values()) {
         {
                if (blockEntity instanceof ChestBlockEntity) ;
                else if (blockEntity instanceof BarrelBlockEntity) ;
                else if (blockEntity instanceof ShulkerBoxBlockEntity) ;
                else if (blockEntity instanceof EnderChestBlockEntity) ;
                else if (blockEntity instanceof AbstractFurnaceBlockEntity) ;
                else if (blockEntity instanceof DispenserBlockEntity) ;
                else if (blockEntity instanceof HopperBlockEntity) ;
            }
        }
    }
}

 **/
