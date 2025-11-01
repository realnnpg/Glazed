package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;

public class FakeScoreboard extends Module {
    private static final String SCOREBOARD_NAME = "glazed_custom";
    private ScoreboardObjective customObjective;
    private ScoreboardObjective originalObjective;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgStats = settings.getDefaultGroup();

    private final Setting<String> title = sgStats.add(new StringSetting.Builder()
        .name("title")
        .defaultValue("Glazed on top")
        .build());

    private final Setting<String> money = sgStats.add(new StringSetting.Builder()
        .name("money")
        .defaultValue("67")
        .build());

    private final Setting<String> shards = sgStats.add(new StringSetting.Builder()
        .name("shards")
        .defaultValue("67")
        .build());

    private final Setting<String> kills = sgStats.add(new StringSetting.Builder()
        .name("kills")
        .defaultValue("67")
        .build());

    private final Setting<String> deaths = sgStats.add(new StringSetting.Builder()
        .name("deaths")
        .defaultValue("67")
        .build());

    private final Setting<String> keyall = sgStats.add(new StringSetting.Builder()
        .name("keyall")
        .defaultValue("67")
        .build());

    private final Setting<String> playtime = sgStats.add(new StringSetting.Builder()
        .name("playtime")
        .defaultValue("6h 7m")
        .build());

    private final Setting<String> team = sgStats.add(new StringSetting.Builder()
        .name("team")
        .defaultValue("Glazed on top")
        .build());

    private final Setting<String> footer = sgStats.add(new StringSetting.Builder()
        .name("footer")
        .defaultValue(" Glazed(67ms)") 
        .build());

    public FakeScoreboard() {
        super(GlazedAddon.esp, "FakeScoreboard", "Custom scoreboard overlay for Glazed.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        originalObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        updateScoreboard();
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        if (originalObjective != null)
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, originalObjective);
        if (customObjective != null)
            scoreboard.removeObjective(customObjective);

        customObjective = null;
    }

    public void updateScoreboard() {
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
            colored("🗡 ", 0xFF0000).append(colored("Kills: ", 0xFFFFFF)).append(colored(kills.get(), 0xFF0000)),
            colored("☠ ", 0xFC7703).append(colored("Deaths: ", 0xFFFFFF)).append(colored(deaths.get(), 0xFC7703)),
            colored("⌛ ", 0x00A2FF).append(colored("Keyall: ", 0xFFFFFF)).append(colored(keyall.get(), 0x00A2FF)),
            colored("⌚ ", 0xFFE600).append(colored("Playtime: ", 0xFFFFFF)).append(colored(playtime.get(), 0xFFE600)),
            colored("🪓 ", 0x00A2FF).append(colored("Team: ", 0xFFFFFF)).append(colored(team.get(), 0x00A2FF)),
            text(" "),
            footerText()
        );
    }

    private MutableText footerText() {
        String raw = footer.get();
        int start = raw.indexOf('(');
        int end = raw.indexOf(')');

        if (start == -1 || end == -1 || end <= start) {
            return colored(raw, 0xA0A0A0);
        }

        String region = raw.substring(0, start).trim();
        String pingValue = raw.substring(start + 1, end).trim();

        MutableText result = colored(region + " ", 0xA0A0A0)
            .append(colored("(", 0xA0A0A0))
            .append(colored(pingValue, 0x00A2FF))
            .append(colored(")", 0xA0A0A0));

        return result;
    }

    private MutableText colored(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private MutableText text(String s) {
        return Text.literal(s);
    }

    private MutableText gradientTitle(String text) {
        return gradient(text, 0x007CF9, 0x00C6F9);
    }

    private MutableText gradientFooter(String text) {
        return gradient(text, 0x00C6F9, 0x007CF9);
    }

    private MutableText gradient(String text, int startColor, int endColor) {
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        MutableText result = Text.empty();
        int len = Math.max(1, text.length());
        for (int i = 0; i < len; i++) {
            float t = (float) i / Math.max(len - 1, 1);
            int r = Math.round(startR + (endR - startR) * t);
            int g = Math.round(startG + (endG - startG) * t);
            int b = Math.round(startB + (endB - startB) * t);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((r << 16) | (g << 8) | b))));
        }
        return result;
    }
}