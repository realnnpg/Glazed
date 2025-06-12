package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.BlockPos;

public class CoordSnapper extends Module {

    public CoordSnapper() {
        super(GlazedAddon.CATEGORY, "CoordSnapper", "Copies your coordinates to clipboard");
    }

    @Override
    public void onActivate() {
        try {
            if (mc.player == null) {
                error("Player is null!");
                toggle();
                return;
            }

            BlockPos pos = mc.player.getBlockPos();
            String coords = String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());

            // Use Meteor's clipboard method
            mc.keyboard.setClipboard(coords);

            info("Copied coordinates: " + coords);

        } catch (Exception e) {
            error("Failed to copy coordinates: " + e.getMessage());
        } finally {
            // Always deactivate
            toggle();
        }
    }

}
