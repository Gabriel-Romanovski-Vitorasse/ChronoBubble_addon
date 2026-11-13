package com.gabriel.chronobubble;

/**
 * Conta do "tempo avançado" no fim do Chrono Stasis, sem depender do Minecraft (por isso dá para testar).
 * <p>
 * Quem congelou paga Time por ponto de vida do alvo. Se o Time cobre a vida toda (mais a absorção), o alvo morre;
 * se não, leva o dano (em pontos inteiros) que o Time pagou. Chefes levam no máximo uma fração da vida máxima e
 * nunca morrem por isso (ficam com pelo menos 1 de vida).
 */
public final class AgingRule {
    private AgingRule() {
    }

    /** Resultado: dano a causar (ou morte), e quanto Time cobrar. */
    public static final class Result {
        public final boolean kill;
        public final float damage;
        public final int timeCost;

        Result(boolean kill, float damage, int timeCost) {
            this.kill = kill;
            this.damage = damage;
            this.timeCost = timeCost;
        }

        @Override
        public String toString() {
            return (kill ? "kill" : "damage " + damage) + " cost " + timeCost;
        }
    }

    public static final Result NOTHING = new Result(false, 0.0F, 0);

    /**
     * @param health       vida atual do alvo (com absorção)
     * @param maxHealth    vida máxima do alvo
     * @param time         Time de quem congelou
     * @param perHp        Time por ponto de vida
     * @param boss         o alvo é chefe (Wither, Ender Dragon)
     * @param bossFraction dano máximo em chefes, em fração da vida máxima
     */
    public static Result resolve(float health, float maxHealth, int time, float perHp, boolean boss, float bossFraction) {
        if (health <= 0.0F || time <= 0 || perHp <= 0.0F) return NOTHING;
        int affordable = (int) Math.floor(time / perHp);                       // pontos de vida que o Time paga
        if (!boss && affordable >= health) {
            return new Result(true, health, (int) Math.ceil(health * perHp));
        }
        float damage = affordable;
        if (boss) {
            damage = Math.min(damage, (float) Math.floor(maxHealth * bossFraction));
            damage = Math.min(damage, health - 1.0F);                          // chefe nunca morre pelo tempo avançado
        } else {
            damage = Math.min(damage, health);
        }
        if (damage < 1.0F) return NOTHING;
        return new Result(false, damage, (int) Math.ceil(damage * perHp));
    }
}
