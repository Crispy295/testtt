package com.blackhole;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class BlackHoleMod implements ModInitializer {
    public static final String MOD_ID = "blackhole";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModEntities.register();
        ModItems.register();
    }
}
