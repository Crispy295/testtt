package com.blackhole;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent HUM = create("hum");
    public static final SoundEvent COLLAPSE = create("collapse");
    public static final SoundEvent SPAWN = create("spawn");

    private static SoundEvent create(String name) {
        Identifier id = BlackHoleMod.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void register() {
        // Вызов нужен, чтобы класс загрузился и звуки зарегистрировались.
    }
}
