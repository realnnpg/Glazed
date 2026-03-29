package com.nnpg.glazed.modules.esp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.ProjectileEntitySimulator;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PearlLandingPredictor extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter  = settings.createGroup("Player Filter");
    private final SettingGroup sgRender  = settings.createGroup("Render");
    private final SettingGroup sgColors  = settings.createGroup("Colors");

    public enum ListMode { Whitelist, Blacklist }

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Also track your own thrown ender pearls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxPerPlayer = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-player")
        .description("Maximum number of predicted landing spots shown per player. 1 = most recent pearl only.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> liveUpdate = sgGeneral.add(new BoolSetting.Builder()
        .name("live-update")
        .description("Re-simulate the landing spot every tick. Needed for chunk-load updates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> simulationSteps = sgGeneral.add(new IntSetting.Builder()
        .name("simulation-steps")
        .description("Maximum simulation steps per pearl. Higher = more accurate for long-range throws.")
        .defaultValue(1000)
        .min(100)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Boolean> advancedRender = sgGeneral.add(new BoolSetting.Builder()
        .name("advanced-render-settings")
        .description("Show advanced render and color settings.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ListMode> listMode = sgFilter.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("Whitelist: only track players on the list. Blacklist: track everyone except listed players.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<List<String>> playerList = sgFilter.add(new StringListSetting.Builder()
        .name("player-list")
        .description("Player names used for the whitelist or blacklist.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> seeThrough = sgRender.add(new BoolSetting.Builder()
        .name("see-through")
        .description("Render the landing spot box through walls.")
        .defaultValue(true)
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the landing spot box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<Double> boxSize = sgRender.add(new DoubleSetting.Builder()
        .name("box-size")
        .description("Size of the landing spot indicator box.")
        .defaultValue(0.3)
        .min(0.1)
        .sliderMax(1.0)
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<Boolean> showName = sgRender.add(new BoolSetting.Builder()
        .name("show-name")
        .description("Display the pearl owner's name above the landing spot.")
        .defaultValue(true)
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<Double> nameScale = sgRender.add(new DoubleSetting.Builder()
        .name("name-scale")
        .description("Font size of the player name label.")
        .defaultValue(1.0)
        .min(0.3)
        .sliderMax(3.0)
        .visible(() -> advancedRender.get() && showName.get())
        .build()
    );

    private final Setting<Boolean> showEstimatedLabel = sgRender.add(new BoolSetting.Builder()
        .name("show-estimated-label")
        .description("Prefix the name with '~' when the landing spot is estimated because the target chunk is not loaded.")
        .defaultValue(true)
        .visible(() -> advancedRender.get() && showName.get())
        .build()
    );

    private final Setting<SettingColor> sideColor = sgColors.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of the landing spot box.")
        .defaultValue(new SettingColor(255, 50, 50, 60))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgColors.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of the landing spot box.")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> estimatedSideColor = sgColors.add(new ColorSetting.Builder()
        .name("estimated-side-color")
        .description("Fill color when the landing spot is estimated (target chunk not loaded).")
        .defaultValue(new SettingColor(255, 200, 0, 40))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> estimatedLineColor = sgColors.add(new ColorSetting.Builder()
        .name("estimated-line-color")
        .description("Outline color when the landing spot is estimated.")
        .defaultValue(new SettingColor(255, 200, 0, 180))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> unknownSideColor = sgColors.add(new ColorSetting.Builder()
        .name("unknown-side-color")
        .description("Fill color when the pearl owner is unknown.")
        .defaultValue(new SettingColor(128, 128, 128, 40))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> unknownLineColor = sgColors.add(new ColorSetting.Builder()
        .name("unknown-line-color")
        .description("Outline color when the pearl owner is unknown.")
        .defaultValue(new SettingColor(128, 128, 128, 180))
        .visible(() -> advancedRender.get())
        .build()
    );

    private final Setting<SettingColor> nameColor = sgColors.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of the player name label.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> advancedRender.get() && showName.get())
        .build()
    );

    private static class PearlEntry {
        final int entityId;
        String ownerName; 
        UUID ownerUuid;   
        final Vector3d landingPos = new Vector3d();
        boolean isEstimated = false;
        boolean isUnknown = false; 
        long timestamp;

        PearlEntry(int entityId, String ownerName, UUID ownerUuid) {
            this.entityId  = entityId;
            this.ownerName = ownerName;
            this.ownerUuid = ownerUuid;
            this.timestamp = System.currentTimeMillis();
        }
    }

    
    private final Map<UUID, Deque<PearlEntry>> trackedPearls = new ConcurrentHashMap<>();
    private final Deque<PearlEntry> unknownPearls = new ArrayDeque<>(); 
    private final Set<Integer> knownPearlIds = ConcurrentHashMap.newKeySet();
    private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();
    
    
    private final Vector3d reusableScreenPos = new Vector3d();

    public PearlLandingPredictor() {
        super(GlazedAddon.esp, "pearl-landing-predictor",
            "Predicts and displays the landing spot of ender pearls thrown by other players.");
    }

    @Override
    public void onActivate() {
        trackedPearls.clear();
        unknownPearls.clear();
        knownPearlIds.clear();
    }

    @Override
    public void onDeactivate() {
        trackedPearls.clear();
        unknownPearls.clear();
        knownPearlIds.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Set<Integer> activeIds = new HashSet<>();
        Set<UUID> activePlayers = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            if (!showSelf.get() && pearl.getOwner() == mc.player) continue;

            
            UUID ownerUuid = pearl.getOwner() != null ? pearl.getOwner().getUuid() : null;
            String ownerName = pearl.getOwner() != null ? pearl.getOwner().getName().getString() : "Unknown";
            
            
            if (pearl.getOwner() != null && !isAllowed(ownerName)) continue;
            
            if (pearl.getOwner() != null) {
                activePlayers.add(ownerUuid);
            }

            activeIds.add(pearl.getId());

            if (!knownPearlIds.contains(pearl.getId())) {
                knownPearlIds.add(pearl.getId());

                PearlEntry entry = new PearlEntry(pearl.getId(), ownerName, ownerUuid);
                entry.isUnknown = (pearl.getOwner() == null);
                simulateLanding(pearl, entry);

                if (ownerUuid != null) {
                    Deque<PearlEntry> deque = trackedPearls.computeIfAbsent(ownerUuid, k -> new ArrayDeque<>());
                    synchronized (deque) {
                        deque.addFirst(entry);
                        while (deque.size() > maxPerPlayer.get()) deque.removeLast();
                    }
                } else {
                    
                    synchronized (unknownPearls) {
                        unknownPearls.addFirst(entry);
                        while (unknownPearls.size() > maxPerPlayer.get()) unknownPearls.removeLast();
                    }
                }

            } else if (liveUpdate.get()) {
                
                PearlEntry entryToUpdate = null;
                
                if (ownerUuid != null) {
                    Deque<PearlEntry> deque = trackedPearls.get(ownerUuid);
                    if (deque != null) {
                        synchronized (deque) {
                            for (PearlEntry entry : deque) {
                                if (entry.entityId == pearl.getId()) {
                                    entryToUpdate = entry;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    synchronized (unknownPearls) {
                        for (PearlEntry entry : unknownPearls) {
                            if (entry.entityId == pearl.getId()) {
                                entryToUpdate = entry;
                                break;
                            }
                        }
                    }
                }
                
                if (entryToUpdate != null) {
                    
                    if (entryToUpdate.isUnknown && pearl.getOwner() != null) {
                        entryToUpdate.ownerName = ownerName;
                        entryToUpdate.ownerUuid = ownerUuid;
                        entryToUpdate.isUnknown = false;
                    }
                    simulateLanding(pearl, entryToUpdate);
                }
            }
        }

        
        knownPearlIds.removeIf(id -> !activeIds.contains(id));
        
        
        synchronized (unknownPearls) {
            unknownPearls.removeIf(entry -> !activeIds.contains(entry.entityId));
        }
        
        
        
        trackedPearls.keySet().removeIf(uuid -> !activePlayers.contains(uuid));
    }

    private boolean isAllowed(String name) {
        boolean inList = playerList.get().stream().anyMatch(n -> n.equalsIgnoreCase(name));
        return switch (listMode.get()) {
            case Whitelist -> inList;
            case Blacklist -> !inList;
        };
    }

    private void simulateLanding(EnderPearlEntity pearl, PearlEntry entry) {
        if (!simulator.set(pearl, false)) return;

        int maxSteps = simulationSteps.get();
        boolean hitSomething = false;
        boolean encounteredUnloadedChunk = false;

        for (int i = 0; i < maxSteps; i++) {
            int chunkX = ChunkSectionPos.getSectionCoord(simulator.pos.x);
            int chunkZ = ChunkSectionPos.getSectionCoord(simulator.pos.z);

            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                encounteredUnloadedChunk = true;
                entry.landingPos.set(extrapolateInAir(
                    simulator.pos.x, simulator.pos.y, simulator.pos.z,
                    pearl.getVelocity().x, pearl.getVelocity().y, pearl.getVelocity().z
                ));
                entry.isEstimated = true;
                return;
            }

            HitResult result = simulator.tick();

            if (result != null) {
                entry.landingPos.set(simulator.pos);
                entry.isEstimated = false;
                hitSomething = true;
                break;
            }
        }

        if (!hitSomething && !encounteredUnloadedChunk) {
            entry.landingPos.set(simulator.pos);
            entry.isEstimated = true;
        }
    }

    private Vector3d extrapolateInAir(double px, double py, double pz,
                                      double vx, double vy, double vz) {
        final double gravity = 0.03;
        final double airDrag = 0.99;

        for (int i = 0; i < 2000; i++) {
            vy -= gravity;
            vx *= airDrag;
            vy *= airDrag;
            vz *= airDrag;
            px += vx;
            py += vy;
            pz += vz;
            if (py < (mc.world != null ? mc.world.getBottomY() : -64)) break;
        }

        return new Vector3d(px, py, pz);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (trackedPearls.isEmpty() && unknownPearls.isEmpty()) return;

        double half = boxSize.get() / 2.0;

        
        try {
            if (seeThrough.get()) RenderSystem.disableDepthTest();

            
            for (Deque<PearlEntry> deque : trackedPearls.values()) {
                synchronized (deque) {
                    for (PearlEntry entry : deque) { 
                        renderPearlEntry(event.renderer, entry, half);
                    }
                }
            }
            
            
            synchronized (unknownPearls) {
                for (PearlEntry entry : unknownPearls) { 
                    renderPearlEntry(event.renderer, entry, half);
                }
            }
        } finally {
            if (seeThrough.get()) RenderSystem.enableDepthTest();
        }
    }
    
    private void renderPearlEntry(meteordevelopment.meteorclient.renderer.Renderer3D r, PearlEntry entry, double half) {
        Vector3d pos = entry.landingPos;
        SettingColor sc, lc;
        
        if (entry.isUnknown) {
            sc = unknownSideColor.get();
            lc = unknownLineColor.get();
        } else if (entry.isEstimated) {
            sc = estimatedSideColor.get();
            lc = estimatedLineColor.get();
        } else {
            sc = sideColor.get();
            lc = lineColor.get();
        }
        
        
        r.box(pos.x - half, pos.y, pos.z - half,
              pos.x + half, pos.y + boxSize.get(), pos.z + half,
              sc, lc, shapeMode.get(), 0);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showName.get()) return;
        if (mc.player == null || mc.world == null) return;
        if (trackedPearls.isEmpty() && unknownPearls.isEmpty()) return;

        double half = boxSize.get() / 2.0;

        
        for (Deque<PearlEntry> deque : trackedPearls.values()) {
            synchronized (deque) {
                for (PearlEntry entry : deque) {
                    renderPearlName(entry, half);
                }
            }
        }
        
        
        synchronized (unknownPearls) {
            for (PearlEntry entry : unknownPearls) {
                renderPearlName(entry, half);
            }
        }
    }
    
    private void renderPearlName(PearlEntry entry, double half) {
        
        Vector3d pos = entry.landingPos;
        reusableScreenPos.set(pos.x, pos.y + half + 0.15, pos.z);

        if (!NametagUtils.to2D(reusableScreenPos, nameScale.get())) return;

        String label = entry.ownerName;
        if (showEstimatedLabel.get() && entry.isEstimated) label = "~" + label;
        if (entry.isUnknown) label = "?" + label;

        NametagUtils.begin(reusableScreenPos);
        TextRenderer.get().begin(nameScale.get(), false, true);
        double textWidth = TextRenderer.get().getWidth(label);
        TextRenderer.get().render(label, -textWidth / 2.0, 0, nameColor.get(), true);
        TextRenderer.get().end();
        NametagUtils.end();
    }
}