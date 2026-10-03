package com.blackhole.client.render;

import com.blackhole.entity.SuckedBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** Рисует блок, летящий по спирали в чёрную дыру: растягивается к центру, вращается и уменьшается. */
public class SuckedBlockEntityRenderer extends EntityRenderer<SuckedBlockEntity> {
    private final BlockRenderManager blockRenderManager;

    public SuckedBlockEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.blockRenderManager = ctx.getBlockRenderManager();
        this.shadowRadius = 0.0F;
    }

    @Override
    public Identifier getTexture(SuckedBlockEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }

    @Override
    public boolean shouldRender(SuckedBlockEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // сущность стоит на месте, а рисуется в другой точке - отсечение по рамке не годится
    }

    @Override
    public void render(SuckedBlockEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float s = entity.progress(tickDelta);
        if (s >= 1.0F) return;
        BlockState state = entity.getBlockState();
        if (state.getRenderType() != BlockRenderType.MODEL) return;

        Vec3d abs = entity.pathPos(s);
        Vec3d off = abs.subtract(entity.getPos());
        Vec3d toCenter = entity.getCenterPos().subtract(abs);
        double len = toCenter.length();

        float p = (float) Math.pow(s, 2.5);
        float along = 1.0F + 2.6F * p * p;                       // растягивание к дыре ("спагеттификация")
        float across = Math.max(0.15F, 1.0F - 0.75F * p);
        float shrink = 1.0F - 0.55F * p;

        matrices.push();
        matrices.translate(off.x, off.y, off.z);
        if (len > 1.0E-4) {
            matrices.multiply(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                    (float) (toCenter.x / len), (float) (toCenter.y / len), (float) (toCenter.z / len)));
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s * 540.0F + (entity.getId() * 37 % 360)));
        matrices.scale(across * shrink * 0.95F, along * shrink * 0.95F, across * shrink * 0.95F);
        matrices.translate(-0.5, -0.5, -0.5);

        int bl = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), 9);
        int packed = LightmapTextureManager.pack(bl, LightmapTextureManager.getSkyLightCoordinates(light));
        this.blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, packed, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
