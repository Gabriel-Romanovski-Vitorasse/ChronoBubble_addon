"""Uso: python3 tools/textures/preview_grimoire.py src/main/resources/assets/chrono_bubble/textures/effect previa.png

Prévia do grimório fora do jogo: ray tracing simples com a MESMA geometria do TimeGrimoireRenderer
(espiral das tampas, linhas da lateral, aura aditiva mais forte na silhueta e estrela no centro).
"""
import os, sys, math
import numpy as np
from PIL import Image

TEX, OUT = sys.argv[1], sys.argv[2]
pages_tex = np.asarray(Image.open(os.path.join(TEX, 'grimoire_pages.png')).convert('RGB')).astype(np.float32) / 255
star_tex = np.asarray(Image.open(os.path.join(TEX, 'grimoire_star.png')).convert('RGB')).astype(np.float32) / 255
PAGES, TWIST, LINE_EVERY, PLAIN_U = 120, 2.6, 4, 0.3
R, H = 0.26, 0.52
AURA = [(1.07, 0.60), (1.17, 0.38), (1.32, 0.20)]
AURA_RGB = np.array([0.45, 0.85, 1.00])
N = 420

def render(elev_deg, spin_deg, pages=PAGES):
    e = math.radians(elev_deg)
    cam = np.array([0, math.sin(e), math.cos(e)]) * 1.6
    fwd = -cam / np.linalg.norm(cam); right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right); up = np.cross(right, fwd)
    xs = (np.arange(N) + 0.5) / N * 2 - 1
    px, py = np.meshgrid(xs, -xs)
    d = fwd + px[..., None] * right * 0.42 + py[..., None] * up * 0.42
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    img = np.zeros((N, N, 3)) + np.array([0.28, 0.08, 0.06])          # fundo escuro avermelhado
    spin = math.radians(spin_deg)

    def hit_cyl(r, h):
        """Primeiro e último ponto em que o raio toca o cilindro fechado (tampas + lateral)."""
        ts = []
        A = d[..., 0] ** 2 + d[..., 2] ** 2; B = 2 * (cam[0] * d[..., 0] + cam[2] * d[..., 2]); C = cam[0] ** 2 + cam[2] ** 2 - r * r
        disc = B * B - 4 * A * C; ok = disc > 0; sq = np.sqrt(np.where(ok, disc, 0))
        for t in ((-B - sq) / (2 * A), (-B + sq) / (2 * A)):
            y = cam[1] + d[..., 1] * t
            ts.append((np.where(ok & (np.abs(y) <= h / 2) & (t > 0), t, np.inf), 'side'))
        for s in (1, -1):
            t = (s * h / 2 - cam[1]) / d[..., 1]
            p = cam + d * t[..., None]
            ts.append((np.where((p[..., 0] ** 2 + p[..., 2] ** 2 <= r * r) & (t > 0), t, np.inf), 'top' if s > 0 else 'bottom'))
        return ts

    # bloco de páginas
    hits = hit_cyl(R, H)
    tmin = np.min(np.stack([h[0] for h in hits]), 0)
    solid = np.isfinite(tmin)
    p = cam + d * np.where(solid, tmin, 0)[..., None]
    theta = (np.arctan2(p[..., 2], p[..., 0]) - spin) % (2 * math.pi)
    rr = np.sqrt(p[..., 0] ** 2 + p[..., 2] ** 2)
    on_cap = np.abs(np.abs(p[..., 1]) - H / 2) < 1e-6
    f = np.clip((rr / R - 0.02) / 0.98, 0, 1)
    twist_term = TWIST * np.sqrt(np.where(on_cap, f, 1.0))
    phase = ((theta - twist_term) % (2 * math.pi)) * PAGES / (2 * math.pi)
    page_idx = np.floor(phase).astype(int)
    frac = phase - page_idx
    frac = np.where(page_idx % LINE_EVERY == 0, frac, PLAIN_U + (1 - PLAIN_U) * frac)   # linha s\u00f3 a cada LINE_EVERY
    u = frac * (pages_tex.shape[1] - 1)
    v = np.where(on_cap, f, (H / 2 - p[..., 1]) / H) * (pages_tex.shape[0] - 1)
    col = pages_tex[v.astype(int).clip(0, 63), u.astype(int).clip(0, 63)]
    shade = np.where(on_cap, 1.0, 0.72 + 0.28 * np.cos(theta + spin - 0.8)) [..., None]
    drawn = solid & (page_idx < pages)
    img = np.where(drawn[..., None], col * shade, img)

    # aura aditiva (atrás do bloco não aparece)
    depth = np.where(drawn, tmin, np.inf)
    for scale, power in AURA:
        r = R * scale; h = H + 2 * R * (scale - 1)
        for t, kind in hit_cyl(r, h):
            vis = np.isfinite(t) & (t < depth)
            q = cam + d * np.where(vis, t, 0)[..., None]
            n = np.where(kind == 'side', 1, 0) * np.stack([q[..., 0], np.zeros_like(t), q[..., 2]], -1) / r \
                + np.where(kind == 'side', 0, 1) * np.array([0, 1 if kind == 'top' else -1, 0])
            facing = np.abs((n * d).sum(-1)).clip(0, 1)
            k = power * (0.15 + 0.85 * (1 - facing) ** 2) * pages / PAGES
            img = img + np.where(vis[..., None], AURA_RGB * k[..., None], 0)

    # estrela no centro do topo, virada para a câmera
    top = np.array([0, H / 2 + 0.03, 0]); rel = top - cam
    z = rel @ fwd; sx = (rel @ right) / z / 0.42; sy = (rel @ up) / z / 0.42
    size = 0.15 / z / 0.42
    gx = (px - sx) / size; gy = (py - sy) / size
    inside = (np.abs(gx) < 1) & (np.abs(gy) < 1)
    st = star_tex[((1 - (gy + 1) / 2) * 63).clip(0, 63).astype(int), (((gx + 1) / 2) * 63).clip(0, 63).astype(int)]
    if pages > 8: img = img + np.where(inside[..., None], st, 0)
    return Image.fromarray((img.clip(0, 1) * 255).astype(np.uint8))

# renderiza em 2x e reduz (suaviza como a tela do jogo faria de longe)
N *= 2
views = [render(38, 0), render(38, 40), render(70, 10), render(38, 0, pages=50)]
N //= 2
views = [v.resize((N, N), Image.LANCZOS) for v in views]
sheet = Image.new('RGB', (N * 4 + 30, N), (15, 15, 15))
for i, v in enumerate(views): sheet.paste(v, (i * (N + 10), 0))
sheet.save(OUT); print('ok')
