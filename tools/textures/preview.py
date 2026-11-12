"""Uso: python3 tools/textures/preview.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png previa.png

Prévia fora do jogo: ray tracing simples com a MESMA conta do StasisBubbleRenderer (fresnel + brilho + faixa)."""
import sys, math
import numpy as np
from PIL import Image

ring = np.asarray(Image.open(sys.argv[1]).convert('RGBA')).astype(np.float32) / 255.0
OUT, N = sys.argv[2], 520
SPHERE_ALPHA, CENTER_ALPHA, RIM_POWER, RING_ALPHA = 0.80, 0.10, 1.8, 0.90
TILT, BAND, ASPECT = math.radians(12), 0.18, 16.0
TOWARD = math.radians(22)   # inclinação na direção de quem olha
CENTER = np.array([0.84, 0.93, 1.00]); EDGE = np.array([0.30, 0.60, 1.00])
H = np.array([-0.45, 0.65, 0.6]); H /= np.linalg.norm(H)

def bg_img():
    y = np.linspace(0, 1, N)[:, None, None]
    sky = np.array([0.55, 0.72, 0.95]) * (1 - y) + np.array([0.36, 0.55, 0.25]) * y   # céu em cima, grama embaixo
    return np.broadcast_to(sky, (N, N, 3)).copy()

def render(rot_deg, cam_dist=3.2):
    img = bg_img()
    xs = (np.arange(N) + 0.5) / N * 2 - 1
    px, py = np.meshgrid(xs, -xs)
    d = np.stack([px * 0.62, py * 0.62, -np.ones_like(px)], -1)
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    o = np.array([0, 0, cam_dist])                       # câmera em +z olhando para -z; esfera de raio 1 na origem
    layers = []                                          # (t, rgb, a)

    # esfera: as duas faces (a de trás aparece através da da frente)
    b = (d * o).sum(-1); c = o @ o - 1.0; disc = b * b - c
    hit = disc > 0; sq = np.sqrt(np.where(hit, disc, 0))
    for t in (-b - sq, -b + sq):
        p = o + d * t[..., None]; n = p                   # normal = posição (raio 1)
        facing = np.abs((n * -d).sum(-1)).clip(0, 1)
        rim = (1 - facing) ** RIM_POWER
        spec = np.clip((n * H).sum(-1), 0, None) ** 24 * 0.7
        rgb = CENTER * (1 - rim[..., None]) + EDGE * rim[..., None] + spec[..., None]
        a = SPHERE_ALPHA * (CENTER_ALPHA + (1 - CENTER_ALPHA) * rim) + spec * SPHERE_ALPHA
        layers.append((np.where(hit, t, np.inf), rgb.clip(0, 1), np.where(hit, a.clip(0, 1), 0)))

    # faixa: cilindro de raio 1.04 e altura BAND, inclinado TILT no eixo z e girado rot no y
    R = 1.04; half = BAND / 2
    cz, sz = math.cos(-TILT), math.sin(-TILT)
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    ry = math.radians(-rot_deg); Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    cx, sx = math.cos(-TOWARD), math.sin(-TOWARD)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    M = Ry @ Rz @ Rx                                      # mundo -> espaço do anel
    ol = M @ o; dl = d @ M.T
    A = dl[..., 0] ** 2 + dl[..., 2] ** 2; B = 2 * (ol[0] * dl[..., 0] + ol[2] * dl[..., 2]); C = ol[0] ** 2 + ol[2] ** 2 - R * R
    disc = B * B - 4 * A * C; ok = disc > 0; sq = np.sqrt(np.where(ok, disc, 0))
    reps = max(1, round(2 * math.pi * R / (BAND * ASPECT)))
    for t in ((-B - sq) / (2 * A), (-B + sq) / (2 * A)):
        p = ol + dl * t[..., None]
        inside = ok & (np.abs(p[..., 1]) <= half) & (t > 0)
        ang = (1.0 - np.arctan2(p[..., 2], p[..., 0]) / (2 * math.pi)) % 1.0   # u decresce com o angulo, como no Java
        u = ((ang * reps) % 1.0 * (ring.shape[1] - 1)).astype(int)
        v = ((half - p[..., 1]) / BAND * (ring.shape[0] - 1)).clip(0, ring.shape[0] - 1).astype(int)
        tex = ring[v, u]
        layers.append((np.where(inside, t, np.inf), tex[..., :3], np.where(inside, tex[..., 3] * RING_ALPHA, 0)))

    # compõe de trás para a frente
    ts = np.stack([l[0] for l in layers]); order = np.argsort(-ts, axis=0)
    rgbs = np.stack([l[1] for l in layers]); als = np.stack([l[2] for l in layers])
    for k in range(len(layers)):
        idx = order[k]
        rgb = np.take_along_axis(rgbs, idx[None, ..., None].repeat(3, -1), 0)[0]
        a = np.take_along_axis(als, idx[None], 0)[0][..., None]
        img = img * (1 - a) + rgb * a
    return Image.fromarray((img.clip(0, 1) * 255).astype(np.uint8))

frames = [render(r) for r in (0, 60, 140)]
sheet = Image.new('RGB', (N * 3 + 20, N), (20, 20, 20))
for i, f in enumerate(frames): sheet.paste(f, (i * (N + 10), 0))
sheet.save(OUT); print('ok')
