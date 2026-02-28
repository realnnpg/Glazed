package com.nnpg.glazed.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MeteorMixinCanceller implements MixinCanceller {

    private static final Logger LOGGER = LoggerFactory.getLogger("Glazed");
    private static final String METEOR_MIXIN = "meteordevelopment.meteorclient.mixin.AbstractSignEditScreenMixin";
    private static final boolean METEOR_PRESENT = FabricLoader.getInstance().isModLoaded("meteor-client");

    static {
        if (METEOR_PRESENT) {
            LOGGER.info("[Glazed] Meteor Fix active - disabling Meteor's broken AbstractSignEditScreenMixin");
        }
    }

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (METEOR_PRESENT && METEOR_MIXIN.equals(mixinClassName)) {
            return true;
        }
        return false;
    }
}