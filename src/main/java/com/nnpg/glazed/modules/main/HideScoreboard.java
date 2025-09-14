package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;

public class HideScoreboard extends Module {
    private ScoreboardObjective savedObjective = null;

    public HideScoreboard() {
        super(GlazedAddon.CATEGORY, "hide-scoreboard", "Hides the sidebar scoreboard.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective current = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        if (current != null) {
            // Save it once
            if (savedObjective == null) savedObjective = current;
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null || savedObjective == null) return;

        // Restore the scoreboard
        mc.world.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, savedObjective);
        savedObjective = null;
    }
}
