# Ferramentas

Scripts de apoio (Python 3). Não fazem parte do `.jar`.

| Script | Para quê | Precisa de |
|---|---|---|
| `config/genconfig.py` | gera `ChronoConfig.java` e as chaves `config.chrono_bubble.*` dos idiomas a partir de `config/opts.py` | — |
| `textures/make_ring.py` | gera `textures/effect/ring.png` (faixa de numerais do Chrono Stasis) | Pillow, fonte DejaVu Serif Bold |
| `textures/make_sphere.py` | gera `textures/effect/sphere.png` (branca: a cor vem do código) | Pillow |
| `textures/preview.py` | prévia da bolha fora do jogo, com a mesma conta do `StasisBubbleRenderer` | Pillow, NumPy |
| `textures/make_grimoire.py` | gera `grimoire_pages.png` (fatia de página com a linha da borda) e `grimoire_star.png` | Pillow, NumPy |
| `textures/preview_grimoire.py` | prévia do grimório fora do jogo, com a mesma geometria do `TimeGrimoireRenderer` | Pillow, NumPy |

Rode da raiz do repositório:

```bash
python3 tools/config/genconfig.py src/main/java/com/gabriel/chronobubble/ChronoConfig.java src/main/resources/assets/chrono_bubble/lang
```

```bash
python3 tools/textures/make_ring.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png
```

```bash
python3 tools/textures/preview.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png previa.png
```

Se mudar algo no desenho da bolha (`StasisBubbleRenderer.java`) ou do grimório (`TimeGrimoireRenderer.java`), mude
também as constantes no topo do script de prévia correspondente, para a prévia continuar fiel.
