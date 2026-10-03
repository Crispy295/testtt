package com.blackhole.entity;

import com.blackhole.BlackHoleConfig;
import com.blackhole.ModEntities;
import com.blackhole.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Летящий предмет. При ударе о блок или сущность превращается в чёрную дыру. */
public class BlackHoleProjectileEntity extends ThrownItemEntity {

    public BlackHoleProjectileEntity(EntityType<? extends BlackHoleProjectileEntity> type, World world) {
        super(type, world);
    }

    public BlackHoleProjectileEntity(World world, LivingEntity owner) {
        super(ModEntities.BLACK_HOLE_PROJECTILE, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BLACK_HOLE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            for (int i = 0; i < 3; i++) {
                this.getWorld().addParticle(ParticleTypes.REVERSE_PORTAL,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.4,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                        (this.random.nextDouble() - 0.5) * 0.1,
                        (this.random.nextDouble() - 0.5) * 0.1,
                        (this.random.nextDouble() - 0.5) * 0.1);
            }
        } else if (this.age > 200) {
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) {
            return;
        }
        Vec3d center = hit.getPos();
        if (hit instanceof BlockHitResult blockHit) {
            // Отодвигаем центр от поверхности, чтобы дыра "висела" над землёй / рядом со стеной.
            center = center.add(Vec3d.of(blockHit.getSide().getVector()).multiply(BlackHoleConfig.HORIZON_RADIUS * 0.9));
        } else {
            center = center.add(0.0, 0.6, 0.0);
        }

        BlackHoleEntity hole = new BlackHoleEntity(ModEntities.BLACK_HOLE, this.getWorld());
        hole.setPosition(center);
        hole.setOwner(this.getOwner());
        this.getWorld().spawnEntity(hole);
        this.discard();
    }
}
