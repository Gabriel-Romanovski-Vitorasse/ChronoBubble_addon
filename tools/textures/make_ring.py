"""Uso: python3 tools/textures/make_ring.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png

Gera a textura da faixa de numerais do Chrono Stasis (do zero, só com Pillow).

Faixa azul translúcida, bordas brancas finas, numerais romanos brancos com espaçamento irregular e letrinhas
(C, D, M, V...) acima/abaixo da linha. A textura emenda nas pontas (repete em volta do anel).
Desenhada em 4x e reduzida, para as bordas ficarem suaves.
"""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, S = 1024, 64, 4                     # tamanho final e fator de supersampling
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
BIG = ImageFont.truetype(FONT, 30 * S)
SMALL = ImageFont.truetype(FONT, 15 * S)

# (texto, tamanho, posição vertical: 0 = na linha, -1 = acima, +1 = abaixo, espaço depois em px finais)
GLYPHS = [
    ("XI", 'b', 0, 10), ("V", 's', -1, 22), ("II", 'b', 0, 16), ("C", 's', 1, 26),
    ("VII", 'b', 0, 6), ("VI", 's', 1, 30), ("I", 'b', 0, 34), ("XII", 'b', 0, 8),
    ("V", 's', -1, 24), ("D", 'b', 0, 28), ("V", 'b', 0, 4), ("M", 's', 1, 30),
    ("IX", 'b', 0, 8), ("II", 's', -1, 26), ("X", 'b', 0, 10), ("IV", 's', -1, 30),
    ("III", 'b', 0, 22), ("L", 's', 1, 28), ("VIII", "b", 0, 18),
]

img = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# fundo: azul translúcido, um pouco mais claro no meio da faixa
for y in range(H * S):
    t = abs((y + 0.5) / (H * S) - 0.5) * 2          # 0 no meio, 1 nas bordas
    a = int(80 + 45 * t)
    d.line([(0, y), (W * S, y)], fill=(150, 200, 255, a))

# bordas brancas finas (uma mais forte por fora, uma suave por dentro)
for y0, a in ((3, 235), (7, 90)):
    d.rectangle([0, y0 * S, W * S, y0 * S + S + S // 2], fill=(255, 255, 255, a))
    yb = H - y0 - 2
    d.rectangle([0, yb * S, W * S, yb * S + S + S // 2], fill=(255, 255, 255, a))

# mede o texto para espalhar a sobra igualmente e emendar nas pontas
def size(txt, f):
    l, t, r, b = d.textbbox((0, 0), txt, font=f)
    return r - l, l, t, b
fonts = {'b': BIG, 's': SMALL}
used = sum(size(g[0], fonts[g[1]])[0] / S + g[3] for g in GLYPHS)
extra = (W - used) / len(GLYPHS)
if extra < 0:
    sys.exit('texto não cabe: %.1f px a mais' % -used)

x = extra / 2
mid = H * S / 2
for txt, sz, pos, gap in GLYPHS:
    f = fonts[sz]
    w, l, t, b = size(txt, f)
    h = b - t
    cy = mid + pos * 9 * S                          # letrinhas acima/abaixo da linha
    # brilho suave atrás do numeral
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text((x * S - l, cy - h / 2 - t), txt, font=f, fill=(200, 230, 255, 160))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(3 * S)))
    d.text((x * S - l, cy - h / 2 - t), txt, font=f, fill=(255, 255, 255, 245))
    x += w / S + gap + extra

out = img.resize((W, H), Image.LANCZOS)
out.save(sys.argv[1])
print('ok', out.size)
