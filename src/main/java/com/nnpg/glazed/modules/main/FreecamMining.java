package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public class FreecamMining extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoMine = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-mine")
        .description("Automatically mines blocks you're looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> holdMine = sgGeneral.add(new BoolSetting.Builder()
        .name("hold-mine")
        .description("Only mines while holding left-click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-block-esp")
        .description("Shows an outline around the block currently being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> espColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of the mining ESP box.")
        .defaultValue(new SettingColor(255, 100, 50, 80))
        .visible(showEsp::get)
        .build()
    );

    private final Freecam freecam = Modules.get().get(Freecam.class);
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private BlockPos currentBlock = null;
    private Direction currentSide = null;

    public FreecamMining() {
        super(GlazedAddon.CATEGORY, "freecam-mining", "Mine blocks while Freecam is active without interacting.");
    }

    @Override
    public void onActivate() {
        if (!freecam.isActive()) freecam.toggle(); // activate Freecam
        currentBlock = null;
        currentSide = null;
    }

    @Override
    public void onDeactivate() {
        if (freecam.isActive()) freecam.toggle(); // deactivate Freecam
        currentBlock = null;
        currentSide = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        PlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;

        // Prevent Freecam from interacting
        mc.crosshairTarget = null;
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.pickItemKey.setPressed(false);

        // Raycast from REAL player
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookVec.multiply(5.0));

        HitResult result = mc.world.raycast(new RaycastContext(
            eyePos,
            endPos,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        BlockPos targetBlock = null;
        Direction targetSide = null;

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) result;
            targetBlock = bhr.getBlockPos();
            targetSide = bhr.getSide();
        }

        // Determine mining
        boolean mining = autoMine.get() || (holdMine.get() &&
            GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);

        if (!mining || targetBlock == null) {
            if (currentBlock != null) {
                im.cancelBlockBreaking();
                currentBlock = null;
                currentSide = null;
            }
            return;
        }

        // Reset progress if new block
        if (currentBlock == null || !currentBlock.equals(targetBlock)) {
            im.cancelBlockBreaking();
            currentBlock = targetBlock;
            currentSide = targetSide;
        }

        boolean breaking = im.updateBlockBreakingProgress(currentBlock, currentSide);
        if (breaking) player.swingHand(Hand.MAIN_HAND);
        else {
            currentBlock = null;
            currentSide = null;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!showEsp.get() || currentBlock == null) return;
        event.renderer.box(currentBlock, espColor.get(), espColor.get(), ShapeMode.Both, 0);
    }
}
