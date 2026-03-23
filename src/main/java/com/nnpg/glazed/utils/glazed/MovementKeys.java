package com.nnpg.glazed.utils.glazed;

import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.option.KeyBinding;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MovementKeys {
    
    public static void forward(boolean pressed) {
        setKey(mc.options.forwardKey, pressed);
    }
    
    public static void back(boolean pressed) {
        setKey(mc.options.backKey, pressed);
    }
    
    public static void left(boolean pressed) {
        setKey(mc.options.leftKey, pressed);
    }
    
    public static void right(boolean pressed) {
        setKey(mc.options.rightKey, pressed);
    }
    
    public static void jump(boolean pressed) {
        setKey(mc.options.jumpKey, pressed);
    }
    
    public static void sneak(boolean pressed) {
        setKey(mc.options.sneakKey, pressed);
    }
    
    public static void sprint(boolean pressed) {
        setKey(mc.options.sprintKey, pressed);
    }
    
    public static void releaseAll() {
        forward(false);
        back(false);
        left(false);
        right(false);
        jump(false);
        sneak(false);
        sprint(false);
    }
    
    public static boolean isForwardPressed() {
        return mc.options.forwardKey.isPressed();
    }
    
    public static boolean isBackPressed() {
        return mc.options.backKey.isPressed();
    }
    
    public static boolean isLeftPressed() {
        return mc.options.leftKey.isPressed();
    }
    
    public static boolean isRightPressed() {
        return mc.options.rightKey.isPressed();
    }
    
    public static boolean isJumpPressed() {
        return mc.options.jumpKey.isPressed();
    }
    
    public static boolean isSneakPressed() {
        return mc.options.sneakKey.isPressed();
    }
    
    public static boolean isSprintPressed() {
        return mc.options.sprintKey.isPressed();
    }
    
    private static void setKey(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }
}
