package com.nnpg.glazed;

import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.main.*;
import com.nnpg.glazed.modules.pvp.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class GlazedAddon extends MeteorAddon {

public static final Category CATEGORY = new Category("Glazed", new ItemStack(Items.CAKE));
public static final Category esp = new Category("Glazed ESP ", new ItemStack(Items.VINE));
public static final Category pvp = new Category("Glazed PVP", new ItemStack(Items.DIAMOND_SWORD));
    private static boolean reconnectEnableModules = false;
    private static final double ORIGIN_RADIUS = 10.0;
    private static final double RETURN_RADIUS = 3.0;
    private static final double PLAYER_DETECT_RANGE = 10.0;
    private Vec3d lastNonOriginPosition = null;
    private Vec3d pendingReturnPosition = null;
    private boolean waitingForReturn = false;

    public static int MyScreenVERSION = 15;

    @Override
    public void onInitialize() {



        Modules.get().add(new SpawnerProtect()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new PlayerDetection()); //done
        Modules.get().add(new AHSniper()); //done
        Modules.get().add(new RTPer()); //done
        Modules.get().add(new ShulkerDropper()); //done
        Modules.get().add(new AutoSell()); //done
        Modules.get().add(new SpawnerDropper()); //done
        Modules.get().add(new AutoShulkerOrder()); // done
        Modules.get().add(new AutoOrder()); //done
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new CrystalMacro());
        Modules.get().add(new AHSell());
        Modules.get().add(new AnchorMacro());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new KelpESP());
        Modules.get().add(new DripstoneESP());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new CrateBuyer());
        Modules.get().add(new WanderingESP());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new AdvancedStashFinder());
        Modules.get().add(new TpaMacro());
        Modules.get().add(new TabDetector());
        Modules.get().add(new OrderSniper());
        Modules.get().add(new LamaESP());
        Modules.get().add(new PillagerESP());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new CoveredHole());
        Modules.get().add(new ClusterFinder());
        Modules.get().add(new AutoShulkerShellOrder());
        Modules.get().add(new EmergencySeller());
        Modules.get().add(new RTPEndBaseFinder());
        Modules.get().add(new ShopBuyer());
        Modules.get().add(new OrderDropper());
        Modules.get().add(new CollectibleESP());
        Modules.get().add(new SpawnerNotifier());
        Modules.get().add(new VineESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new BlockNotifier());
        Modules.get().add(new AnchorMacro());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new RegionMap());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new AutoShulkerShellOrder());
        Modules.get().add(new NoBlockInteract());
        Modules.get().add(new BeehiveESP());
        Modules.get().add(new WindPearlMacro());
        Modules.get().add(new SwordPlaceObsidian());
        Modules.get().add(new ChestAndShulkerStealer());
        Modules.get().add(new DoubleAnchorMacro());
        Modules.get().add(new AutoDoubleHand());
        Modules.get().add(new SweetBerryESP());
        Modules.get().add(new PistonESP());
        Modules.get().add(new TpaAllMacro());
        Modules.get().add(new RTPNetherBaseFinder());
        Modules.get().add(new HomeReset());
        Modules.get().add(new KeyPearl());
        Modules.get().add(new DrownedTridentESP());
        Modules.get().add(new RTPBaseFinder());
        Modules.get().add(new HoverTotem());
        Modules.get().add(new TunnelBaseFinder());
        Modules.get().add(new AimAssist());
        Modules.get().add(new SkeletonESP());
        Modules.get().add(new RainNoti());
        Modules.get().add(new AutoPearlChain());
        Modules.get().add(new AutoBlazeRodOrder());
        Modules.get().add(new BlazeRodDropper());
        Modules.get().add(new BreachSwap());
        Modules.get().add(new FakeScoreboard());
        Modules.get().add(new AutoInvTotem());
        Modules.get().add(new FreecamMining());
        Modules.get().add(new BedrockVoidESP());
        Modules.get().add(new UIHelper());
        Modules.get().add(new ShieldBreaker());
        Modules.get().add(new InvisESP());
        Modules.get().add(new AutoTotemOrder());
        Modules.get().add(new LightESP());
        Modules.get().add(new FreecamV2());

        ensureAutoReconnectEnabled();
        applyFocusFix();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
        ensureAutoReconnectEnabled();
        applyFocusFix();
        if (reconnectEnableModules) {
            reconnectEnableModules = false;
            enableModuleOnReconnect(SpawnerProtect.class);
            enableModuleOnReconnect(SpawnerOrder.class);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
        reconnectEnableModules = shouldAutoEnableAfterDisconnect();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        applyFocusFix();
        if (mc.player == null || mc.world == null) return;

        Vec3d position = mc.player.getPos();
        boolean atOrigin = isNearOrigin(position);

        if (waitingForReturn) {
            if (!atOrigin && pendingReturnPosition != null &&
                position.squaredDistanceTo(pendingReturnPosition) <= RETURN_RADIUS * RETURN_RADIUS) {
                enableSpawnerOrderIfSafe(mc);
                waitingForReturn = false;
                pendingReturnPosition = null;
            }
            return;
        }

        if (atOrigin) {
            if (lastNonOriginPosition != null) {
                pendingReturnPosition = lastNonOriginPosition;
                waitingForReturn = true;
            }
            return;
        }

        lastNonOriginPosition = position;
    }

    private boolean shouldAutoEnableAfterDisconnect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof DisconnectedScreen screen)) return false;

        String reason = extractDisconnectReason(screen).toLowerCase();
        return reason.contains("java.net.socketexception") || reason.contains("connection reset");
    }

    private String extractDisconnectReason(DisconnectedScreen screen) {
        try {
            var field = DisconnectedScreen.class.getDeclaredField("reason");
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof net.minecraft.text.Text text) {
                return text.getString();
            }
        } catch (Exception ignored) {
        }
        return screen.getTitle().getString();
    }

    private <T extends meteordevelopment.meteorclient.systems.modules.Module> void enableModuleOnReconnect(Class<T> moduleClass) {
        var module = Modules.get().get(moduleClass);
        if (module != null && !module.isActive()) {
            module.toggle();
        }
    }

    private boolean isNearOrigin(Vec3d position) {
        return Math.abs(position.x) <= ORIGIN_RADIUS &&
            Math.abs(position.z) <= ORIGIN_RADIUS;
    }

    private void enableSpawnerOrderIfSafe(MinecraftClient mc) {
        if (hasNearbyPlayer(mc)) {
            return;
        }
        var module = Modules.get().get(com.nnpg.glazed.modules.main.SpawnerOrder.class);
        if (module != null && !module.isActive()) {
            module.toggle();
        }
    }

    private boolean hasNearbyPlayer(MinecraftClient mc) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) <= PLAYER_DETECT_RANGE) {
                return true;
            }
        }
        return false;
    }

    private void ensureAutoReconnectEnabled() {
        var autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && !autoReconnect.isActive()) {
            autoReconnect.toggle();
        }
    }

    private void applyFocusFix() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && mc.options.pauseOnLostFocus) {
            mc.options.pauseOnLostFocus = false;
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(esp);
        Modules.registerCategory(pvp);


        //mc.setScreen(new MyScreen(GuiThemes.get()));
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }


}
