#!/usr/bin/env python3
"""Генератор текстур мода: реальная трассировка геодезических Шварцшильда.
Рисует чёрную дыру с аккреционным диском (линзирование, доплер-эффект, гравитационное
красное смещение, чернотельный цвет) -> анимированная иконка предмета.
Запуск: python3 tools/gen_textures.py   (нужны numpy, scipy, pillow)
"""
import os, json
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "blackhole")

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def blackbody(T):
    t = np.clip(T, 1000, 40000) / 100.0
    r = np.where(t <= 66, 255.0, 329.698727446 * np.maximum(t - 60, 1e-3) ** -0.1332047592)
    g = np.where(t <= 66, 99.4708025861 * np.log(t) - 161.1195681661,
                 288.1221695283 * np.maximum(t - 60, 1e-3) ** -0.0755148492)
    b = np.where(t >= 66, 255.0, np.where(t <= 19, 0.0, 138.5177312231 * np.log(np.maximum(t - 10, 1e-3)) - 305.0447927307))
    c = np.clip(np.stack([r, g, b], -1), 0, 255) / 255.0
    return c

class BlackHoleTracer:
    def __init__(self, N, fov, inc_deg, rc=40.0, h=0.012, phimax=4.4 * np.pi, rin=3.0, rout=8.0):
        self.N, self.fov, self.rin, self.rout = N, fov, rin, rout
        i = np.radians(inc_deg); self.i = i
        xs = (np.arange(N) + 0.5) / N * 2 - 1
        X, Y = np.meshgrid(xs * fov, -xs * fov)
        self.X, self.Y = X, Y
        b = np.maximum(np.hypot(X, Y), 1e-6)
        A = np.cos(i); B = np.sin(i) * Y / b
        phi0 = np.arctan2(-A, B) % np.pi
        u = np.full(b.shape, 1.0 / rc)
        w = np.sqrt(np.maximum(1 / b**2 - u**2 * (1 - u), 0))
        alive = np.ones(b.shape, bool)
        captured = np.zeros(b.shape, bool)
        K = 4
        self.r = np.full((K,) + b.shape, np.nan)
        self.phik = np.stack([phi0 + k * np.pi for k in range(K)])
        found = np.zeros((K,) + b.shape, bool)
        f = lambda u: -u + 1.5 * u * u
        steps = int(phimax / h)
        for s in range(steps):
            p0 = s * h
            k1u, k1w = w, f(u)
            k2u, k2w = w + 0.5 * h * k1w, f(u + 0.5 * h * k1u)
            k3u, k3w = w + 0.5 * h * k2w, f(u + 0.5 * h * k2u)
            k4u, k4w = w + h * k3w, f(u + h * k3u)
            un = u + h / 6 * (k1u + 2 * k2u + 2 * k3u + k4u)
            wn = w + h / 6 * (k1w + 2 * k2w + 2 * k3w + k4w)
            for k in range(K):
                m = alive & ~found[k] & (self.phik[k] > p0) & (self.phik[k] <= p0 + h)
                if m.any():
                    fr = (self.phik[k] - p0) / h
                    uc = u + fr * (un - u)
                    self.r[k][m] = 1.0 / np.maximum(uc[m], 1e-6)
                    found[k][m] = True
            cap = alive & (un >= 1.0)
            esc = alive & (un <= 0.0)
            captured |= cap
            alive &= ~(cap | esc)
            u = np.where(alive, un, u); w = np.where(alive, wn, w)
            if not alive.any():
                break
        captured |= alive  # крутятся у фотонной сферы -> считаем захваченными
        self.captured = captured.astype(np.float32)
        self.found = found
        # азимут точки пересечения с диском
        ex = -Y * np.cos(i) / b; ez = -X / b
        self.phid = []
        for k in range(K):
            ph = self.phik[k]; r = np.nan_to_num(self.r[k], nan=1.0)
            px = r * (np.cos(ph) * np.sin(i) + np.sin(ph) * ex)
            pz = r * (np.sin(ph) * ez)
            self.phid.append(np.arctan2(pz, px))

    def shade(self, frame=0, frames=48, exposure=3.6, alpha_disc=0.97):
        X, i = self.X, self.i
        L = np.zeros(X.shape + (3,)); Tr = np.ones(X.shape)
        for k in range(self.r.shape[0]):
            r = np.nan_to_num(self.r[k], nan=1e3)
            hit = self.found[k] & (r >= self.rin) & (r <= self.rout)
            if not hit.any(): continue
            Om = np.sqrt(0.5 / r**3)
            g = np.sqrt(np.maximum(1 - 1.5 / r, 1e-4)) / (1 + Om * X * np.sin(i))
            nn = np.clip(np.rint(3.2 * (self.rin / r) ** 1.2), 1, 3)
            ph = self.phid[k] + 2 * np.pi * nn * frame / frames
            pat = 0.66 + 0.34 * (0.5 * np.sin(2 * ph + 5 * np.log(r)) + 0.3 * np.sin(3 * ph - 3 * r) + 0.2 * np.sin(ph + 7 * np.log(r)))
            streak = 0.95 + 0.05 * np.sin(14 * r + 2.0 * np.sin(2 * ph))
            x = (r / self.rin)
            Ie = x ** -2.1 * smoothstep(1.0, 1.12, x) * (1 - smoothstep(5.0 / 3, 8.0 / 3, x))
            Iobs = Ie * np.maximum(g, 0) ** 2.3 * pat * streak
            T = 4700 * x ** -0.55 * np.maximum(g, 1e-3) ** 0.8
            col = blackbody(T)
            col = col / np.maximum(col.max(-1, keepdims=True), 1e-6)
            L += (Tr * Iobs)[..., None] * col * hit[..., None]
            Tr = np.where(hit, Tr * (1 - alpha_disc), Tr)
        P = 1 - np.exp(-exposure * L)
        return P

def compose(P, capt, bloom=(0.9, 0.45), sig=(0.018, 0.05)):
    N = P.shape[0]
    out = P.copy()
    for wgt, s in zip(bloom, sig):
        out += wgt * 0.5 * np.stack([gaussian_filter(P[..., c], s * N) for c in range(3)], -1)
    out = np.clip(out, 0, 1)
    capt = np.clip(gaussian_filter(capt, 0.022 * N) * 2.3, 0, 1)
    return out, capt

def downsample(a, n):
    f = a.shape[0] // n
    return a.reshape(n, f, n, f, *a.shape[2:]).mean((1, 3))

def to_rgba(P, capt):
    A = np.clip(np.maximum(capt, P.max(-1) * 2.2), 0, 1)
    rgb = np.where(A[..., None] > 1e-3, P / np.maximum(A[..., None], 1e-3), 0)
    rgb = np.clip(rgb, 0, 1)
    return (np.dstack([rgb, A]) * 255 + 0.5).astype(np.uint8)

def main():
    os.makedirs(os.path.join(OUT, "textures", "item"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "textures", "entity"), exist_ok=True)
    N = 384
    print("trace...")
    tr = BlackHoleTracer(N, 8.6, 79.0)
    frames = 48
    strip = []
    for f in range(frames):
        P = tr.shade(f, frames)
        P, c = compose(P, tr.captured)
        strip.append(to_rgba(downsample(P, 64), downsample(c, 64)))
    Image.fromarray(np.vstack(strip), "RGBA").save(os.path.join(OUT, "textures", "item", "black_hole.png"), optimize=True)
    with open(os.path.join(OUT, "textures", "item", "black_hole.png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": 2, "interpolate": True}}, fh, indent=2)
    P = tr.shade(0, frames); P, c = compose(P, tr.captured)
    big = to_rgba(downsample(P, 128), downsample(c, 128))
    Image.fromarray(big, "RGBA").save(os.path.join(OUT, "icon.png"))
    prev = to_rgba(P, c)
    Image.fromarray(prev, "RGBA").save(os.path.join(os.path.dirname(__file__), "preview_384.png"))
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(os.path.join(OUT, "textures", "entity", "white.png"))
    print("done")

if __name__ == "__main__":
    main()
