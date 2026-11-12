"""Textura da esfera: branca e lisa. Cor, brilho e transparência vêm do código (efeito fresnel por vértice)."""
import sys
from PIL import Image
Image.new('RGBA', (16, 16), (255, 255, 255, 255)).save(sys.argv[1])
print('ok')
