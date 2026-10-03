package com.blackhole.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Чисто визуальный "летящий в дыру блок".
 * Сущность не двигается: траектория (спираль к центру дыры) вычисляется аналитически
 * из начальной позиции, центра и времени, поэтому по сети почти ничего не передаётся.
 */
public class SuckedBlockEntity extends Entity {
    private static final TrackedData<Integer> STATE_ID = DataTracker.registerData(SuckedBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> CENTER_X = DataTracker.registerData(SuckedBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> CENTER_Y = DataTracker.registerData(SuckedBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> CENTER_Z = DataTracker.registerData(SuckedBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> DURATION = DataTracker.registerData(SuckedBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public SuckedBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    /** Вызывать на сервере до spawnEntity. */
    public void init(BlockPos pos, BlockState state, Vec3d center) {
        this.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        this.dataTracker.set(STATE_ID, Block.getRawIdFromState(state));
        this.dataTracker.set(CENTER_X, (float) center.x);
        this.dataTracker.set(CENTER_Y, (float) center.y);
        this.dataTracker.set(CENTER_Z, (float) center.z);
        double dist = this.getPos().distanceTo(center);
        this.dataTracker.set(DURATION, MathHelper.clamp(10 + (int) (dist * 1.1), 12, 32));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE_ID, Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
        builder.add(CENTER_X, 0.0F);
        builder.add(CENTER_Y, 0.0F);
        builder.add(CENTER_Z, 0.0F);
        builder.add(DURATION, 20);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.getDuration() + 4) {
            this.discard();
        }
    }

    public BlockState getBlockState() {
        return Block.getStateFromRawId(this.dataTracker.get(STATE_ID));
    }

    public Vec3d getCenterPos() {
        return new Vec3d(this.dataTracker.get(CENTER_X), this.dataTracker.get(CENTER_Y), this.dataTracker.get(CENTER_Z));
    }

    public int getDuration() {
        return Math.max(1, this.dataTracker.get(DURATION));
    }

    /** Прогресс полёта 0..1. */
    public float progress(float tickDelta) {
        return MathHelper.clamp((this.age + tickDelta) / (float) this.getDuration(), 0.0F, 1.0F);
    }

    /** Мировая позиция на спирали при прогрессе s (0..1). */
    public Vec3d pathPos(float s) {
        Vec3d c = this.getCenterPos();
        Vec3d st = this.getPos();
        double dx = st.x - c.x, dy = st.y - c.y, dz = st.z - c.z;
        double r0 = Math.sqrt(dx * dx + dz * dz);
        double a0 = Math.atan2(dz, dx);
        double p = Math.pow(s, 1.7);               // ускорение к центру
        double r = r0 * (1.0 - p);
        double ang = a0 + 3.4 * Math.pow(s, 1.3);  // закрутка
        return new Vec3d(c.x + r * Math.cos(ang), c.y + dy * (1.0 - p), c.z + r * Math.sin(ang));
    }
}
