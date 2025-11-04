package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

// ✅ Correct imports for Minecraft 1.21.4+
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.objective.Objective;
import net.minecraft.scoreboard.score.Score;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FakeScoreboard extends Module {
    private static final String SCOREBOARD_NAME = "glazed_custom";
    private Objective customObjective;
    private Objective originalObjective;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private final SettingGroup sgStats = settings.getDefaultGroup();

    private final Setting<String> title = sgStats.add(new StringSetting.Builder()
        .name("title").defaultValue("Glazed on top").build());
    private final Setting<String> money = sgStats.add(new StringSetting.Builder()
        .name("money").defaultValue("67").build());
    private final Setting<String> shards = sgStats.add(new StringSetting.Builder()
        .name("shards").defaultValue("67").build());
    private final Setting<String> kills = sgStats.add(new StringSetting.Builder()
        .name("kills").defaultValue("67").build());
    private final Setting<String> deaths = sgStats.add(new StringSetting.Builder()
        .name("deaths").defaultValue("67").build());
    private final Setting<Integer> keyallStart = sgStats.add(new IntSetting.Builder()
        .name("keyall").description("Starting countdown in minutes").defaultValue(10).range(0, 60).build());
    private final Setting<String> playtimeStart = sgStats.add(new StringSetting.Builder()
        .name("playtime").defaultValue("0h 0m").build());
    private final Setting<String> team = sgStats.add(new StringSetting.Builder()
        .name("team").defaultValue("Glazed on top").build());
    private final Setting<String> footer = sgStats.add(new StringSetting.Builder()
        .name("footer").defaultValue(" Glazed(67ms)").build());

    private int keyallTimer;
    private long playtimeSeconds;
    private Thread updaterThread;
    private volatile boolean running = false;
    private int killsCount;
    private int deathsCount;

    public FakeScoreboard() {
        super(GlazedAddon.esp, "FakeScoreboard", "Custom scoreboard overlay for Glazed.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        originalObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        keyallTimer = keyallStart.get() * 60;
        playtimeSeconds = parsePlaytime(playtimeStart.get());
        killsCount = parseInt(kills.get());
        deathsCount = parseInt(deaths.get());
        running = true;
        updaterThread = new Thread(this::updateLoop, "FakeScoreboard-Updater");
        updaterThread.start();
    }

    @Override
    public void onDeactivate() {
        running = false;
        try {
            if (updaterThread != null) updaterThread.join();
        } catch (InterruptedException ignored) {}

        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (originalObjective != null) scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, originalObjective);
        if (customObjective != null) scoreboard.removeObjective(customObjective);
        customObjective = null;
    }

    private void updateLoop() {
        while (running) {
            try {
                detectKillsDeaths();
                updateScoreboard();
                Thread.sleep(600 + random.nextInt(601));
                keyallTimer--;
                if (keyallTimer <= 0) keyallTimer = 60 * 60;
                playtimeSeconds++;
            } catch (InterruptedException ignored) {}
        }
    }

    private void detectKillsDeaths() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        String playerName = player.getEntityName();
        Objective killsObj = scoreboard.getObjective("playerKills");
        Objective deathsObj = scoreboard.getObjective("playerDeaths");

        if (killsObj != null) {
            Score score = scoreboard.getPlayerScore(playerName, killsObj);
            killsCount = Math.max(killsCount, score.getScore());
        }
        if (deathsObj != null) {
            Score score = scoreboard.getPlayerScore(playerName, deathsObj);
            deathsCount = Math.max(deathsCount, score.getScore());
        }
    }

    private void updateScoreboard() {
        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (customObjective != null) scoreboard.removeObjective(customObjective);
        customObjective = scoreboard.addObjective(
            SCOREBOARD_NAME,
            ScoreboardCriterion.DUMMY,
            gradientTitle(title.get()),
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            (NumberFormat) BlankNumberFormat.INSTANCE
        );
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, customObjective);

        List<MutableText> entries = generateEntriesText();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String teamName = "team_line_" + i;
            Team t = scoreboard.getTeam(teamName);
            if (t == null) t = scoreboard.addTeam(teamName);
            t.setPrefix(entries.get(i));
            teams.add(t);
        }

        for (int i = 0; i < entries.size(); i++) {
            String playerName = "\u00A7" + i;
            Score score = scoreboard.getPlayerScore(playerName, customObjective);
            score.setScore(entries.size() - i);
            scoreboard.addPlayerToTeam(playerName, teams.get(i));
        }
    }

    private List<MutableText> generateEntriesText() {
        return List.of(
            text(" "),
            colored("$ ", 0x00FF00).append(colored("Money: ", 0xFFFFFF)).append(colored(money.get(), 0x00FF00)),
            colored("★ ", 0xA503FC).append(colored("Shards: ", 0xFFFFFF)).append(colored(shards.get(), 0xA503FC)),
            colored("🗡 ", 0xFF0000).append(colored("Kills: ", 0xFFFFFF)).append(colored(String.valueOf(killsCount), 0xFF0000)),
            colored("☠ ", 0xFC7703).append(colored("Deaths: ", 0xFFFFFF)).append(colored(String.valueOf(deathsCount), 0xFC7703)),
            colored("⌛ ", 0x00A2FF).append(colored("Keyall: ", 0xFFFFFF)).append(colored(formatKeyall(), 0x00A2FF)),
            colored("⌚ ", 0xFFE600).append(colored("Playtime: ", 0xFFFFFF)).append(colored(formatPlaytime(), 0xFFE600)),
            colored("🪓 ", 0x00A2FF).append(colored("Team: ", 0xFFFFFF)).append(colored(team.get(), 0x00A2FF)),
            text(" "),
            footerText()
        );
    }

    private String formatKeyall() {
        int m = keyallTimer / 60, s = keyallTimer % 60;
        return String.format("%02dm %02ds", m, s);
    }

    private String formatPlaytime() {
        long totalMin = playtimeSeconds / 60;
        long h = totalMin / 60, m = totalMin % 60;
        return String.format("%dh %dm", h, m);
    }

    private long parsePlaytime(String raw) {
        try {
            String[] p = raw.split(" ");
            return Long.parseLong(p[0].replace("h", "")) * 3600
                + Long.parseLong(p[1].replace("m", "")) * 60;
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseInt(String raw) {
        try { return Integer.parseInt(raw); }
        catch (Exception e) { return 0; }
    }

    private MutableText footerText() {
        int ping = random.nextDouble() < 0.7 ? random.nextInt(37) : 37 + random.nextInt(64);
        String raw = footer.get();
        int start = raw.indexOf('('), end = raw.indexOf(')');
        String region = (start != -1 && end > start) ? raw.substring(0, start).trim() : raw;
        return colored(region + " (" + ping + "ms)", 0xA0A0A0);
    }

    private MutableText colored(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private MutableText text(String s) { return Text.literal(s); }

    private MutableText gradientTitle(String text) { return gradient(text, 0x007CF9, 0x00C6F9); }

    private MutableText gradient(String text, int start, int end) {
        int sr = (start >> 16) & 0xFF, sg = (start >> 8) & 0xFF, sb = start & 0xFF;
        int er = (end >> 16) & 0xFF, eg = (end >> 8) & 0xFF, eb = end & 0xFF;
        MutableText result = Text.empty();
        int len = Math.max(1, text.length());
        for (int i = 0; i < len; i++) {
            float t = (float) i / Math.max(len - 1, 1);
            int r = Math.round(sr + (er - sr) * t);
            int g = Math.round(sg + (eg - sg) * t);
            int b = Math.round(sb + (eb - sb) * t);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((r << 16) | (g << 8) | b))));
        }
        return result;
    }
}
