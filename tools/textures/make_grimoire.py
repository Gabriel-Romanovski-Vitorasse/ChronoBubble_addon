"""Uso: python3 tools/textures/make_grimoire.py src/main/resources/assets/chrono_bubble/textures/effect

Gera as texturas do grimório do Julius (do zero, só com Pillow):
  grimoire_pages.png   - uma "fatia" de página: branca, com a linha escura da borda em u = 0. Usada nas tampas
                         (a linha vira a espiral do redemoinho) e na lateral (a linha vira a borda vertical).
  grimoire_star.png    - estrela de quatro pontas com brilho ciano, sobre fundo preto (desenhada com mistura
                         aditiva: preto = transparente).
"""
import os, sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1]
S = 4

# ---- páginas ----
W, H = 64, 64
img = Image.new('RGBA', (W * S, H * S), (255, 255, 255, 255))
d = ImageDraw.Draw(img)
for x in range(W * S):                      # papel levemente mais escuro perto da borda da página seguinte
    t = x / (W * S)
    c = int(252 - 10 * t ** 3)
    d.line([(x, 0), (x, H * S)], fill=(c, c + 1 if c < 255 else c, 255, 255))
line = 10 * S                                # linha da borda (larga o bastante para não sumir de longe)
d.rectangle([0, 0, line, H * S], fill=(84, 100, 138, 255))
d.rectangle([line, 0, line + 2 * S, H * S], fill=(200, 210, 228, 255))   # sombra suave depois da linha
img.resize((W, H), Image.LANCZOS).save(os.path.join(out, 'grimoire_pages.png'))

# ---- estrela ----
N = 64
star = Image.new('RGB', (N * S, N * S), (0, 0, 0))
c = N * S / 2
def petal(angle, length, width):
    pts = []
    for k in range(64):                              # elipse fina girada em 'angle'
        t = k / 63 * 2 * math.pi
        x, y = math.cos(t) * length, math.sin(t) * width
        a = math.radians(angle)
        pts.append((c + x * math.cos(a) - y * math.sin(a), c + x * math.sin(a) + y * math.cos(a)))
    return pts
glow = Image.new('RGB', star.size, (0, 0, 0)); g = ImageDraw.Draw(glow)
g.ellipse([c - 20 * S, c - 20 * S, c + 20 * S, c + 20 * S], fill=(40, 120, 170))
star.paste(glow.filter(ImageFilter.GaussianBlur(9 * S)))
layer = Image.new('RGB', star.size, (0, 0, 0)); l = ImageDraw.Draw(layer)
for ang, ln, wd, col in ((0, 31, 3.2, (150, 225, 255)), (90, 31, 3.2, (150, 225, 255)),
                         (45, 13, 2.0, (90, 170, 220)), (135, 13, 2.0, (90, 170, 220))):
    l.polygon(petal(ang, ln * S, wd * S), fill=col)
def brighten(base, top):                            # junta camadas pegando o mais claro de cada pixel
    return Image.fromarray(np.maximum(np.asarray(base), np.asarray(top)))
star = brighten(star, layer.filter(ImageFilter.GaussianBlur(1.2 * S)))
core = Image.new('RGB', star.size, (0, 0, 0)); ImageDraw.Draw(core).ellipse([c - 5 * S, c - 5 * S, c + 5 * S, c + 5 * S], fill=(255, 255, 255))
star = brighten(star, core.filter(ImageFilter.GaussianBlur(2.5 * S)))
star.resize((N, N), Image.LANCZOS).convert('RGBA').save(os.path.join(out, 'grimoire_star.png'))
print('ok')
