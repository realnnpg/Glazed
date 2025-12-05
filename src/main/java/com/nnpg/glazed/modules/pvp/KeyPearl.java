package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import org.lwjgl.glfw.GLFW;

public class KeyPearl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> activateKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activate-key")
        .description("The key to throw the pearl.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_G))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay before throwing pearl (ticks).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switch back to previous slot after throwing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Delay after throwing before switching back (ticks).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int prevSlot = -1;
    private int delayCounter = 0;
    private int switchCounter = 0;
    private boolean throwing = false;
    private boolean keyPressedLastTick = false;

    public KeyPearl() {
        super(GlazedAddon.pvp, "key-pearl", "Switches to an ender pearl and throws it when you press the bind.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        boolean keyCurrentlyPressed = activateKey.get().isPressed();

        if (keyCurrentlyPressed && !keyPressedLastTick) {
            throwPearl();
        }

        keyPressedLastTick = keyCurrentlyPressed;

        if (throwing) {
            if (delayCounter < delay.get()) {
                delayCounter++;
                return;
            }

            if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            if (switchBack.get()) {
                if (switchCounter < switchDelay.get()) {
                    switchCounter++;
                    return;
                }
                if (prevSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(prevSlot);
                }
            }

            reset();
        }
    }

    private void throwPearl() {
        if (mc.player == null) return;

        prevSlot = mc.player.getInventory().getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                mc.player.getInventory().setSelectedSlot(i);
                throwing = true;
                delayCounter = 0;
                switchCounter = 0;
                break;
            }
        }
    }

    private void reset() {
        throwing = false;
        delayCounter = 0;
        switchCounter = 0;
        prevSlot = -1;
    }
}
