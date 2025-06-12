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
        // Register modules
        Modules.get().add(new SpawnerProtect());
        Modules.get().add(new PearlThrow()); //done
        Modules.get().add(new AutoSpawnerDrop());
        Modules.get().add(new AutoInvTotem()); //done
        Modules.get().add(new RTPBaseFinder());
        Modules.get().add(new webhook()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new AutoFirework()); //done
        Modules.get().add(new ElytraSwap()); //done









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
