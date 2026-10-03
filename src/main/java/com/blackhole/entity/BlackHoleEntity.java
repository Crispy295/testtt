package com.blackhole.entity;

import com.blackhole.BlackHoleConfig;
import com.blackhole.ModDamageTypes;
import com.blackhole.ModEntities;
import com.blackhole.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Чёрная дыра. Логика работает на сервере:
 *  - блоки в радиусе {@link BlackHoleConfig#BLOCK_RADIUS} съедаются изнутри наружу (радиус растёт со временем);
 *  - мобы в радиусе {@link BlackHoleConfig#MOB_RADIUS} затягиваются по спирали, у горизонта событий получают смертельный урон.
 * Клиент только рисует (см. BlackHoleEntityRenderer) и спавнит частицы.
 */
public class BlackHoleEntity extends Entity {
    private static final TrackedData<Integer> LIFE_AGE =
            DataTracker.registerData(BlackHoleEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Смещения блоков сферы радиуса BLOCK_RADIUS, отсортированные от центра: [dist², dx, dy, dz, ...]. */
    private static final int[] OFFSETS = buildOffsets();

    @Nullable
    private UUID ownerUuid;
    private int cursor = 0;
    private int clientAge = 0;

    public BlackHoleEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    // ------------------------------------------------------------------ состояние

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(LIFE_AGE, 0);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerUuid = owner == null ? null : owner.getUuid();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Возраст в тиках для визуализации (с долей тика). */
    public float getVisualAge(float tickDelta) {
        return this.clientAge + tickDelta;
    }

    // ------------------------------------------------------------------ кривые анимации (общие для сервера и клиента)

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Масштаб дыры 0..1: плавно раскрывается, в конце схлопывается. */
    public static float visualScale(float age) {
        float grow = smooth(age / BlackHoleConfig.GROW_TICKS);
        float c = MathHelper.clamp((BlackHoleConfig.LIFETIME_TICKS - age) / BlackHoleConfig.COLLAPSE_TICKS, 0.0F, 1.0F);
        return grow * c * c;
    }

    /** Сила притяжения 0..1. */
    public static float strengthAt(float age) {
        float grow = smooth(age / BlackHoleConfig.GROW_TICKS);
        float c = MathHelper.clamp((BlackHoleConfig.LIFETIME_TICKS - age) / BlackHoleConfig.COLLAPSE_TICKS, 0.0F, 1.0F);
        return grow * c;
    }

    /** Вспышка 0..1, нарастает во время схлопывания. */
    public static float flashAt(float age) {
        float c = MathHelper.clamp((age - (BlackHoleConfig.LIFETIME_TICKS - BlackHoleConfig.COLLAPSE_TICKS))
                / BlackHoleConfig.COLLAPSE_TICKS, 0.0F, 1.0F);
        return c * c;
    }

    /** Прогресс ударной волны 0..1 в последние тики жизни, иначе -1. */
    public static float shockAt(float age) {
        float start = BlackHoleConfig.LIFETIME_TICKS - 16;
        if (age < start) return -1.0F;
        return MathHelper.clamp((age - start) / 16.0F, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            this.serverTick(serverWorld);
        } else {
            this.clientTick();
        }
    }

    private void clientTick() {
        int synced = this.dataTracker.get(LIFE_AGE);
        if (Math.abs(synced - this.clientAge) > 2) {
            this.clientAge = synced;
        }
        this.clientAge++;

        float scale = visualScale(this.clientAge);
        if (scale > 0.15F) {
            // Частицы, "падающие" в дыру: PORTAL стартует в точке спавна + вектор скорости и летит обратно в точку спавна.
            for (int i = 0; i < 6; i++) {
                double dist = 3.0 + this.random.nextDouble() * 18.0;
                double theta = this.random.nextDouble() * Math.PI * 2.0;
                double dy = (this.random.nextDouble() - 0.5) * 0.9;
                double len = Math.sqrt(1.0 - dy * dy);
                this.getWorld().addParticle(ParticleTypes.PORTAL,
                        this.getX(), this.getY(), this.getZ(),
                        Math.cos(theta) * len * dist, dy * dist * 0.6, Math.sin(theta) * len * dist);
            }
        }
    }

    private void serverTick(ServerWorld world) {
        int age = this.dataTracker.get(LIFE_AGE);

        if (age == 0) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SPAWN, SoundCategory.AMBIENT, 6.0F, 1.0F);
        }
        if (age % 60 == 5) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HUM, SoundCategory.AMBIENT, 5.0F, 1.0F);
        }
        if (age == BlackHoleConfig.LIFETIME_TICKS - BlackHoleConfig.COLLAPSE_TICKS) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.COLLAPSE, SoundCategory.AMBIENT, 6.0F, 1.0F);
        }
        if (age >= BlackHoleConfig.LIFETIME_TICKS) {
            this.discard();
            return;
        }

        float strength = strengthAt(age);
        this.pullEntities(world, age, strength);
        if (age < BlackHoleConfig.LIFETIME_TICKS - BlackHoleConfig.COLLAPSE_TICKS) {
            this.eatBlocks(world, age);
        }
        this.dataTracker.set(LIFE_AGE, age + 1);
    }

    // ------------------------------------------------------------------ мобы

    private boolean isSuckable(Entity e) {
        if (e == this || e.isRemoved()) return false;
        if (e instanceof SuckedBlockEntity || e instanceof BlackHoleEntity || e instanceof BlackHoleProjectileEntity) return false;
        if (BlackHoleConfig.OWNER_IMMUNE && this.ownerUuid != null && e.getUuid().equals(this.ownerUuid)) return false;
        if (e instanceof PlayerEntity player) {
            return !player.isCreative() && !player.isSpectator();
        }
        if (e instanceof LivingEntity) return true;
        if (e instanceof FallingBlockEntity) return true;
        return BlackHoleConfig.SWALLOW_ITEMS && (e instanceof ItemEntity || e instanceof ExperienceOrbEntity);
    }

    private void pullEntities(ServerWorld world, int age, float strength) {
        Vec3d c = this.getPos();
        double r = BlackHoleConfig.MOB_RADIUS;
        Box box = new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        double horizon = BlackHoleConfig.HORIZON_RADIUS * Math.max(visualScale(age), 0.35F) + 0.7;

        for (Entity e : world.getOtherEntities(this, box, this::isSuckable)) {
            Vec3d mid = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
            Vec3d to = c.subtract(mid);
            double dist = to.length();
            if (dist > r) continue;

            if (e instanceof FallingBlockEntity) {
                if (dist <= BlackHoleConfig.BLOCK_RADIUS + 3.0) e.discard();
                continue;
            }
            if (dist < horizon) {
                this.swallow(world, e);
                continue;
            }

            Vec3d dir = to.multiply(1.0 / dist);
            double f = 1.0 - dist / r;
            double accel = strength * (0.04 + 0.34 * f * f);
            Vec3d tangent = new Vec3d(-dir.z, 0.0, dir.x);
            Vec3d v = e.getVelocity().multiply(0.86).add(dir.multiply(accel)).add(tangent.multiply(accel * 0.45));
            if (e.isOnGround()) {
                v = v.add(0.0, 0.05 + 0.1 * strength, 0.0);
            }
            double speed = v.length();
            if (speed > 2.2) v = v.multiply(2.2 / speed);
            e.setVelocity(v);
            e.velocityModified = true;
            e.fallDistance = 0.0F;
        }
    }

    private void swallow(ServerWorld world, Entity e) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getBodyY(0.5), e.getZ(), 12, 0.25, 0.25, 0.25, 0.15);

        if (e instanceof LivingEntity living) {
            Entity owner = this.ownerUuid == null ? null : world.getEntity(this.ownerUuid);
            DamageSource source = ModDamageTypes.blackHole(world, this, owner);
            living.damage(source, Float.MAX_VALUE);
            // Страховка для существ, которые не умирают от обычного урона (Дракон Края, Иссушитель в щите и т.п.)
            if (living.isAlive()) {
                living.kill();
            }
            if (!(living instanceof PlayerEntity) && !living.isRemoved() && living.isAlive()) {
                living.discard();
            }
        } else {
            e.discard();
        }
    }

    // ------------------------------------------------------------------ блоки

    private static int[] buildOffsets() {
        int range = (int) Math.ceil(BlackHoleConfig.BLOCK_RADIUS);
        int r2 = (int) Math.floor(BlackHoleConfig.BLOCK_RADIUS * BlackHoleConfig.BLOCK_RADIUS);
        List<int[]> list = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2) list.add(new int[]{d2, dx, dy, dz});
                }
            }
        }
        list.sort(Comparator.comparingInt(a -> a[0]));
        int[] out = new int[list.size() * 4];
        for (int i = 0; i < list.size(); i++) {
            System.arraycopy(list.get(i), 0, out, i * 4, 4);
        }
        return out;
    }

    private void eatBlocks(ServerWorld world, int age) {
        float t = MathHelper.clamp((age - 3.0F) / BlackHoleConfig.EAT_TICKS, 0.0F, 1.0F);
        double radius = Math.max(2.5, BlackHoleConfig.BLOCK_RADIUS * Math.pow(t, 0.85));
        double r2 = radius * radius;

        BlockPos origin = BlockPos.ofFloored(this.getPos());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int total = OFFSETS.length / 4;
        int checks = 0, removed = 0, debris = 0;
        Chunk chunk = null;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

        while (this.cursor < total
                && checks < BlackHoleConfig.MAX_BLOCK_CHECKS_PER_TICK
                && removed < BlackHoleConfig.MAX_BLOCK_REMOVALS_PER_TICK) {
            int i = this.cursor * 4;
            if (OFFSETS[i] > r2) break;
            this.cursor++;
            checks++;

            pos.set(origin.getX() + OFFSETS[i + 1], origin.getY() + OFFSETS[i + 2], origin.getZ() + OFFSETS[i + 3]);
            if (world.isOutOfHeightLimit(pos)) continue;

            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (chunk == null || cx != chunkX || cz != chunkZ) {
                chunkX = cx;
                chunkZ = cz;
                chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false); // не подгружаем новые чанки
            }
            if (chunk == null) continue;

            BlockState state = chunk.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof FluidBlock) continue;
            if (state.getHardness(world, pos) < 0.0F) continue; // бедрок, барьеры, портальные рамки и т.д.

            BlockPos target = pos.toImmutable();
            boolean makeDebris = debris < BlackHoleConfig.MAX_DEBRIS_PER_TICK
                    && state.getRenderType() == BlockRenderType.MODEL
                    && world.random.nextInt(2) == 0;

            world.setBlockState(target, state.getFluidState().getBlockState(), Block.NOTIFY_ALL);
            removed++;

            if (makeDebris) {
                debris++;
                SuckedBlockEntity piece = new SuckedBlockEntity(ModEntities.SUCKED_BLOCK, world);
                piece.init(target, state, this.getPos());
                world.spawnEntity(piece);
            }
        }
    }
}
