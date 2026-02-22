package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class AntiTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder().name("notifications").description("Show chat feedback.").defaultValue(true).build());

    private final Setting<Boolean> removeExisting = sgGeneral.add(new BoolSetting.Builder()
        .name("remove-existing")
        .description("Remove existing trap entities when enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preventSpawn = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-spawn")
        .description("Prevent new trap entities from spawning.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> armorStands = sgGeneral.add(new BoolSetting.Builder()
        .name("armor-stands")
        .description("Target armor stands.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chestMinecarts = sgGeneral.add(new BoolSetting.Builder()
        .name("chest-minecarts")
        .description("Target chest minecarts.")
        .defaultValue(true)
        .build()
    );

    public AntiTrap() {
        super(GlazedAddon.pvp, "anti-trap", "Allows you to escape from armor stands and chest minecarts.");
    }


    @Override
    public void onActivate() {


        if (removeExisting.get()) {
            removeTrapEntities();
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!preventSpawn.get()) return;

        Entity entity = event.entity;
        if (isTrapEntity(entity)) {
            entity.discard();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        if (mc.player.age % 20 == 0) { // Check every second
            List<Entity> toRemove = new ArrayList<>();

            for (Entity entity : mc.world.getEntities()) {
                if (isTrapEntity(entity)) {
                    toRemove.add(entity);
                }
            }

            for (Entity entity : toRemove) {
                entity.discard();
            }
        }
    }

    private void removeTrapEntities() {
        if (mc.world == null) return;

        List<Entity> trapEntities = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (isTrapEntity(entity)) {
                trapEntities.add(entity);
            }
        }

        for (Entity entity : trapEntities) {
            entity.discard();
        }

        if (!trapEntities.isEmpty()) {
            if (notifications.get()) info("Removed %d trap entities", trapEntities.size());
        }
    }

    private boolean isTrapEntity(Entity entity) {
        if (entity == null) return false;

        EntityType<?> type = entity.getType();

        if (armorStands.get() && type == EntityType.ARMOR_STAND) {
            return true;
        }

        if (chestMinecarts.get() && type == EntityType.CHEST_MINECART) {
            return true;
        }

        return false;
    }
}
