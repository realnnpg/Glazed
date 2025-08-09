package com.nnpg.glazed;

import com.nnpg.glazed.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.nnpg.glazed.modules.SpawnerProtect;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.MeteorClient;


public class GlazedAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Glazed");
    public static final Category esp = new Category("Glazed-ESPs");

    public static int VERSION = 10;

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
        Modules.get().add(new RTPer()); //not working idk why wrong math ig
        Modules.get().add(new TunnelBaseFinder()); //done
        Modules.get().add(new ShulkerDropper()); //done
        Modules.get().add(new AutoSell()); //done
        Modules.get().add(new SpawnerDropper()); //done
        Modules.get().add(new AutoShulkerOrder()); // done
        Modules.get().add(new AutoOrder()); //working on it
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new AutoInvTotem());
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
        Modules.get().add(new FastBreak());
        Modules.get().add(new PillagerESP());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new CoveredHole());
        Modules.get().add(new ClusterFinder());


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

        //mc.setScreen(new MyScreen(GuiThemes.get()));
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }
}
