package com.blackhole.client.render;

import com.blackhole.BlackHoleConfig;
import com.blackhole.BlackHoleMod;
import com.blackhole.entity.BlackHoleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Рендер чёрной дыры целиком геометрией с цветами в вершинах (без текстур и шейдеров):
 *  1. непрозрачная чёрная сфера (тень) - пишет глубину, поэтому прячет заднюю часть диска;
 *  2. мягкое свечение вокруг (billboard);
 *  3. аккреционный диск - плоское кольцо в плоскости XZ с доплеровской яркостью и вращающимися рукавами;
 *  4. линзированное гало - кольцо, обращённое к камере: фотонное кольцо + "арки" над и под тенью,
 *     то самое изображение задней части диска, загнутое гравитацией;
 *  5. ударная волна при схлопывании.
 * Слои 2-5 рисуются аддитивно (RenderLayer.getEyes), цвета считает {@link BlackHoleShading}.
 */
public class BlackHoleEntityRenderer extends EntityRenderer<BlackHoleEntity> {
    private static final Identifier WHITE = BlackHoleMod.id("textures/entity/white.png");

    private static final int DISC_SEG = 144;
    private static final int DISC_RING = 36;
    private static final int HALO_SEG = 96;
    private static final int HALO_RING = 14;
    private static final int BLOOM_SEG = 48;
    private static final int BLOOM_RING = 10;
    private static final int SHOCK_SEG = 64;
    private static final int SHOCK_RING = 8;
    private static final float TAU = (float) (Math.PI * 2.0);

    private final float[] tmp = new float[3];

    public BlackHoleEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public Identifier getTexture(BlackHoleEntity entity) {
        return WHITE;
    }

    @Override
    public boolean shouldRender(BlackHoleEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // дыра огромная, считаем видимой всегда (дальность отсекает сама сущность)
    }

    @Override
    public void render(BlackHoleEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.getVisualAge(tickDelta);
        float scale = BlackHoleEntity.visualScale(age);
        if (scale < 0.003F) return;

        float flash = BlackHoleEntity.flashAt(age);
        float time = entity.age + tickDelta;
        float rho = BlackHoleConfig.HORIZON_RADIUS * scale;
        float bright = MathHelper.clamp(scale * 1.6F, 0.0F, 1.0F) * (1.0F + 2.0F * flash);

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d toCam = camera.getPos().subtract(entity.getLerpedPos(tickDelta));
        double dist = Math.max(toCam.length(), 1.0E-3);
        float vx = (float) (toCam.x / dist), vy = (float) (toCam.y / dist), vz = (float) (toCam.z / dist);

        Quaternionf q = camera.getRotation();
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(q);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(q);
        float sI = (float) Math.sqrt(Math.max(0.0F, 1.0F - vy * vy));   // 1 = смотрим вдоль плоскости диска, 0 = сверху
        float cR = right.z * vx - right.x * vz;                          // доплер на "правой" стороне экрана

        // экранная плоскость слегка позади центра, чтобы не конфликтовать с глубиной сферы
        float ox = -vx * 0.12F * rho, oy = -vy * 0.12F * rho, oz = -vz * 0.12F * rho;

        // 1. чёрная сфера
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(WHITE));
        drawSphere(solid, entry, rho * 0.985F);

        // 2-5. свечение
        VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEyes(WHITE));
        Matrix4f pm = entry.getPositionMatrix();
        drawBloom(glow, pm, rho, bright, right, up, ox, oy, oz);
        drawDisc(glow, pm, rho, time, vx * (float) dist / rho, vy * (float) dist / rho, vz * (float) dist / rho, bright, vy > 0.0F);
        drawHalo(glow, pm, rho, bright, right, up, ox, oy, oz, sI, cR);
        float shock = BlackHoleEntity.shockAt(age);
        if (shock >= 0.0F) {
            drawShock(glow, pm, shock, right, up);
        }
    }

    // ------------------------------------------------------------------ вспомогательные

    private static int c8(float f) {
        return MathHelper.clamp((int) (f * 255.0F + 0.5F), 0, 255);
    }

    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b) {
        vc.vertex(m, x, y, z)
                .color(c8(r), c8(g), c8(b), 255)
                .texture(0.5F, 0.5F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F);
    }

    /**
     * Сетка (rings+1) x (segs+1) вершин -> квады. Порядок вершин по умолчанию: (a,j) (a+1,j) (a+1,j+1) (a,j+1);
     * flip меняет сторону, в которую смотрит лицевая грань.
     */
    private static void emitGrid(VertexConsumer vc, Matrix4f m, float[] pos, float[] col, int rings, int segs, boolean flip) {
        int stride = segs + 1;
        for (int a = 0; a < rings; a++) {
            for (int j = 0; j < segs; j++) {
                int i00 = (a * stride + j) * 3, i10 = ((a + 1) * stride + j) * 3;
                int i11 = ((a + 1) * stride + j + 1) * 3, i01 = (a * stride + j + 1) * 3;
                int b = flip ? i01 : i10;   // вторая вершина
                int d = flip ? i10 : i01;   // четвёртая вершина
                put(vc, m, pos[i00], pos[i00 + 1], pos[i00 + 2], col[i00], col[i00 + 1], col[i00 + 2]);
                put(vc, m, pos[b], pos[b + 1], pos[b + 2], col[b], col[b + 1], col[b + 2]);
                put(vc, m, pos[i11], pos[i11 + 1], pos[i11 + 2], col[i11], col[i11 + 1], col[i11 + 2]);
                put(vc, m, pos[d], pos[d + 1], pos[d + 2], col[d], col[d + 1], col[d + 2]);
            }
        }
    }

    private void drawSphere(VertexConsumer vc, MatrixStack.Entry entry, float r) {
        Matrix4f m = entry.getPositionMatrix();
        int lat = 18, lon = 28;
        for (int i = 0; i < lat; i++) {
            float t0 = (float) Math.PI * i / lat, t1 = (float) Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = TAU * j / lon, p1 = TAU * (j + 1) / lon;
                float[][] pts = {{t0, p0}, {t0, p1}, {t1, p1}, {t1, p0}};   // нормали наружу
                for (float[] pt : pts) {
                    float nx = (float) (Math.sin(pt[0]) * Math.cos(pt[1]));
                    float ny = (float) Math.cos(pt[0]);
                    float nz = (float) (Math.sin(pt[0]) * Math.sin(pt[1]));
                    vc.vertex(m, nx * r, ny * r, nz * r)
                            .color(0, 0, 0, 255)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                            .normal(entry, nx, ny, nz);
                }
            }
        }
    }

    // ------------------------------------------------------------------ свечение вокруг

    private void drawBloom(VertexConsumer vc, Matrix4f m, float rho, float bright, Vector3f right, Vector3f up,
                           float ox, float oy, float oz) {
        int n = BLOOM_SEG, k = BLOOM_RING;
        float[] pos = new float[(k + 1) * (n + 1) * 3];
        float[] col = new float[pos.length];
        for (int a = 0; a <= k; a++) {
            float t = (float) a / k;
            float s = 6.5F * t * t;
            BlackHoleShading.bloom(s, bright, tmp);
            for (int j = 0; j <= n; j++) {
                float al = TAU * j / n;
                float rad = rho * (1.0F + s);
                float x = rad * (float) Math.cos(al), y = rad * (float) Math.sin(al);
                int i = (a * (n + 1) + j) * 3;
                pos[i] = right.x * x + up.x * y + ox;
                pos[i + 1] = right.y * x + up.y * y + oy;
                pos[i + 2] = right.z * x + up.z * y + oz;
                col[i] = tmp[0]; col[i + 1] = tmp[1]; col[i + 2] = tmp[2];
            }
        }
        emitGrid(vc, m, pos, col, k, n, false);
    }

    // ------------------------------------------------------------------ аккреционный диск

    private void drawDisc(VertexConsumer vc, Matrix4f m, float rho, float time, float camX, float camY, float camZ,
                          float bright, boolean cameraAbove) {
        int n = DISC_SEG, k = DISC_RING;
        float[] pos = new float[(k + 1) * (n + 1) * 3];
        float[] col = new float[pos.length];
        for (int a = 0; a <= k; a++) {
            float t = (float) a / k;
            float rr = BlackHoleShading.DISC_IN + (BlackHoleShading.DISC_OUT - BlackHoleShading.DISC_IN) * (float) Math.pow(t, 1.5);
            for (int j = 0; j <= n; j++) {
                float phi = TAU * j / n;
                int i = (a * (n + 1) + j) * 3;
                pos[i] = rr * rho * (float) Math.cos(phi);
                pos[i + 1] = 0.0F;
                pos[i + 2] = rr * rho * (float) Math.sin(phi);
                BlackHoleShading.disc(rr, phi, time, camX, camY, camZ, bright, tmp);
                col[i] = tmp[0]; col[i + 1] = tmp[1]; col[i + 2] = tmp[2];
            }
        }
        // при порядке по умолчанию лицевая грань смотрит вниз; если камера выше диска - переворачиваем
        emitGrid(vc, m, pos, col, k, n, cameraAbove);
    }

    // ------------------------------------------------------------------ линзированное гало

    private void drawHalo(VertexConsumer vc, Matrix4f m, float rho, float bright, Vector3f right, Vector3f up,
                          float ox, float oy, float oz, float sI, float cR) {
        int n = HALO_SEG, k = HALO_RING;
        float[] pos = new float[(k + 1) * (n + 1) * 3];
        float[] col = new float[pos.length];
        for (int j = 0; j <= n; j++) {
            float al = TAU * j / n;
            float ca = (float) Math.cos(al), sa = (float) Math.sin(al), asa = Math.abs(sa);
            float sMax = BlackHoleShading.haloSMax(sI, asa);
            float tau = BlackHoleShading.haloTau(sI, asa);
            float amp = BlackHoleShading.haloAmp(sI, asa);
            float delta = BlackHoleShading.haloDelta(cR, ca);
            for (int a = 0; a <= k; a++) {
                float t = (float) a / k;
                float s = sMax * t * t;
                float rad = rho * (1.02F + s);
                float x = rad * ca, y = rad * sa;
                int i = (a * (n + 1) + j) * 3;
                pos[i] = right.x * x + up.x * y + ox;
                pos[i + 1] = right.y * x + up.y * y + oy;
                pos[i + 2] = right.z * x + up.z * y + oz;
                BlackHoleShading.halo(s, tau, amp, delta, bright, tmp);
                col[i] = tmp[0]; col[i + 1] = tmp[1]; col[i + 2] = tmp[2];
            }
        }
        emitGrid(vc, m, pos, col, k, n, false);
    }

    // ------------------------------------------------------------------ ударная волна при схлопывании

    private void drawShock(VertexConsumer vc, Matrix4f m, float u, Vector3f right, Vector3f up) {
        int n = SHOCK_SEG, k = SHOCK_RING;
        float radius = BlackHoleConfig.HORIZON_RADIUS * (1.0F + 14.0F * u);
        float width = 0.9F + 2.5F * u;
        float power = (float) Math.pow(1.0F - u, 1.5) * 2.2F;
        float[] pos = new float[(k + 1) * (n + 1) * 3];
        float[] col = new float[pos.length];
        for (int a = 0; a <= k; a++) {
            float t = (float) a / k * 2.0F - 1.0F;
            float prof = (float) Math.exp(-t * t * 3.5F) * power;
            float rad = Math.max(0.01F, radius + t * width);
            for (int j = 0; j <= n; j++) {
                float al = TAU * j / n;
                float x = rad * (float) Math.cos(al), y = rad * (float) Math.sin(al);
                int i = (a * (n + 1) + j) * 3;
                pos[i] = right.x * x + up.x * y;
                pos[i + 1] = right.y * x + up.y * y;
                pos[i + 2] = right.z * x + up.z * y;
                col[i] = Math.min(1.0F, 1.00F * prof);
                col[i + 1] = Math.min(1.0F, 0.80F * prof);
                col[i + 2] = Math.min(1.0F, 0.55F * prof);
            }
        }
        emitGrid(vc, m, pos, col, k, n, false);
    }
}
