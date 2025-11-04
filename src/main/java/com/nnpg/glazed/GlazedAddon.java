package com.nnpg.glazed;

import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.main.*;
import com.nnpg.glazed.modules.pvp.*;
import com.nnpg.glazed.modules.troll.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import meteordevelopment.meteorclient.systems.modules.Category;




public class GlazedAddon extends MeteorAddon {

public static final Category CATEGORY = new Category("MAIN", new ItemStack(Items.GRASS_BLOCK));
public static final Category esp = new Category("TROJAN ESP ", new ItemStack(Items.AMETHYST_BLOCK));
public static final Category pvp = new Category("TROJAN COMBAT", new ItemStack(Items.NETHERITE_SWORD));
public static final Category pvp = new Category("TROLL", new ItemStack(Items.TNT_BLOCK));




    public static int MyScreenVERSION = 14;

    @Override
    public void onInitialize() {



        Modules.get().add(new SpawnerProtect()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new ElytraSwap()); //done
        Modules.get().add(new AHSniper()); //done
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new LegitCrystalMacro());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new KelpESP());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new TpaMacro());
        Modules.get().add(new PillagerESP());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new CoveredHole());
        Modules.get().add(new VineESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new LegitAnchorMacro());
        Modules.get().add(new RegionMap());
        Modules.get().add(new QuickShieldBreaker());
        Modules.get().add(new DoubleAnchorMacro());
        Modules.get().add(new StunMace());
        Modules.get().add(new KeyPearl());
        Modules.get().add(new DrownedTridentESP());
        Modules.get().add(new RTPBaseFinder());
        Modules.get().add(new HoverTotem());
        Modules.get().add(new TunnelBaseFinder());
        Modules.get().add(new SkeletonESP());
        Modules.get().add(new RainNoti());
        Modules.get().add(new AutoPearlChain());
        Modules.get().add(new BreachSwap());
        Modules.get().add(new FakeScoreboard());
        Modules.get().add(new FreecamMining());


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
        Modules.registerCategory(troll);


        //mc.setScreen(new MyScreen(GuiThemes.get()));
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }


}
