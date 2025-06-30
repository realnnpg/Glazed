/**
 *
 * to add:
 * snipe cheap items on /ah--for example: /ah elytra and if under 10k buy it
 *
 */
package com.nnpg.glazed;

import com.nnpg.glazed.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.nnpg.glazed.modules.SpawnerProtect;
import meteordevelopment.meteorclient.systems.modules.Category;



public class GlazedAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Glazed");

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
        Modules.get().add(new RTPTunnelMiner()); //done
        Modules.get().add(new ShulkerDropper()); //have to test
        Modules.get().add(new AutoSell()); //working on it













    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }
}
