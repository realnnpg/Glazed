package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FakeScoreboard extends Module {
    private static final String SCOREBOARD_NAME = "glazed_custom";

    private ScoreboardObjective customObjective;
    private ScoreboardObjective originalObjective;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    private final SettingGroup sgStats = settings.getDefaultGroup();

    private final Setting<String> title = sgStats.add(new StringSetting.Builder()
        .name("title").defaultValue("Glazed on top").build());
    private final Setting<String> money = sgStats.add(new StringSetting.Builder()
        .name("money").defaultValue("67").build());
    private final Setting<String> shards = sgStats.add(new StringSetting.Builder()
        .name("shards").defaultValue("67").build());
    private final Setting<String> killsStart = sgStats.add(new StringSetting.Builder()
        .name("kills").defaultValue("0").build());
    private final Setting<String> deathsStart = sgStats.add(new StringSetting.Builder()
        .name("deaths").defaultValue("0").build());
    private final Setting<Integer> keyallStart = sgStats.add(new IntSetting.Builder()
        .name("keyall").description("Starting countdown in minutes").defaultValue(10).range(0, 60).build());
    private final Setting<String> playtimeStart = sgStats.add(new StringSetting.Builder()
        .name("playtime").defaultValue("0h 0m").build());
    private final Setting<String> team = sgStats.add(new StringSetting.Builder()
        .name("team").defaultValue("Glazed on top").build());
    private final Setting<String> footer = sgStats.add(new StringSetting.Builder()
        .name("footer").defaultValue(" Glazed(67ms)").build());

    // Runtime state
    private int keyallTimer;
    private long playtimeSeconds;
    private int kills;
    private int deaths;
    private int ping = 67;
    private boolean running = false;
    private Thread updaterThread;
    private Thread pingThread;

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
        kills = parseInt(killsStart.get());
        deaths = parseInt(deathsStart.get());

        running = true;
        updaterThread = new Thread(this::updateLoop, "FakeScoreboard-Updater");
        pingThread = new Thread(this::pingLoop, "FakeScoreboard-PingUpdater");
        updaterThread.start();
        pingThread.start();
    }

    @Override
    public void onDeactivate() {
        running = false;
        try {
            if (updaterThread != null) updaterThread.join();
            if (pingThread != null) pingThread.join();
        } catch (InterruptedException ignored) {}

        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (originalObjective != null)
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, originalObjective);
        if (customObjective != null)
            scoreboard.removeObjective(customObjective);
        customObjective = null;
    }

    // === Main update loop (kills, deaths, playtime, scoreboard) ===
    private void updateLoop() {
        while (running) {
            try {
                detectKillsDeaths();
                updateScoreboard();
                Thread.sleep(1000);
                keyallTimer--;
                if (keyallTimer <= 0) keyallTimer = 60 * 60;
                playtimeSeconds++;
            } catch (InterruptedException ignored) {}
        }
    }

    // === Random ping loop ===
    private void pingLoop() {
        while (running) {
            ping = weightedPing();
            try {
                long delay = (long) (600 + random.nextDouble() * 600); // 0.6–1.2 sec
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {}
        }
    }

    // Weighted random ping — low values much more likely
    private int weightedPing() {
        double r = random.nextDouble();
        if (r < 0.75) return random.nextInt(37);   // 0–36 most common
        else if (r < 0.95) return 37 + random.nextInt(20); // 37–56 less common
        else return 57 + random.nextInt(44);       // 57–100 rare
    }

    // === Detects kills and deaths (increments counters) ===
    private void detectKillsDeaths() {
        if (mc.player == null || mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        String playerName = mc.player.getEntityName();

        ScoreboardObjective killsObj = scoreboard.getObjective("playerKills");
        ScoreboardObjective deathsObj = scoreboard.getObjective("playerDeaths");

        if (killsObj != null) {
            int liveKills = scoreboard.getPlayerScore(playerName, killsObj).getScore();
            if (liveKills > kills) kills = liveKills;
        }
        if (deathsObj != null) {
            int liveDeaths = scoreboard.getPlayerScore(playerName, deathsObj).getScore();
            if (liveDeaths > deaths) deaths = liveDeaths;
        }
    }

    // === Builds and sets the scoreboard ===
    private void updateScoreboard() {
        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        if (customObjective != null)
            scoreboard.removeObjective(customObjective);

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
            String holderName = "\u00A7" + i;
            ScoreHolder holder = ScoreHolder.fromName(holderName);
            scoreboard.removeScore(holder, customObjective);
            ScoreAccess score = scoreboard.getOrCreateScore(holder, customObjective);
            score.setScore(entries.size() - i);
            scoreboard.addScoreHolderToTeam(holder.getNameForScoreboard(), teams.get(i));
        }
    }

    private List<MutableText> generateEntriesText() {
        return List.of(
            text(" "),
            colored("$ ", 0x00FF00).append(colored("Money: ", 0xFFFFFF)).append(colored(money.get(), 0x00FF00)),
            colored("★ ", 0xA503FC).append(colored("Shards: ", 0xFFFFFF)).append(colored(shards.get(), 0xA503FC)),
            colored("🗡 ", 0xFF0000).append(colored("Kills: ", 0xFFFFFF)).append(colored(String.valueOf(kills), 0xFF0000)),
            colored("☠ ", 0xFC7703).append(colored("Deaths: ", 0xFFFFFF)).append(colored(String.valueOf(deaths), 0xFC7703)),
            colored("⌛ ", 0x00A2FF).append(colored("Keyall: ", 0xFFFFFF)).append(colored(formatKeyall(), 0x00A2FF)),
            colored("⌚ ", 0xFFE600).append(colored("Playtime: ", 0xFFFFFF)).append(colored(formatPlaytime(), 0xFFE600)),
            colored("🪓 ", 0x00A2FF).append(colored("Team: ", 0xFFFFFF)).append(colored(team.get(), 0x00A2FF)),
            text(" "),
            footerText()
        );
    }

    private String formatKeyall() {
        int m = keyallTimer / 60;
        int s = keyallTimer % 60;
        return String.format("%02dm %02ds", m, s);
    }

    private String formatPlaytime() {
        long totalMin = playtimeSeconds / 60;
        long h = totalMin / 60;
        long m = totalMin % 60;
        return String.format("%dh %dm", h, m);
    }

    private long parsePlaytime(String raw) {
        try {
            String[] p = raw.split(" ");
            return Long.parseLong(p[0].replace("h","")) * 3600 +
                   Long.parseLong(p[1].replace("m","")) * 60;
        } catch (Exception e) { return 0; }
    }

    private int parseInt(String raw) {
        try { return Integer.parseInt(raw); }
        catch (Exception e) { return 0; }
    }

    private MutableText footerText() {
        String raw = footer.get();
        int start = raw.indexOf('(');
        String region = start != -1 ? raw.substring(0, start).trim() : raw;
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
