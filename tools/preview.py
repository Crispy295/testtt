#!/usr/bin/env python3
"""Рисует превью из точек, выгруженных PreviewDump.java. usage: preview.py dump.bin out.png"""
import sys, numpy as np
from PIL import Image
d = np.fromfile(sys.argv[1], dtype=">f4")[1:].reshape(-1, 6)
N, ext = 640, 7.0
px = N / (2 * ext)
x = ((d[:, 0] + ext) * px).astype(int); y = ((ext - d[:, 1]) * px).astype(int)
ok = (x >= 0) & (x < N) & (y >= 0) & (y < N)
img = np.zeros((N, N, 3), np.float32)
cov = d[:, 5] * px * px
for ch in range(3):
    np.add.at(img[..., ch], (y[ok], x[ok]), (d[:, 2 + ch] * cov)[ok])
bg = np.zeros((N, N, 3), np.float32) + np.array([0.10, 0.11, 0.15], np.float32)
yy, xx = np.mgrid[0:N, 0:N]
sphere = ((xx - N / 2 + .5) ** 2 + (yy - N / 2 + .5) ** 2) < (0.985 * px) ** 2
bg[sphere] = 0
out = np.clip(bg + img, 0, 1)
Image.fromarray((out ** (1 / 1.0) * 255).astype(np.uint8)).save(sys.argv[2])
