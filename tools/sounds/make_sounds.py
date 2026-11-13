"""Uso: python3 tools/sounds/make_sounds.py <pasta de saída>

Gera, do zero (síntese com NumPy, sem nenhum áudio de terceiros), os sons do Chrono Bubble em Ogg Vorbis mono.
Estilo "brilho que desliza", baseado em características medidas de referência (não em áudio copiado): muitas
parciais agudas (4-9 kHz) com tremor deslizando de frequência, um sopro de ruído filtrado por baixo e eco.
  stasis_start.ogg   o tempo para: o brilho desce (~7 -> 4 kHz) por ~1,2 s
  stasis_end.ogg     o tempo volta: o brilho sobe, crescendo, e corta no fim
  time_dissolve.ogg  projétil se desfazendo: brilho curto subindo e um "puff"
O resultado é sempre o mesmo (sementes fixas). Precisa do ffmpeg (com libvorbis) para converter de WAV para OGG.
"""
import os, sys, wave, subprocess, tempfile
import numpy as np

SR = 44100
rng = np.random.default_rng(11)        # fixo: o resultado é sempre o mesmo


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


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


def normalize(x, peak=0.85):
    x = x - np.mean(x)
    fade = np.minimum(1.0, (len(x) - np.arange(len(x))) / (0.03 * SR))   # sem estalo no fim
    x = x * fade
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def shimmer(sec, f_from, f_to, n=40, spread=0.45):
    """Brilho aéreo: muitas parciais agudas com tremor, deslizando de f_from para f_to."""
    t = t_axis(sec)
    glide = np.geomspace(1.0, f_to / f_from, len(t))
    out = np.zeros_like(t)
    for _ in range(n):
        base = f_from * rng.uniform(1 - spread, 1 + spread)
        freq = base * glide
        phase = 2 * np.pi * np.cumsum(freq) / SR
        trem = 0.55 + 0.45 * np.sin(2 * np.pi * rng.uniform(9, 23) * t + rng.uniform(0, 6.3))
        out += np.sin(phase + rng.uniform(0, 6.3)) * trem * rng.uniform(0.3, 1.0)
    return out / n

def air(sec, f_from, f_to):
    return lowpass_sweep(rng.standard_normal(int(SR * sec)), f_from, f_to) - lowpass_sweep(rng.standard_normal(int(SR * sec)), f_from * 0.25, f_to * 0.25) * 0.6

def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-9

def mix(shim, airy, air_ratio):
    """Junta brilho e sopro com o sopro em air_ratio da energia do brilho (senão o chiado cobre tudo)."""
    return shim / rms(shim) + airy / rms(airy) * air_ratio

def env(sec, attack, release_pow=1.5):
    t = t_axis(sec); a = np.minimum(1, t / attack)
    return a * (1 - t / sec) ** release_pow

def stasis_start():
    sec = 1.25
    s = mix(shimmer(sec, 7000, 3800) * env(sec, 0.03), air(sec, 9000, 2500) * env(sec, 0.02, 2.2), 0.25)
    out = np.zeros(int(SR * 1.6)); place(out, s, 0.0)
    return normalize(reverb(out, 0.3, 0.6))

def stasis_end():
    sec = 1.0
    t = t_axis(sec)
    swell = np.minimum(1, t / (sec * 0.85)) ** 2 * np.minimum(1, (sec - t) / 0.08)
    s = mix(shimmer(sec, 3600, 6800) * swell, air(sec, 2000, 9000) * swell, 0.25)
    out = np.zeros(int(SR * 1.3)); place(out, s, 0.0)
    return normalize(reverb(out, 0.25, 0.4))

def time_dissolve():
    sec = 0.55
    t = t_axis(sec)
    s = shimmer(sec, 5200, 9000, n=30) * env(sec, 0.01, 1.2)
    s = s / rms(s)
    puff = lowpass_sweep(rng.standard_normal(int(SR * 0.25)), 4000, 300) * np.exp(-t_axis(0.25) * 14)
    puff = puff / rms(puff) * 0.35
    out = np.zeros(int(SR * 0.8)); place(out, s, 0.0); place(out, puff, 0.32)
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
