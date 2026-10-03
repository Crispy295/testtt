package com.blackhole;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final RegistryKey<DamageType> BLACK_HOLE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, BlackHoleMod.id("black_hole"));

    public static DamageSource blackHole(World world, Entity source, @Nullable Entity attacker) {
        return new DamageSource(
                world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLACK_HOLE),
                source, attacker);
    }
}
