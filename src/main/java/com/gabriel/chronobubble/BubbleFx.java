package com.gabriel.chronobubble;

/**
 * Contas das animações da bolha (entrada, ondas dos golpes e cacos da quebra), sem depender do Minecraft (por isso
 * dá para testar). Tempos em ticks; ângulos em radianos.
 */
public final class BubbleFx {
    /** Duração da entrada (a bolha cresce passando um pouco do tamanho e volta). */
    public static final float ENTRY_TICKS = 10.0F;
    /** Quanto tempo uma onda leva para atravessar a bolha e sumir. */
    public static final float RIPPLE_TICKS = 16.0F;
    /** Quanto tempo os cacos voam até sumir. */
    public static final float SHATTER_TICKS = 18.0F;

    private BubbleFx() {
    }

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /** Curva "passa do ponto e volta": 0 em 0, ~1,08 perto de 0,6 e 1 em 1. */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.2F, c3 = c1 + 1.0F, u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    /** Clarão da entrada: forte no começo, some até o fim da entrada. */
    public static float entryFlash(float progress) {
        float k = 1.0F - clamp01(progress);
        return k * k;
    }

    /** Giro extra dos anéis na entrada (graus): entram rápido e desaceleram até o giro normal. */
    public static float entrySpin(float progress) {
        float k = 1.0F - clamp01(progress);
        return 540.0F * k * k * k;
    }

    /**
     * Força da onda num ponto da bolha.
     *
     * @param cosAngle cosseno do ângulo entre o ponto e o lugar do golpe (1 = no ponto do golpe)
     * @param age      0..1 da vida da onda
     * @return 0..~1: um anel que nasce no ponto do golpe e atravessa a bolha, mais um clarão no próprio ponto
     */
    public static float ripple(float cosAngle, float age) {
        if (age < 0.0F || age >= 1.0F) return 0.0F;
        float angle = (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, cosAngle)));
        float front = age * (float) Math.PI * 0.95F;             // a frente da onda anda do golpe até o lado oposto
        float d = (angle - front) / 0.38F;
        float fade = 1.0F - age;
        float ring = (float) Math.exp(-d * d) * fade;
        float spot = angle / 0.28F;
        float flash = (float) Math.exp(-spot * spot) * (1.0F - age) * (1.0F - age);
        return Math.min(1.2F, ring + flash);
    }

    /** Opacidade de um caco: começa cheia e some. */
    public static float shardAlpha(float age) {
        return (float) Math.pow(1.0F - clamp01(age), 1.5D);
    }

    /** Distância que um caco já andou para fora (em raios da bolha), desacelerando no ar. */
    public static float shardTravel(float ticks, float speed) {
        return speed * (1.0F - (float) Math.exp(-ticks / 8.0F)) * 8.0F;
    }

    /** Quanto um caco caiu (em blocos) depois de {@code ticks}. */
    public static float shardDrop(float ticks) {
        return 0.006F * ticks * ticks;
    }
}
