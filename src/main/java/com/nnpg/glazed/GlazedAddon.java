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
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import meteordevelopment.meteorclient.systems.modules.Category;




public class GlazedAddon extends MeteorAddon {

public static final Category CATEGORY = new Category("Glazed", new ItemStack(Items.CAKE));
public static final Category esp = new Category("Glazed ESP ", new ItemStack(Items.VINE));
public static final Category pvp = new Category("Glazed PVP", new ItemStack(Items.DIAMOND_SWORD));




    public static int MyScreenVERSION = 14;

    @Override
    public void onInitialize() {



        Modules.get().add(new SpawnerProtect()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new ElytraSwap()); //done
        Modules.get().add(new PlayerDetection()); //done
        Modules.get().add(new AHSniper()); //done
        Modules.get().add(new RTPer()); //done
        Modules.get().add(new ShulkerDropper()); //done
        Modules.get().add(new AutoSell()); //done
        Modules.get().add(new SpawnerDropper()); //done
        Modules.get().add(new AutoShulkerOrder()); // done
        Modules.get().add(new AutoOrder()); //done
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new LegitCrystalMacro());
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
        Modules.get().add(new LegitAnchorMacro());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new RegionMap());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new RegionMap());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new RegionMap());
        Modules.get().add(new AutoShulkerShellOrder());
        Modules.get().add(new NoBlockInteract());
        Modules.get().add(new BeehiveESP());
        Modules.get().add(new WindPearlMacro());
        Modules.get().add(new QuickShieldBreaker());
        Modules.get().add(new UDTriggerBot());
        Modules.get().add(new SwordPlaceObsidian());
        Modules.get().add(new ChestAndShulkerStealer());
        Modules.get().add(new DoubleAnchorMacro());
        Modules.get().add(new StunMace());
        Modules.get().add(new HoldHotbarTotem());
        Modules.get().add(new SweetBerryESP());
        Modules.get().add(new ElytraFirework());
        Modules.get().add(new PistonESP());
        Modules.get().add(new TpaAllMacro());
        Modules.get().add(new RTPNetherBaseFinder());
        Modules.get().add(new HomeReset());
        Modules.get().add(new KeyPearl());
        Modules.get().add(new DrownedTridentESP());
        Modules.get().add(new RTPBaseFinder());
        Modules.get().add(new HoverTotem());
        Modules.get().add(new TapResetMacro());
        Modules.get().add(new JumpResetMacro());
        Modules.get().add(new TunnelBaseFinder());
        Modules.get().add(new AimAssist());
        Modules.get().add(new HumanTriggerBot());
        Modules.get().add(new SkeletonESP());
        Modules.get().add(new RainNoti());
        Modules.get().add(new AutoPearlChain());
        Modules.get().add(new AutoBlazeRodOrder());
        Modules.get().add(new SwordAxePlaceCobweb());
        Modules.get().add(new BlazeRodDropper());
        Modules.get().add(new BreachSwap());
        Modules.get().add(new FakeScoreboard());
        Modules.get().add(new AutoInvTotem());
        Modules.get().add(new FreecamMining());
        Modules.get().add(new BedrockVoidESP());
        Modules.get().add(new UIHelper());


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
