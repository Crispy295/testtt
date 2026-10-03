#!/usr/bin/env python3
"""Синтез звуков: гул чёрной дыры и звук коллапса (numpy -> wav -> ogg через ffmpeg)."""
import os, subprocess, wave
import numpy as np
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "blackhole", "sounds")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def lowpass_noise(n, cutoff):
    x = rng.standard_normal(n); X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR); X *= 1 / (1 + (f / cutoff) ** 4)
    y = np.fft.irfft(X, n); return y / np.abs(y).max()

def save(name, y):
    y = np.clip(y / max(np.abs(y).max(), 1e-9) * 0.9, -1, 1)
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((y * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)

# --- гул: 3 c, зацикливаемый (целое число периодов у всех гармоник)
T = 3.0; n = int(SR * T); t = np.arange(n) / SR
hum = sum(a * np.sin(2 * np.pi * f * t + p) for f, a, p in [(30, 1.0, 0), (45, 0.7, 1.3), (60, 0.5, 2.1), (90, 0.25, 0.4)])
hum *= 0.75 + 0.25 * np.sin(2 * np.pi * (2 / T) * t)
rumble = lowpass_noise(n, 120)
rumble = rumble * (0.6 + 0.4 * np.sin(2 * np.pi * (1 / T) * t + 1))
y = hum * 0.8 + rumble * 0.7
fade = np.minimum(1, np.minimum(t, T - t) / 0.02); save("hum", y * fade)

# --- коллапс: удар + падающий тон + хвост
T = 3.2; n = int(SR * T); t = np.arange(n) / SR
f = 140 * np.exp(-t * 1.6) + 18
ph = 2 * np.pi * np.cumsum(f) / SR
boom = np.sin(ph) * np.exp(-t * 1.1)
noise = lowpass_noise(n, 900) * np.exp(-t * 2.2)
click = lowpass_noise(n, 6000) * np.exp(-t * 40)
whoosh = lowpass_noise(n, 2500) * np.exp(-((t - 0.25) / 0.35) ** 2) * 0.5
save("collapse", boom * 1.0 + noise * 0.8 + click * 0.5 + whoosh)

# --- появление: нарастающий свист + удар
T = 2.4; n = int(SR * T); t = np.arange(n) / SR
f = 40 + 900 * (t / T) ** 3
ph = 2 * np.pi * np.cumsum(f) / SR
sweep = np.sin(ph) * (t / T) ** 2 * np.exp(-((t - T) / 0.9) ** 2 * 0)
hit = np.sin(2 * np.pi * 38 * t) * np.exp(-np.maximum(t - 1.5, 0) * 2.5) * (t > 1.5)
nz = lowpass_noise(n, 1800) * (t / T) ** 2 * 0.5
y = sweep * 0.6 + nz + hit * 1.6
y *= np.minimum(1, t / 0.05); save("spawn", y)
