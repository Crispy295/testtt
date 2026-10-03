package com.blackhole.client;

import com.blackhole.ModEntities;
import com.blackhole.client.render.BlackHoleEntityRenderer;
import com.blackhole.client.render.SuckedBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class BlackHoleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.BLACK_HOLE, BlackHoleEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SUCKED_BLOCK, SuckedBlockEntityRenderer::new);
        // Летящий предмет рисуется как его иконка (всегда яркий, увеличенный в 2 раза)
        EntityRendererRegistry.register(ModEntities.BLACK_HOLE_PROJECTILE,
                ctx -> new FlyingItemEntityRenderer<>(ctx, 2.0F, true));
    }
}
