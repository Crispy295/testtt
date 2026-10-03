package com.blackhole.item;

import com.blackhole.BlackHoleConfig;
import com.blackhole.entity.BlackHoleProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class BlackHoleItem extends Item {
    public BlackHoleItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.PLAYERS, 0.8F, 0.5F);

        if (!world.isClient) {
            BlackHoleProjectileEntity projectile = new BlackHoleProjectileEntity(world, user);
            projectile.setItem(stack);
            projectile.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, BlackHoleConfig.THROW_SPEED, 0.4F);
            world.spawnEntity(projectile);
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        user.getItemCooldownManager().set(this, BlackHoleConfig.COOLDOWN_TICKS);
        if (BlackHoleConfig.CONSUME_ITEM) {
            stack.decrementUnlessCreative(1, user);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.blackhole.black_hole.tooltip1").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.blackhole.black_hole.tooltip2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.blackhole.black_hole.tooltip3").formatted(Formatting.DARK_GRAY));
    }
}
