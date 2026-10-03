package com.blackhole.client.render;

/**
 * Чистая математика цвета чёрной дыры (без зависимостей от Minecraft, поэтому её можно запускать и тестировать отдельно).
 * Все расстояния - в единицах радиуса тени (rho). Возвращаемый цвет - RGB 0..1 для аддитивного смешивания.
 *
 * Физические идеи (упрощённые): доплеровское усиление яркости (g^n) и сдвиг цвета, гравитационное красное смещение,
 * температура диска падает с радиусом, диск вращается быстрее у центра (кеплеровское вращение).
 */
public final class BlackHoleShading {
    private BlackHoleShading() {}

    public static final float DISC_IN = 1.32F;
    public static final float DISC_OUT = 5.0F;

    private static final float[][] RAMP = {
            {0.00F, 0.20F, 0.03F, 0.01F},
            {0.25F, 0.65F, 0.15F, 0.03F},
            {0.50F, 1.00F, 0.42F, 0.08F},
            {0.75F, 1.00F, 0.72F, 0.35F},
            {1.00F, 1.00F, 0.93F, 0.80F},
            {1.25F, 0.85F, 0.93F, 1.00F},
    };

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float smoothstep(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Цвет "жара": 0 - тёмно-красный, 0.5 - оранжевый, 1 - бело-жёлтый, 1.25 - голубовато-белый. */
    public static void ramp(float u, float[] out) {
        u = clamp(u, 0.0F, 1.25F);
        for (int i = 0; i < RAMP.length - 1; i++) {
            float[] a = RAMP[i], b = RAMP[i + 1];
            if (u <= b[0]) {
                float t = (u - a[0]) / (b[0] - a[0]);
                out[0] = a[1] + (b[1] - a[1]) * t;
                out[1] = a[2] + (b[2] - a[2]) * t;
                out[2] = a[3] + (b[3] - a[3]) * t;
                return;
            }
        }
        out[0] = RAMP[RAMP.length - 1][1];
        out[1] = RAMP[RAMP.length - 1][2];
        out[2] = RAMP[RAMP.length - 1][3];
    }

    private static void scale(float[] c, float k) {
        c[0] *= k;
        c[1] *= k;
        c[2] *= k;
    }

    /**
     * Цвет точки аккреционного диска.
     * @param rr   расстояние от центра (DISC_IN..DISC_OUT)
     * @param phi  азимут точки в плоскости диска
     * @param time время в тиках
     * @param camX,camY,camZ положение камеры относительно центра, в единицах rho
     */
    public static void disc(float rr, float phi, float time, float camX, float camY, float camZ, float bright, float[] out) {
        float xn = (rr - DISC_IN) / (DISC_OUT - DISC_IN);
        float base = 1.25F * (float) Math.pow(1.0F - xn, 1.9) + 0.05F;
        base *= smoothstep(0.0F, 0.05F, xn) * (1.0F - smoothstep(0.78F, 1.0F, xn));

        // вращение: внутренние кольца быстрее внешних, узор - закрученные "рукава"
        float omega = 0.30F * (float) Math.pow(DISC_IN / rr, 1.5);
        float ph = phi + omega * time;
        float pat = 0.62F + 0.38F * (0.55F * (float) Math.sin(2.0F * ph + 6.0F * (float) Math.log(rr))
                + 0.30F * (float) Math.sin(3.0F * ph - 5.0F * rr)
                + 0.15F * (float) Math.sin(5.0F * ph + 3.0F * rr));

        // направление на камеру и скорость вещества в этой точке (вращение против часовой стрелки сверху)
        float cs = (float) Math.cos(phi), sn = (float) Math.sin(phi);
        float nx = camX - rr * cs, ny = camY, nz = camZ - rr * sn;
        float inv = 1.0F / (float) Math.sqrt(nx * nx + ny * ny + nz * nz + 1.0E-6F);
        float cosT = (sn * nx - cs * nz) * inv;

        float beta = 0.50F / (float) Math.sqrt(rr);
        float gamma = 1.0F / (float) Math.sqrt(1.0F - beta * beta);
        float delta = 1.0F / (gamma * (1.0F - beta * cosT));
        float grav = (float) Math.sqrt(Math.max(0.0F, 1.0F - 0.385F / rr));
        float g = delta * grav;

        float beam = (float) Math.pow(g, 2.3);
        float heat = 0.9F * (float) Math.pow(1.0F - xn, 0.6) * (float) Math.pow(Math.max(g, 0.05F), 0.9);
        float inten = base * pat * beam * bright * 1.2F;
        float k = 1.0F - (float) Math.exp(-inten * 1.4F);
        ramp(heat, out);
        scale(out, k);
    }

    // --- гало (линзированное изображение диска вокруг тени), рисуется кольцом, обращённым к камере

    /** sI = cos(угла возвышения камеры над плоскостью диска), asa = |sin(экранного угла)|. */
    public static float haloSMax(float sI, float asa) {
        return 0.6F + 2.6F * sI * (float) Math.pow(asa, 1.3);
    }

    public static float haloTau(float sI, float asa) {
        return 0.16F + 0.42F * sI * (float) Math.pow(asa, 1.2);
    }

    public static float haloAmp(float sI, float asa) {
        return 0.50F + 0.75F * sI * (float) Math.pow(asa, 0.8);
    }

    /** cR - составляющая скорости вещества на правой стороне вдоль луча зрения, ca - cos экранного угла. */
    public static float haloDelta(float cR, float ca) {
        float beta = 0.40F;
        float gamma = 1.0F / (float) Math.sqrt(1.0F - beta * beta);
        return 1.0F / (gamma * (1.0F - beta * cR * ca));
    }

    /** s - расстояние от края тени в радиусах тени. */
    public static void halo(float s, float tau, float amp, float delta, float bright, float[] out) {
        float beam = delta * delta * delta;
        float inten = amp * beam * (0.75F * (float) Math.exp(-s / tau) + 0.5F * (float) Math.exp(-s / 0.03F));
        float heat = 0.78F * (float) Math.exp(-s / 1.0F) * (float) Math.pow(delta, 0.9);
        float k = 1.0F - (float) Math.exp(-inten * bright * 1.0F);
        ramp(heat, out);
        scale(out, k);
    }

    /** Мягкое свечение вокруг дыры. */
    public static void bloom(float s, float bright, float[] out) {
        float warm = 0.20F * (float) Math.exp(-s / 1.2F) * bright;
        float cool = 0.05F * (float) Math.exp(-s / 3.0F) * bright;
        out[0] = 1.00F * warm + 0.35F * cool;
        out[1] = 0.62F * warm + 0.20F * cool;
        out[2] = 0.28F * warm + 0.60F * cool;
    }
}
