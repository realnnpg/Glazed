package com.nnpg.glazed.utils.glazed;

import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.option.KeyBinding;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * MovementKeys Utility - Vereinfachte Steuerung von Movement-Keys
 * 
 * Verwendung in Modulen:
 *   MovementKeys.forward(true);  // Vorwärts laufen
 *   MovementKeys.jump(true);     // Springen
 *   MovementKeys.sneak(true);    // Schleichen
 *   MovementKeys.releaseAll();   // Alle Keys loslassen
 */
public class MovementKeys {
    
    /**
     * Setzt den Forward-Key (W)
     */
    public static void forward(boolean pressed) {
        setKey(mc.options.forwardKey, pressed);
    }
    
    /**
     * Setzt den Back-Key (S)
     */
    public static void back(boolean pressed) {
        setKey(mc.options.backKey, pressed);
    }
    
    /**
     * Setzt den Left-Key (A)
     */
    public static void left(boolean pressed) {
        setKey(mc.options.leftKey, pressed);
    }
    
    /**
     * Setzt den Right-Key (D)
     */
    public static void right(boolean pressed) {
        setKey(mc.options.rightKey, pressed);
    }
    
    /**
     * Setzt den Jump-Key (Space)
     */
    public static void jump(boolean pressed) {
        setKey(mc.options.jumpKey, pressed);
    }
    
    /**
     * Setzt den Sneak-Key (Shift)
     */
    public static void sneak(boolean pressed) {
        setKey(mc.options.sneakKey, pressed);
    }
    
    /**
     * Setzt den Sprint-Key (Ctrl)
     */
    public static void sprint(boolean pressed) {
        setKey(mc.options.sprintKey, pressed);
    }
    
    /**
     * Lässt alle Movement-Keys los
     */
    public static void releaseAll() {
        forward(false);
        back(false);
        left(false);
        right(false);
        jump(false);
        sneak(false);
        sprint(false);
    }
    
    /**
     * Prüft ob Forward-Key gedrückt ist
     */
    public static boolean isForwardPressed() {
        return mc.options.forwardKey.isPressed();
    }
    
    /**
     * Prüft ob Back-Key gedrückt ist
     */
    public static boolean isBackPressed() {
        return mc.options.backKey.isPressed();
    }
    
    /**
     * Prüft ob Left-Key gedrückt ist
     */
    public static boolean isLeftPressed() {
        return mc.options.leftKey.isPressed();
    }
    
    /**
     * Prüft ob Right-Key gedrückt ist
     */
    public static boolean isRightPressed() {
        return mc.options.rightKey.isPressed();
    }
    
    /**
     * Prüft ob Jump-Key gedrückt ist
     */
    public static boolean isJumpPressed() {
        return mc.options.jumpKey.isPressed();
    }
    
    /**
     * Prüft ob Sneak-Key gedrückt ist
     */
    public static boolean isSneakPressed() {
        return mc.options.sneakKey.isPressed();
    }
    
    /**
     * Prüft ob Sprint-Key gedrückt ist
     */
    public static boolean isSprintPressed() {
        return mc.options.sprintKey.isPressed();
    }
    
    /**
     * Zentrale Methode zum Setzen eines Movement-Keys.
     * Setzt sowohl KeyBinding als auch Meteor's Input-System.
     */
    private static void setKey(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }
}
