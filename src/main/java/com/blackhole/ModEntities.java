package com.blackhole;

import com.blackhole.entity.BlackHoleEntity;
import com.blackhole.entity.BlackHoleProjectileEntity;
import com.blackhole.entity.SuckedBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<BlackHoleProjectileEntity> BLACK_HOLE_PROJECTILE = register("black_hole_projectile",
            EntityType.Builder.<BlackHoleProjectileEntity>create(BlackHoleProjectileEntity::new, SpawnGroup.MISC)
                    .dimensions(0.4F, 0.4F)
                    .maxTrackingRange(8)
                    .trackingTickInterval(5));

    public static final EntityType<BlackHoleEntity> BLACK_HOLE = register("black_hole",
            EntityType.Builder.<BlackHoleEntity>create(BlackHoleEntity::new, SpawnGroup.MISC)
                    .dimensions(3.0F, 3.0F)
                    .maxTrackingRange(16)
                    .trackingTickInterval(20)
                    .disableSaving());

    public static final EntityType<SuckedBlockEntity> SUCKED_BLOCK = register("sucked_block",
            EntityType.Builder.<SuckedBlockEntity>create(SuckedBlockEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5F, 0.5F)
                    .maxTrackingRange(16)
                    .trackingTickInterval(20)
                    .disableSaving()
                    .disableSummon());

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = BlackHoleMod.id(name);
        return Registry.register(Registries.ENTITY_TYPE, id, builder.build(id.toString()));
    }

    public static void register() {
        // Вызов нужен, чтобы класс загрузился и типы сущностей зарегистрировались.
    }
}
