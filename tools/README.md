# Ferramentas

Scripts de apoio (Python 3). Não fazem parte do `.jar`.

| Script | Para quê | Precisa de |
|---|---|---|
| `config/genconfig.py` | gera `ChronoConfig.java` e as chaves `config.chrono_bubble.*` dos idiomas a partir de `config/opts.py` | — |
| `textures/make_ring.py` | gera `textures/effect/ring.png` (faixa de numerais do Chrono Stasis) | Pillow, fonte DejaVu Serif Bold |
| `textures/make_sphere.py` | gera `textures/effect/sphere.png` (branca: a cor vem do código) | Pillow |
| `textures/preview.py` | prévia da bolha fora do jogo, com a mesma conta do `StasisBubbleRenderer` | Pillow, NumPy |
| `sounds/make_sounds.py` | gera sons originais (síntese, sem áudio de terceiros) para `stasis_start`, `stasis_end` e `time_dissolve` | NumPy, ffmpeg com libvorbis |

Rode da raiz do repositório:

```bash
python3 tools/config/genconfig.py src/main/java/com/gabriel/chronobubble/ChronoConfig.java src/main/resources/assets/chrono_bubble/lang
```

```bash
python3 tools/textures/make_ring.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png
```

```bash
python3 tools/sounds/make_sounds.py pasta_de_saida
```

O gerador de sons grava numa pasta à sua escolha: ouça antes de copiar os `.ogg` para
`src/main/resources/assets/chrono_bubble/sounds/`.

```bash
python3 tools/textures/preview.py src/main/resources/assets/chrono_bubble/textures/effect/ring.png previa.png
```

Se mudar algo no desenho da bolha em `StasisBubbleRenderer.java` (cores, inclinação, largura da faixa), mude também
as constantes no topo do `preview.py`, para a prévia continuar fiel.
