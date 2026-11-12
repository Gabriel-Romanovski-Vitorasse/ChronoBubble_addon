package com.gabriel.chronobubble;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Escolha do que a Restauração do tempo traz de volta, sem depender do Minecraft (por isso dá para testar).
 * <p>
 * Os grupos (uma quebra = 1 bloco; uma explosão = todos os blocos dela) vêm do mais recente para o mais antigo,
 * como rebobinar. Cada grupo volta inteiro ou não volta. Um grupo que não cabe no que sobra do orçamento é pulado,
 * mas os seguintes (menores) ainda podem entrar.
 */
public final class RestorationPlanner {
    private RestorationPlanner() {
    }

    /**
     * @param newestFirst grupos candidatos, do mais recente para o mais antigo
     * @param size        quantos blocos o grupo tem
     * @param canRestore  checagem do mundo (lugar livre, itens ainda no chão...); só é chamada para grupos que cabem
     * @param budget      máximo de blocos (limite por uso e Time disponível)
     * @return grupos escolhidos, na mesma ordem
     */
    public static <G> List<G> pick(List<G> newestFirst, ToIntFunction<G> size, Predicate<G> canRestore, int budget) {
        List<G> chosen = new ArrayList<>();
        int left = budget;
        for (G g : newestFirst) {
            int n = size.applyAsInt(g);
            if (n <= 0 || n > left) continue;
            if (!canRestore.test(g)) continue;
            chosen.add(g);
            left -= n;
            if (left == 0) break;
        }
        return chosen;
    }

    /** Quantos blocos o jogador pode pagar: (Time - custo fixo) / custo por bloco, limitado ao máximo por uso. */
    public static int affordableBlocks(int time, int baseCost, int perBlock, int maxBlocks) {
        if (time <= baseCost) return 0;
        if (perBlock <= 0) return maxBlocks;
        return Math.max(0, Math.min(maxBlocks, (time - baseCost) / perBlock));
    }
}
