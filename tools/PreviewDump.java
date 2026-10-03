import java.io.*;
import com.blackhole.client.render.BlackHoleShading;

/** Выгружает точки (экранные координаты + цвет) ровно тех функций, что использует мод. Рисует preview.py. */
public class PreviewDump {
    public static void main(String[] a) throws Exception {
        float elev = (float) Math.toRadians(Double.parseDouble(a[0]));
        float time = Float.parseFloat(a[1]);
        float dist = Float.parseFloat(a[2]);   // расстояние камеры в rho
        String out = a[3];
        float vx = (float) (Math.cos(elev) * 0.6), vy = (float) Math.sin(elev), vz = (float) (Math.cos(elev) * 0.8);
        // right = (-v) x up
        float rx = vz, rz = -vx; float rl = (float) Math.hypot(rx, rz); rx /= rl; rz /= rl;
        float fx = -vx, fy = -vy, fz = -vz;
        // up = right x forward
        float ux = 0 * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - 0 * fx;
        float cR = rz * vx - rx * vz;
        float sI = (float) Math.sqrt(1 - vy * vy);
        float[] c = new float[3];
        try (DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out), 1 << 20))) {
            os.writeFloat(0);
            int RINGS = 500, SEG = 1800;
            for (int k = 0; k < RINGS; k++) {
                float rr = BlackHoleShading.DISC_IN + (BlackHoleShading.DISC_OUT - BlackHoleShading.DISC_IN) * (k + 0.5f) / RINGS;
                float w = rr * (BlackHoleShading.DISC_OUT - BlackHoleShading.DISC_IN) / RINGS * (float) (2 * Math.PI / SEG);
                for (int j = 0; j < SEG; j++) {
                    float phi = (float) (2 * Math.PI * j / SEG);
                    float px = rr * (float) Math.cos(phi), pz = rr * (float) Math.sin(phi);
                    float d = px * vx + pz * vz;
                    float lx = px - d * vx, ly = -d * vy, lz = pz - d * vz;
                    float lat = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                    if (lat < 0.985f && d < Math.sqrt(1 - lat * lat)) continue; // за сферой
                    BlackHoleShading.disc(rr, phi, time, vx * dist, vy * dist, vz * dist, 1f, c);
                    os.writeFloat(px * rx + pz * rz); os.writeFloat(px * ux + pz * uz);
                    os.writeFloat(c[0]); os.writeFloat(c[1]); os.writeFloat(c[2]); os.writeFloat(w);
                }
            }
            int SEGH = 1440, RS = 500;
            for (int j = 0; j < SEGH; j++) {
                float al = (float) (2 * Math.PI * j / SEGH);
                float ca = (float) Math.cos(al), sa = (float) Math.sin(al), asa = Math.abs(sa);
                float sMax = BlackHoleShading.haloSMax(sI, asa), tau = BlackHoleShading.haloTau(sI, asa), amp = BlackHoleShading.haloAmp(sI, asa);
                float delta = BlackHoleShading.haloDelta(cR, ca);
                for (int k = 0; k < RS; k++) {
                    float t = (k + 0.5f) / RS; float s = sMax * t * t; float ds = sMax * 2 * t / RS;
                    float rad = 1.02f + s;
                    BlackHoleShading.halo(s, tau, amp, delta, 1f, c);
                    os.writeFloat(rad * ca); os.writeFloat(rad * sa);
                    os.writeFloat(c[0]); os.writeFloat(c[1]); os.writeFloat(c[2]);
                    os.writeFloat(rad * ds * (float) (2 * Math.PI / SEGH));
                }
            }
            for (int j = 0; j < 720; j++) {
                float al = (float) (2 * Math.PI * j / 720); float ca = (float) Math.cos(al), sa = (float) Math.sin(al);
                for (int k = 0; k < 400; k++) {
                    float t = (k + 0.5f) / 400; float s = 6.5f * t * t; float ds = 6.5f * 2 * t / 400; float rad = 1.0f + s;
                    BlackHoleShading.bloom(s, 1f, c);
                    os.writeFloat(rad * ca); os.writeFloat(rad * sa);
                    os.writeFloat(c[0]); os.writeFloat(c[1]); os.writeFloat(c[2]);
                    os.writeFloat(rad * ds * (float) (2 * Math.PI / 720));
                }
            }
        }
    }
}
