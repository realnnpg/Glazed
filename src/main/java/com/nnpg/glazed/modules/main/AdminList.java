package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.Arrays;
import java.util.List;

public class AdminList extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<List<String>> admins = sgGeneral.add(new StringListSetting.Builder()
            .name("admins")
            .description("List of admin names to be treated as whitelisted.")
            .defaultValue(Arrays.asList("drdonutt"))
            .build());

    public AdminList() {
        super(GlazedAddon.CATEGORY, "admin-list", "List of administrators to be ignored by other modules.");
    }

    public boolean isAdmin(String name) {
        if (!isActive())
            return false;
        return admins.get().stream().anyMatch(admin -> admin.equalsIgnoreCase(name));
    }
}
