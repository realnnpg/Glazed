/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.nnpg.glazed;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.util.Util;

import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class MyScreen extends WindowScreen {
    private int latestVersion = -1;
    private boolean isVersionFetched = false;
    private static boolean hasCheckedThisSession = false;

    public MyScreen(GuiTheme theme) {
        super(theme, "Version Check");
        fetchLatestVersion();
    }

    public static void checkVersionOnServerJoin() {
        if (hasCheckedThisSession) return;
        hasCheckedThisSession = true;

        MeteorExecutor.execute(() -> {
            try {
                String versionString = Http.get("https://glazedclient.com/versions/normal1.21.5.txt").sendString();
                if (versionString != null && !versionString.isEmpty()) {
                    int latestVersion = Integer.parseInt(versionString.trim());


                    if (latestVersion > GlazedAddon.MyScreenVERSION) {
                        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                            net.minecraft.client.MinecraftClient.getInstance().setScreen(new MyScreen(meteordevelopment.meteorclient.gui.GuiThemes.get()));
                        });
                    }
                }
            } catch (Exception e) {
            }
        });
    }

    public static void resetSessionCheck() {
        hasCheckedThisSession = false;
    }

    @Override
    public void initWidgets() {
        buildUI();
    }

    private void fetchLatestVersion() {
        MeteorExecutor.execute(() -> {
            try {
                String versionString = Http.get("https://glazedclient.com/versions/normal1.21.5.txt").sendString();
                if (versionString != null && !versionString.isEmpty()) {
                    latestVersion = Integer.parseInt(versionString.trim());
                } else {
                    latestVersion = GlazedAddon.MyScreenVERSION;
                }
                isVersionFetched = true;

                reload();
            } catch (Exception e) {
                latestVersion = GlazedAddon.MyScreenVERSION;
                isVersionFetched = true;
                reload();
            }
        });
    }

    private void buildUI() {
        addWrappedMessage("Welcome to Glazed Client version checker. Here you can see your current version and check for updates.");

        add(theme.horizontalSeparator()).padVertical(theme.scale(4)).expandX();

        addMessage(String.format("Installed Version: %d", GlazedAddon.MyScreenVERSION));

        if (isVersionFetched) {
            addMessage(String.format("Latest Version: %d", latestVersion));
        } else {
            addMessage("Latest Version: Checking...");
        }

        // Always add separator and update message area to maintain consistent height
        add(theme.horizontalSeparator()).padVertical(theme.scale(8)).expandX();

        if (isVersionFetched && latestVersion > GlazedAddon.MyScreenVERSION) {
            addWrappedMessage("You're using an outdated version of the Glazed addon. Please update to the latest version. Newer versions may include important bug fixes, improvements, and additional features.");
        } else if (isVersionFetched && latestVersion == GlazedAddon.MyScreenVERSION) {
            addWrappedMessage("You're using the latest version of the Glazed addon. No update needed.");
        } else {
            addWrappedMessage("Checking for updates...");
        }

        add(theme.horizontalSeparator()).padVertical(theme.scale(8)).expandX();

        // Buttons
        WHorizontalList buttonsContainer = add(theme.horizontalList()).expandX().widget();

        // GitHub button
        WButton githubButton = buttonsContainer.add(theme.button("GitHub")).expandX().widget();
        githubButton.action = () -> {
            Util.getOperatingSystem().open("https://github.com/realnnpg/glazed");
        };

        // Website button
        WButton websiteButton = buttonsContainer.add(theme.button("Website")).expandX().widget();
        websiteButton.action = () -> {
            Util.getOperatingSystem().open("https://glazedclient.com");
        };
    }

    private void addMessage(String message) {
        WHorizontalList l = add(theme.horizontalList()).expandX().widget();
        l.add(theme.label(message)).expandX();
    }

    private void addWrappedMessage(String message) {
        String[] words = message.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > 60) {
                if (currentLine.length() > 0) {
                    addMessage(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }

            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            addMessage(currentLine.toString());
        }
    }
}
