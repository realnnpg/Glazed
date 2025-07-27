package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.nnpg.glazed.VersionUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.screen.slot.SlotActionType;
import java.util.Random;

public class AnchorMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> whileUse = sgGeneral.add(new BoolSetting.Builder()
        .name("while-use")
        .description("If it should trigger while eating/using shield")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> stopOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-kill")
        .description("Doesn't anchor if body nearby")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> clickSimulation = sgGeneral.add(new BoolSetting.Builder()
        .name("click-simulation")
        .description("Makes the CPS hud think you're legit")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> switchBackToAnchor = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back-to-anchor")
        .description("Switches back to respawn anchor after exploding")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> switchChance = sgGeneral.add(new IntSetting.Builder()
        .name("switch-chance")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );
    private final Setting<Integer> placeChance = sgGeneral.add(new IntSetting.Builder()
        .name("place-chance")
        .description("Randomization")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );
    private final Setting<Integer> glowstoneDelay = sgGeneral.add(new IntSetting.Builder()
        .name("glowstone-delay")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> glowstoneChance = sgGeneral.add(new IntSetting.Builder()
        .name("glowstone-chance")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );
    private final Setting<Integer> explodeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("explode-delay")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> explodeChance = sgGeneral.add(new IntSetting.Builder()
        .name("explode-chance")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );
    private final Setting<Integer> explodeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("explode-slot")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMax(9)
        .build()
    );
    private final Setting<Boolean> onlyOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("only-own")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> onlyCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("only-charge")
        .defaultValue(false)
        .build()
    );

    private int switchClock = 0;
    private int glowstoneClock = 0;
    private int explodeClock = 0;
    private boolean shouldSwitchBackToAnchor = false;

    private final Set<BlockPos> ownedAnchors = new HashSet<>();
    private final Random random = new Random();

    public AnchorMacro() {
        super(GlazedAddon.CATEGORY, "AnchorMacro", "Automatically blows up respawn anchors for you");
    }

    @Override
    public void onActivate() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        shouldSwitchBackToAnchor = false;
    }

    @Override
    public void onDeactivate() {
        // No event bus unregister needed, handled by Meteor
    }

    private boolean isAnchorCharged(BlockPos pos) {
        // Respawn anchor is charged if its "charges" block state > 0
        return mc.world.getBlockState(pos).contains(net.minecraft.state.property.Properties.CHARGES)
            && mc.world.getBlockState(pos).get(net.minecraft.state.property.Properties.CHARGES) > 0;
    }

    private boolean isAnchorNotCharged(BlockPos pos) {
        return mc.world.getBlockState(pos).contains(net.minecraft.state.property.Properties.CHARGES)
            && mc.world.getBlockState(pos).get(net.minecraft.state.property.Properties.CHARGES) < 4;
    }

    private int findAnchorSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.RESPAWN_ANCHOR)) {
                return i;
            }
        }
        return -1;
    }

    private static final Set<net.minecraft.item.Item> FOOD_ITEMS = new HashSet<>(Arrays.asList(
        Items.BREAD,
        Items.APPLE,
        Items.COOKED_BEEF,
        Items.COOKED_PORKCHOP,
        Items.COOKED_CHICKEN,
        Items.COOKED_MUTTON,
        Items.COOKED_RABBIT,
        Items.COOKED_COD,
        Items.COOKED_SALMON,
        Items.CARROT,
        Items.POTATO,
        Items.BAKED_POTATO,
        Items.GOLDEN_APPLE,
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.BEETROOT,
        Items.BEETROOT_SOUP,
        Items.MELON_SLICE,
        Items.PUMPKIN_PIE,
        Items.MUSHROOM_STEW,
        Items.SUSPICIOUS_STEW,
        Items.SWEET_BERRIES,
        Items.GLOW_BERRIES
    ));

    private boolean isFood(ItemStack stack) {
        return FOOD_ITEMS.contains(stack.getItem());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        // Handle switching back to anchor after explosion
        if (shouldSwitchBackToAnchor && switchBackToAnchor.get()) {
            int anchorSlot = findAnchorSlot();
            if (anchorSlot != -1) {
                VersionUtil.setSelectedSlot(mc.player, anchorSlot);
            }
            shouldSwitchBackToAnchor = false;
            return;
        }

        // TODO: Implement stopOnKill logic if WorldUtils.isDeadBodyNearby() equivalent exists

        boolean rightClick = mc.options.useKey.isPressed();
        if (!rightClick) return;

        if (!whileUse.get()) {
            boolean isUsing = false;
            ItemStack main = mc.player.getMainHandStack();
            ItemStack off = mc.player.getOffHandStack();
            if (main.getItem() instanceof ShieldItem || off.getItem() instanceof ShieldItem || isFood(main) || isFood(off)) {
                isUsing = true;
            }
            if (isUsing) return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();

        if (mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR) {
            if (onlyOwn.get() && !ownedAnchors.contains(pos)) return;

            // Place glowstone if anchor is not fully charged
            if (isAnchorNotCharged(pos)) {
                int rand = random.nextInt(100) + 1;
                if (rand > placeChance.get()) return;

                if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    if (switchClock != switchDelay.get()) {
                        switchClock++;
                        return;
                    }
                    rand = random.nextInt(100) + 1;
                    if (rand > switchChance.get()) return;
                    switchClock = 0;
                    // Switch to glowstone in hotbar
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) {
                            VersionUtil.setSelectedSlot(mc.player, i);
                            break;
                        }
                    }
                }
                if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    if (glowstoneClock != glowstoneDelay.get()) {
                        glowstoneClock++;
                        return;
                    }
                    rand = random.nextInt(100) + 1;
                    if (rand > glowstoneChance.get()) return;
                    glowstoneClock = 0;
                    if (clickSimulation.get()) {
                        // Simulate right click
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    } else {
                        // Place block
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    }
                }
            }
            // Explode anchor if charged
            if (isAnchorCharged(pos)) {
                int slot = explodeSlot.get() - 1;
                if (mc.player.getInventory().selectedSlot != slot) {
                    if (switchClock != switchDelay.get()) {
                        switchClock++;
                        return;
                    }
                    int rand = random.nextInt(100) + 1;
                    if (rand > switchChance.get()) return;
                    switchClock = 0;
                    VersionUtil.setSelectedSlot(mc.player, slot);
                }
                if (mc.player.getInventory().selectedSlot == slot) {
                    if (explodeClock != explodeDelay.get()) {
                        explodeClock++;
                        return;
                    }
                    int rand = random.nextInt(100) + 1;
                    if (rand > explodeChance.get()) return;
                    explodeClock = 0;
                    if (!onlyCharge.get()) {
                        if (clickSimulation.get()) {
                            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        } else {
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        }
                        ownedAnchors.remove(pos);

                        // Set flag to switch back to anchor on next tick
                        if (switchBackToAnchor.get()) {
                            shouldSwitchBackToAnchor = true;
                        }
                    }
                }
            }
        }
    }

}
