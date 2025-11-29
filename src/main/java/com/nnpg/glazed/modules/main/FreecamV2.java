package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.option.Perspective;
import org.joml.Vector3d;

public class FreecamV2 extends Module {
    private final SettingGroup sgGeneral;
    private final Setting<Double> speed;
    private final Setting<Double> speedScrollSensitivity;
    private final Setting<Boolean> allowMining;
    private final Setting<Boolean> toggleOnDamage;
    private final Setting<Boolean> toggleOnDeath;
    private final Setting<Boolean> toggleOnLog;
    private final Setting<Boolean> reloadChunks;
    private final Setting<Boolean> renderHands;
    private final Setting<Boolean> rotate;
    private final Setting<Boolean> staticView;

    public final Vector3d pos;
    public final Vector3d prevPos;

    private Perspective perspective;
    private double speedValue;
    public float yaw;
    public float pitch;
    public float lastYaw;
    public float lastPitch;
    private double fovScale;
    private boolean bobView;

    private boolean forward;
    private boolean backward;
    private boolean right;
    private boolean left;
    private boolean up;
    private boolean down;

    public FreecamV2() {
        super(GlazedAddon.CATEGORY, "freecam-v2", "Allows the camera to move away from the player while still allowing mining and interaction.");
        this.sgGeneral = this.settings.getDefaultGroup();

        this.speed = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Your speed while in freecam.")
            .defaultValue(1.0)
            .min(0.0)
            .onChanged(val -> this.speedValue = val)
            .build());

        this.speedScrollSensitivity = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("speed-scroll-sensitivity")
            .description("Allows you to change speed value using scroll wheel. 0 to disable.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(2.0)
            .build());

        this.allowMining = this.sgGeneral.add(new BoolSetting.Builder()
            .name("allow-mining")
            .description("Allows you to mine and interact while in freecam.")
            .defaultValue(true)
            .build());

        this.toggleOnDamage = this.sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-on-damage")
            .description("Disables freecam when you take damage.")
            .defaultValue(false)
            .build());

        this.toggleOnDeath = this.sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-on-death")
            .description("Disables freecam when you die.")
            .defaultValue(false)
            .build());

        this.toggleOnLog = this.sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-on-log")
            .description("Disables freecam when you disconnect from a server.")
            .defaultValue(true)
            .build());

        this.reloadChunks = this.sgGeneral.add(new BoolSetting.Builder()
            .name("reload-chunks")
            .description("Disables cave culling.")
            .defaultValue(true)
            .build());

        this.renderHands = this.sgGeneral.add(new BoolSetting.Builder()
            .name("show-hands")
            .description("Whether or not to render your hands in freecam.")
            .defaultValue(true)
            .build());

        this.rotate = this.sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates to the block or entity you are looking at.")
            .defaultValue(false)
            .build());

        this.staticView = this.sgGeneral.add(new BoolSetting.Builder()
            .name("static")
            .description("Disables settings that move the view.")
            .defaultValue(true)
            .build());

        this.pos = new Vector3d();
        this.prevPos = new Vector3d();
    }

    @Override
    public void onActivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            this.toggle();
        } else {
            this.fovScale = mc.options.getFov().getValue();
            this.bobView = mc.options.getBobView().getValue();

            if (this.staticView.get()) {
                mc.options.getFov().setValue(0);
                mc.options.getBobView().setValue(false);
            }

            this.yaw = mc.player.getYaw();
            this.pitch = mc.player.getPitch();
            this.perspective = mc.options.getPerspective();
            this.speedValue = this.speed.get();

            Vec3d playerPos = mc.player.getPos();
            this.pos.set(playerPos.x, playerPos.y, playerPos.z);
            this.prevPos.set(playerPos.x, playerPos.y, playerPos.z);

            if (this.perspective == Perspective.THIRD_PERSON_BACK) {
                this.yaw += 180.0F;
                this.pitch *= -1.0F;
            }

            this.lastYaw = this.yaw;
            this.lastPitch = this.pitch;

            this.forward = Input.isPressed(mc.options.forwardKey);
            this.backward = Input.isPressed(mc.options.backKey);
            this.right = Input.isPressed(mc.options.rightKey);
            this.left = Input.isPressed(mc.options.leftKey);
            this.up = Input.isPressed(mc.options.jumpKey);
            this.down = Input.isPressed(mc.options.sneakKey);

            this.unpress();

            if (this.reloadChunks.get()) {
                mc.worldRenderer.reload();
            }

            this.info("FreecamV2 enabled - Mining and interaction " + (this.allowMining.get() ? "allowed" : "disabled"));
        }
    }

    @Override
    public void onDeactivate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (this.reloadChunks.get()) {
            mc.execute(mc.worldRenderer::reload);
        }

        mc.options.setPerspective(this.perspective);

        if (this.staticView.get()) {
            mc.options.getFov().setValue((int)this.fovScale);
            mc.options.getBobView().setValue(this.bobView);
        }

        this.info("FreecamV2 disabled");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        this.unpress();
        this.prevPos.set(this.pos);
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
    }

    private void unpress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onTick(Post event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!this.perspective.isFirstPerson()) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        // Calculate forward direction vector from yaw
        double yawRad = Math.toRadians(this.yaw);
        Vec3d forward = new Vec3d(
            -Math.sin(yawRad),
            0.0,
            Math.cos(yawRad)
        );
        
        // Calculate right direction vector (90 degrees to the right)
        double rightYawRad = Math.toRadians(this.yaw + 90.0F);
        Vec3d right = new Vec3d(
            -Math.sin(rightYawRad),
            0.0,
            Math.cos(rightYawRad)
        );

        double velX = 0.0;
        double velY = 0.0;
        double velZ = 0.0;

        if (this.rotate.get() && mc.targetedEntity != null) {
            Entity target = mc.targetedEntity;
            Vec3d targetPos = target.getPos().add(0, target.getEyeHeight(target.getPose()), 0);
            Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos), 0, null);
        } else if (this.rotate.get() && mc.crosshairTarget != null) {
            Vec3d hitPos = mc.crosshairTarget.getPos();
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 0, null);
        }

        double s = 0.5;
        if (Input.isPressed(mc.options.sprintKey)) {
            s = 1.0;
        }

        boolean a = false;
        if (this.forward) {
            velX += forward.x * s * this.speedValue;
            velZ += forward.z * s * this.speedValue;
            a = true;
        }

        if (this.backward) {
            velX -= forward.x * s * this.speedValue;
            velZ -= forward.z * s * this.speedValue;
            a = true;
        }

        boolean b = false;
        if (this.right) {
            velX += right.x * s * this.speedValue;
            velZ += right.z * s * this.speedValue;
            b = true;
        }

        if (this.left) {
            velX -= right.x * s * this.speedValue;
            velZ -= right.z * s * this.speedValue;
            b = true;
        }

        if (a && b) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (this.up) {
            velY += s * this.speedValue;
        }

        if (this.down) {
            velY -= s * this.speedValue;
        }

        this.prevPos.set(this.pos);
        this.pos.set(this.pos.x + velX, this.pos.y + velY, this.pos.z + velZ);
    }

    @EventHandler(priority = 100)
    public void onKey(KeyEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!Input.isKeyPressed(292)) {
            if (!this.checkGuiMove()) {
                boolean cancel = !this.allowMining.get();

                if (mc.options.forwardKey.matchesKey(event.key, 0)) {
                    this.forward = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.forwardKey.setPressed(false);
                } else if (mc.options.backKey.matchesKey(event.key, 0)) {
                    this.backward = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.backKey.setPressed(false);
                } else if (mc.options.rightKey.matchesKey(event.key, 0)) {
                    this.right = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.rightKey.setPressed(false);
                } else if (mc.options.leftKey.matchesKey(event.key, 0)) {
                    this.left = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.leftKey.setPressed(false);
                } else if (mc.options.jumpKey.matchesKey(event.key, 0)) {
                    this.up = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.jumpKey.setPressed(false);
                } else if (mc.options.sneakKey.matchesKey(event.key, 0)) {
                    this.down = event.action != KeyAction.Release;
                    if (!this.allowMining.get()) mc.options.sneakKey.setPressed(false);
                } else {
                    cancel = false;
                }

                if (cancel) event.cancel();
            }
        }
    }

    @EventHandler(priority = 100)
    private void onMouseButton(MouseButtonEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (!this.checkGuiMove()) {
            boolean cancel = !this.allowMining.get();

            if (mc.options.forwardKey.matchesMouse(event.button)) {
                this.forward = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.forwardKey.setPressed(false);
            } else if (mc.options.backKey.matchesMouse(event.button)) {
                this.backward = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.backKey.setPressed(false);
            } else if (mc.options.rightKey.matchesMouse(event.button)) {
                this.right = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.rightKey.setPressed(false);
            } else if (mc.options.leftKey.matchesMouse(event.button)) {
                this.left = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.leftKey.setPressed(false);
            } else if (mc.options.jumpKey.matchesMouse(event.button)) {
                this.up = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.jumpKey.setPressed(false);
            } else if (mc.options.sneakKey.matchesMouse(event.button)) {
                this.down = event.action != KeyAction.Release;
                if (!this.allowMining.get()) mc.options.sneakKey.setPressed(false);
            } else {
                cancel = false;
            }

            if (cancel) event.cancel();
        }
    }

    @EventHandler(priority = -100)
    private void onMouseScroll(MouseScrollEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (this.speedScrollSensitivity.get() > 0.0 && mc.currentScreen == null) {
            this.speedValue += event.value * 0.25 * this.speedScrollSensitivity.get() * this.speedValue;
            if (this.speedValue < 0.1) this.speedValue = 0.1;
            event.cancel();
        }
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (this.toggleOnLog.get()) {
            this.toggle();
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket) {
            EntityStatusS2CPacket packet = (EntityStatusS2CPacket) event.packet;
            Entity entity = packet.getEntity(MinecraftClient.getInstance().world);
            
            if (entity == MinecraftClient.getInstance().player && this.toggleOnDeath.get()) {
                this.toggle();
                this.info("Toggled off because you died.");
            }
        } else if (event.packet instanceof EntityDamageS2CPacket) {
            if (MinecraftClient.getInstance().player != null && this.toggleOnDamage.get()) {
                this.toggle();
                this.info("Toggled off because you took damage.");
            }
        }
    }

    private boolean checkGuiMove() {
        MinecraftClient mc = MinecraftClient.getInstance();
        GUIMove guiMove = (GUIMove) Modules.get().get(GUIMove.class);
        
        if (mc.currentScreen != null && !guiMove.isActive()) {
            return true;
        }
        return mc.currentScreen != null && guiMove.isActive() && guiMove.skip();
    }

    public void changeLookDirection(double deltaX, double deltaY) {
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.yaw += (float) deltaX;
        this.pitch += (float) deltaY;
        this.pitch = MathHelper.clamp(this.pitch, -90.0F, 90.0F);
    }

    public boolean renderHands() {
        return !this.isActive() || this.renderHands.get();
    }

    public boolean allowMining() {
        return this.allowMining.get();
    }

    public double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.x, this.pos.x);
    }

    public double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.y, this.pos.y);
    }

    public double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.z, this.pos.z);
    }

    public double getYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastYaw, this.yaw);
    }

    public double getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastPitch, this.pitch);
    }
}