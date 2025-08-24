package com.nnpg.glazed.utils.glazed;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class KeyUtils {

    public static CharSequence getKey(int key) {
        switch (key) {
            case 2 -> {
                return EncryptedString.of("MMB");
            }
            case -1 -> {
                return EncryptedString.of("Unknown");
            }
            case 256 -> {
                return EncryptedString.of("Esc");
            }
            case 96 -> {
                return EncryptedString.of("Grave Accent");
            }
            case 161 -> {
                return EncryptedString.of("World 1");
            }
            case 162 -> {
                return EncryptedString.of("World 2");
            }
            case 283 -> {
                return EncryptedString.of("Print Screen");
            }
            case 284 -> {
                return EncryptedString.of("Pause");
            }
            case 260 -> {
                return EncryptedString.of("Insert");
            }
            case 261 -> {
                return EncryptedString.of("Delete");
            }
            case 268 -> {
                return EncryptedString.of("Home");
            }
            case 266 -> {
                return EncryptedString.of("Page Up");
            }
            case 267 -> {
                return EncryptedString.of("Page Down");
            }
            case 269 -> {
                return EncryptedString.of("End");
            }
            case 258 -> {
                return EncryptedString.of("Tab");
            }
            case 341 -> {
                return EncryptedString.of("Left Control");
            }
            case 345 -> {
                return EncryptedString.of("Right Control");
            }
            case 342 -> {
                return EncryptedString.of("Left Alt");
            }
            case 346 -> {
                return EncryptedString.of("Right Alt");
            }
            case 340 -> {
                return EncryptedString.of("Left Shift");
            }
            case 344 -> {
                return EncryptedString.of("Right Shift");
            }
            case 265 -> {
                return EncryptedString.of("Arrow Up");
            }
            case 264 -> {
                return EncryptedString.of("Arrow Down");
            }
            case 263 -> {
                return EncryptedString.of("Arrow Left");
            }
            case 262 -> {
                return EncryptedString.of("Arrow Right");
            }
            case 39 -> {
                return EncryptedString.of("Apostrophe");
            }
            case 259 -> {
                return EncryptedString.of("Backspace");
            }
            case 280 -> {
                return EncryptedString.of("Caps Lock");
            }
            case 348 -> {
                return EncryptedString.of("Menu");
            }
            case 343 -> {
                return EncryptedString.of("Left Super");
            }
            case 347 -> {
                return EncryptedString.of("Right Super");
            }
            case 257 -> {
                return EncryptedString.of("Enter");
            }
            case 335 -> {
                return EncryptedString.of("Numpad Enter");
            }
            case 282 -> {
                return EncryptedString.of("Num Lock");
            }
            case 32 -> {
                return EncryptedString.of("Space");
            }
            case 290 -> {
                return EncryptedString.of("F1");
            }
            case 291 -> {
                return EncryptedString.of("F2");
            }
            case 292 -> {
                return EncryptedString.of("F3");
            }
            case 293 -> {
                return EncryptedString.of("F4");
            }
            case 294 -> {
                return EncryptedString.of("F5");
            }
            case 295 -> {
                return EncryptedString.of("F6");
            }
            case 296 -> {
                return EncryptedString.of("F7");
            }
            case 297 -> {
                return EncryptedString.of("F8");
            }
            case 298 -> {
                return EncryptedString.of("F9");
            }
            case 299 -> {
                return EncryptedString.of("F10");
            }
            case 300 -> {
                return EncryptedString.of("F11");
            }
            case 301 -> {
                return EncryptedString.of("F12");
            }
            case 302 -> {
                return EncryptedString.of("F13");
            }
            case 303 -> {
                return EncryptedString.of("F14");
            }
            case 304 -> {
                return EncryptedString.of("F15");
            }
            case 305 -> {
                return EncryptedString.of("F16");
            }
            case 306 -> {
                return EncryptedString.of("F17");
            }
            case 307 -> {
                return EncryptedString.of("F18");
            }
            case 308 -> {
                return EncryptedString.of("F19");
            }
            case 309 -> {
                return EncryptedString.of("F20");
            }
            case 310 -> {
                return EncryptedString.of("F21");
            }
            case 311 -> {
                return EncryptedString.of("F22");
            }
            case 312 -> {
                return EncryptedString.of("F23");
            }
            case 313 -> {
                return EncryptedString.of("F24");
            }
            case 314 -> {
                return EncryptedString.of("F25");
            }
            case 281 -> {
                return EncryptedString.of("Scroll Lock");
            }
            case 91 -> {
                return EncryptedString.of("Left Bracket");
            }
            case 93 -> {
                return EncryptedString.of("Right Bracket");
            }
            case 59 -> {
                return EncryptedString.of("Semicolon");
            }
            case 61 -> {
                return EncryptedString.of("Equals");
            }
            case 92 -> {
                return EncryptedString.of("Backslash");
            }
            case 44 -> {
                return EncryptedString.of("Comma");
            }
            case 0 -> {
                return EncryptedString.of("LMB");
            }
            case 1 -> {
                return EncryptedString.of("RMB");
            }
        }
        String keyName = GLFW.glfwGetKeyName(key, 0);
        if (keyName == null) {
            return EncryptedString.of("None");
        }
        return StringUtils.capitalize(keyName);
    }

    public static boolean isKeyPressed(final int n) {
        if (n <= 8) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), n) == 1;
        }
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), n) == 1;
    }
}
