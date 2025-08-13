package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.state.property.IntProperty;

import java.util.HashSet;
import java.util.Set;

public class KelpESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> kelpColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Kelp ESP box color")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build());

    private final Setting<ShapeMode> kelpShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Kelp ESP box render mode")
        .defaultValue(ShapeMode.Lines)
        .build());

    private final Setting<Boolean> kelpChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce flagged kelp chunks in chat")
        .defaultValue(true)
        .build());

    private final Set<ChunkPos> flaggedKelpChunks = new HashSet<>();

    public KelpESP() {
        super(GlazedAddon.esp, "KelpESP", "ESP for kelp chunks with suspicious patterns.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        flaggedKelpChunks.clear();

        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) scanChunkForKelp(worldChunk);
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        scanChunkForKelp(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;

        Chunk chunk = mc.world.getChunk(pos);
        if (chunk instanceof WorldChunk worldChunk) {
            scanChunkForKelp(worldChunk);
        }
    }

    private void scanChunkForKelp(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        flaggedKelpChunks.remove(cpos);

        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        int kelpColumns = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {

                for (int y = yMin; y < yMax; y++) {
                    BlockState state = chunk.getBlockState(new BlockPos(x, y, z));

                    if (state.getBlock() instanceof KelpBlock /*|| state.getBlock() instanceof KelpPlantBlock*/) {
                       int age = state.get(KelpBlock.AGE);
                        if (age == 25) {
                            kelpColumns++;
                        }
                    }
                    {
                    }

                }
            }

            if (kelpColumns >= 5) {
                flaggedKelpChunks.add(cpos);
                if (kelpChat.get()) {
                    info("§5[§dkelpEsp§5] §akelpEsp§5: §aChunk " + cpos + " flagged: ", kelpColumns + " Kelp Columns are full aged");
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Color side = new Color(kelpColor.get());
        Color outline = new Color(kelpColor.get());
        for (ChunkPos pos : flaggedKelpChunks) {
            event.renderer.box(
                pos.getStartX(), 63, pos.getStartZ(),
                pos.getStartX() + 16, 63, pos.getStartZ() + 16,
                side, outline, kelpShapeMode.get(), 0);
        }
    }
}
