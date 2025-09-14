package com.nnpg.glazed;

import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.main.*;
import com.nnpg.glazed.modules.pvp.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.MeteorClient;





public class GlazedAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Glazed");
    public static final Category esp = new Category("Glazed-ESPs");
    public static final Category pvp = new Category("Glazed-PVP");



    public static int MyScreenVERSION = 13;

    @Override
    public void onInitialize() {



        Modules.get().add(new SpawnerProtect()); //done
        Modules.get().add(new PearlThrow()); //done
        Modules.get().add(new RTPBaseFinder()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new AutoFirework()); //done
        Modules.get().add(new ElytraSwap()); //done
        Modules.get().add(new PlayerDetection()); //done
        Modules.get().add(new AHSniper()); //done
        Modules.get().add(new RTPer()); //done
        Modules.get().add(new TunnelBaseFinder()); //done
        Modules.get().add(new ShulkerDropper()); //done
        Modules.get().add(new AutoSell()); //done
        Modules.get().add(new SpawnerDropper()); //done
        Modules.get().add(new AutoShulkerOrder()); // done
        Modules.get().add(new AutoOrder()); //done
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new AutoInvTotem());
        Modules.get().add(new LegitCrystalMacro());
        Modules.get().add(new CrystalMacro());
        Modules.get().add(new AHSell());
        Modules.get().add(new AnchorMacro());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new KelpESP());
        Modules.get().add(new DeepslateESP());
        Modules.get().add(new DripstoneESP());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new CrateBuyer());
        Modules.get().add(new WanderingESP());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new AdvancedStashFinder());
        Modules.get().add(new TpaMacro());
        Modules.get().add(new TabDetector());
        Modules.get().add(new AutoSex());
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
        Modules.get().add(new UndetectedTunneler());
        Modules.get().add(new OrderDropper());
        Modules.get().add(new ElytraAutoFly());
        Modules.get().add(new CollectibleESP());
        Modules.get().add(new SpawnerNotifier());
        Modules.get().add(new VineESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new BlockNotifier());
        Modules.get().add(new LegitAnchorMacro());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new RegionMap());
        Modules.get().add(new HoverTotem());
        Modules.get().add(new Itemswap());
        Modules.get().add(new AutoTrident());
        Modules.get().add(new AutoTreeFarmer());



        // Register this class for events
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
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
