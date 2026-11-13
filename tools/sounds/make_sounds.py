"""Uso: python3 tools/sounds/make_sounds.py <pasta de saída>

Gera, do zero (síntese com NumPy, sem nenhum áudio de terceiros), os sons do Chrono Bubble em Ogg Vorbis mono:
  stasis_start.ogg   o tempo para: whoosh subindo, tique-taques desacelerando, sino cristalino com eco
  stasis_end.ogg     o tempo volta: sino invertido crescendo, tique-taques acelerando
  time_dissolve.ogg  projétil se desfazendo: tique-taques acelerando até virar brilho e um "puff"
Precisa do ffmpeg (com libvorbis) para converter de WAV para OGG.
"""
import os, sys, wave, subprocess, tempfile
import numpy as np

SR = 44100
rng = np.random.default_rng(7)          # fixo: o resultado é sempre o mesmo


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def bell(freq, sec, decay=1.6):
    """Sino com parciais inarmônicas (soa metálico/cristalino)."""
    t = t_axis(sec)
    out = np.zeros_like(t)
    for ratio, amp, dk in ((1.0, 1.0, 1.0), (2.76, 0.55, 1.6), (5.40, 0.30, 2.4), (8.93, 0.18, 3.5), (13.3, 0.08, 5.0)):
        out += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t * decay * dk)
    attack = np.minimum(1.0, t / 0.004)
    return out * attack


def tick(freq=2600.0, sec=0.035):
    """Tique de relógio: estalo curto e metálico."""
    t = t_axis(sec)
    click = np.sin(2 * np.pi * freq * t) * np.exp(-t * 180) + 0.5 * np.sin(2 * np.pi * freq * 1.9 * t) * np.exp(-t * 260)
    noise = rng.standard_normal(len(t)) * np.exp(-t * 500) * 0.3
    return click + noise


def lowpass_sweep(x, f_from, f_to):
    """Filtro passa-baixa de um polo com corte variando no tempo (whoosh)."""
    n = len(x)
    f = np.geomspace(f_from, f_to, n)
    a = np.exp(-2 * np.pi * f / SR)
    y = np.zeros(n)
    acc = 0.0
    for i in range(n):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def reverb(x, mix=0.35, sec_tail=0.8):
    """Eco simples (Schroeder: 4 combs em paralelo + 2 all-pass)."""
    tail = np.concatenate([x, np.zeros(int(SR * sec_tail))])
    out = np.zeros_like(tail)
    for d_ms, g in ((29.7, 0.78), (37.1, 0.76), (41.1, 0.74), (43.7, 0.72)):
        d = int(SR * d_ms / 1000)
        buf = tail.copy()
        for i in range(d, len(buf)):
            buf[i] += g * buf[i - d]
        out += buf
    out /= 4
    for d_ms, g in ((5.0, 0.7), (1.7, 0.7)):
        d = int(SR * d_ms / 1000)
        y = np.zeros_like(out)
        for i in range(len(out)):
            xd = out[i - d] if i >= d else 0.0
            yd = y[i - d] if i >= d else 0.0
            y[i] = -g * out[i] + xd + g * yd
        out = y
    return (1 - mix) * tail + mix * out


def place(dst, src, at_sec, gain=1.0):
    i = int(at_sec * SR)
    j = min(len(dst), i + len(src))
    dst[i:j] += gain * src[: j - i]


def ticks_schedule(start, first_gap, factor, count):
    """Momentos dos tiques: cada intervalo multiplica o anterior (factor > 1 desacelera, < 1 acelera)."""
    times, t, gap = [], start, first_gap
    for _ in range(count):
        times.append(t)
        t += gap
        gap *= factor
    return times


def normalize(x, peak=0.85):
    x = x - np.mean(x)
    fade = np.minimum(1.0, (len(x) - np.arange(len(x))) / (0.03 * SR))   # sem estalo no fim
    x = x * fade
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def stasis_start():
    out = np.zeros(int(SR * 1.5))          # cabe o sino inteiro (começa em 0,42 s e dura 1 s)
    whoosh = lowpass_sweep(rng.standard_normal(int(SR * 0.45)), 300, 6000) * np.hanning(int(SR * 0.45)) * 0.9
    place(out, whoosh, 0.0)
    for k, t in enumerate(ticks_schedule(0.05, 0.06, 1.35, 6)):          # relógio desacelerando até parar
        place(out, tick(2600 - 120 * k), t, 0.55 * (1 - 0.1 * k))
    chord = bell(880, 1.0) + 0.7 * bell(1318.5, 1.0) + 0.45 * bell(1760, 1.0, 2.2)
    place(out, chord, 0.42, 0.55)
    return normalize(reverb(out, 0.35, 0.6))


def stasis_end():
    chord = bell(880, 0.7) + 0.7 * bell(1318.5, 0.7) + 0.45 * bell(1760, 0.7, 2.2)
    rev = chord[::-1] * 0.5                                                 # sino invertido: cresce até o tempo voltar
    out = np.zeros(int(SR * 1.1))
    place(out, rev, 0.0)
    for k, t in enumerate(ticks_schedule(0.62, 0.11, 0.72, 7)):          # relógio acelerando de volta
        place(out, tick(2200 + 100 * k), t, 0.5)
    whoosh = lowpass_sweep(rng.standard_normal(int(SR * 0.3)), 5000, 400) * np.hanning(int(SR * 0.3)) * 0.5
    place(out, whoosh, 0.62)
    return normalize(reverb(out, 0.25, 0.4))


def time_dissolve():
    out = np.zeros(int(SR * 0.75))
    for k, t in enumerate(ticks_schedule(0.0, 0.07, 0.78, 14)):          # tempo acelerando muito
        place(out, tick(2600 + 160 * k, 0.025), t, 0.45)
    sparkle = np.zeros(int(SR * 0.4))
    for f in (3520, 4186, 5274, 6272):                                      # brilho agudo
        sparkle += bell(f, 0.4, 6.0) * rng.uniform(0.5, 1.0)
    place(out, sparkle, 0.30, 0.35)
    puff = lowpass_sweep(rng.standard_normal(int(SR * 0.2)), 3000, 200) * np.exp(-t_axis(0.2) * 18)
    place(out, puff, 0.30, 0.9)
    return normalize(reverb(out, 0.25, 0.3), 0.75)


def save_ogg(x, path):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.remove(wav)


if __name__ == '__main__':
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    for name, fn in (('stasis_start', stasis_start), ('stasis_end', stasis_end), ('time_dissolve', time_dissolve)):
        save_ogg(fn(), os.path.join(out_dir, name + '.ogg'))
        print('ok', name)
