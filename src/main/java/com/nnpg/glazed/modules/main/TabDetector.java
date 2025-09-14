package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;

import java.util.*;

public class TabDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> targetPlayers = sgGeneral.add(new StringListSetting.Builder()
        .name("target-players")
        .description("List of player names to detect when they join")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when target players are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> logOffline = sgGeneral.add(new BoolSetting.Builder()
        .name("log-offline")
        .description("Also log when target players go offline")
        .defaultValue(true)
        .build()
    );

    private final Set<String> currentTargetPlayers = new HashSet<>();
    private final Set<String> previousTargetPlayers = new HashSet<>();

    public TabDetector() {
        super(GlazedAddon.CATEGORY, "TabDetector", "Detects when specific players join or leave the server");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        currentTargetPlayers.clear();

        Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();

        for (PlayerListEntry entry : playerList) {
            String playerName = entry.getProfile().getName();

            for (String targetName : targetPlayers.get()) {
                if (targetName.equalsIgnoreCase(playerName)) {
                    currentTargetPlayers.add(playerName);
                    break;
                }
            }
        }

        Set<String> newPlayers = new HashSet<>(currentTargetPlayers);
        newPlayers.removeAll(previousTargetPlayers);

        if (!newPlayers.isEmpty()) {
            handlePlayerJoin(newPlayers);
        }

        if (logOffline.get()) {
            Set<String> leftPlayers = new HashSet<>(previousTargetPlayers);
            leftPlayers.removeAll(currentTargetPlayers);

            if (!leftPlayers.isEmpty()) {
                handlePlayerLeave(leftPlayers);
            }
        }

        previousTargetPlayers.clear();
        previousTargetPlayers.addAll(currentTargetPlayers);
    }

    private void handlePlayerJoin(Set<String> players) {
        String playerList = String.join(", ", players);
        String message = players.size() == 1 ?
            String.format("Target player joined: (highlight)%s", playerList) :
            String.format("Target players joined: (highlight)%s", playerList);

        switch (notificationMode.get()) {
            case Chat -> info(message);
            case Toast -> {
                String toastMessage = players.size() == 1 ? "Target Player Joined!" : "Target Players Joined!";
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(toastMessage)));
            }
            case Both -> {
                info(message);
                String toastMessage = players.size() == 1 ? "Target Player Joined!" : "Target Players Joined!";
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(toastMessage)));
            }
        }

    }

    private void handlePlayerLeave(Set<String> players) {
        String playerList = String.join(", ", players);
        String message = players.size() == 1 ?
            String.format("Target player left: (highlight)%s", playerList) :
            String.format("Target players left: (highlight)%s", playerList);

        switch (notificationMode.get()) {
            case Chat -> info(message);
            case Toast -> {
                String toastMessage = players.size() == 1 ? "Target Player Left!" : "Target Players Left!";
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(toastMessage)));
            }
            case Both -> {
                info(message);
                String toastMessage = players.size() == 1 ? "Target Player Left!" : "Target Players Left!";
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(toastMessage)));
            }
        }


    }

    @Override
    public void onActivate() {
        currentTargetPlayers.clear();
        previousTargetPlayers.clear();

        if (mc.getNetworkHandler() != null) {
            Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry : playerList) {
                String playerName = entry.getProfile().getName();
                for (String targetName : targetPlayers.get()) {
                    if (targetName.equalsIgnoreCase(playerName)) {
                        previousTargetPlayers.add(playerName);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        currentTargetPlayers.clear();
        previousTargetPlayers.clear();
    }

    @Override
    public String getInfoString() {
        if (currentTargetPlayers.isEmpty()) {
            return targetPlayers.get().isEmpty() ? "No targets" : null;
        }
        return String.valueOf(currentTargetPlayers.size());
    }

    public enum Mode {
        Chat("Chat"),
        Toast("Toast"),
        Both("Both");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
