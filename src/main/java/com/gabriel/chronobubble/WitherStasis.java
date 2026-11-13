package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Chrono Stasis no Wither. O Wither ignora qualquer efeito de poção ({@code WitherEntity.addEffect} sempre devolve
 * false), então o efeito do Block Clover nunca pega nele. Aqui o congelamento é feito pelo addon (IA desligada, parado
 * no ar, sem gravidade), com as regras de "chefe que resiste ao tempo":
 * <ul>
 *   <li>dura só uma fração do normal, com um máximo, e metade disso na segunda fase (abaixo de metade da vida);</li>
 *   <li>depois de cada congelamento, o Wither fica imune por um tempo (resistência ao tempo).</li>
 * </ul>
 * Vale para a esfera arremessada (normal ou sustentada) e para o Grigora. O sustentado não segura o Wither além do
 * limite: para ele, conta como um congelamento comum.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WitherStasis {
    private static final String TAG = "chrono_bubble_wither_frozen";
    private static final String TAG_GRAVITY = "chrono_bubble_wither_ng";

    private static final class Frozen {
        final WitherEntity wither;
        final long until;
        final Vector3d at;

        Frozen(WitherEntity wither, long until) {
            this.wither = wither;
            this.until = until;
            this.at = wither.position();
        }
    }

    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    /** Quando cada Wither (uuid) volta a poder ser congelado. */
    private static final Map<UUID, Long> IMMUNE_UNTIL = new HashMap<>();

    public static boolean applies(Entity e) {
        return e instanceof WitherEntity && ChronoConfig.WITHER_ENABLED.get();
    }

    /** Duração real para o Wither a partir da duração normal (fração, máximo e segunda fase). */
    static int duration(int normalTicks, boolean secondPhase) {
        double ticks = normalTicks * ChronoConfig.WITHER_FREEZE_FRACTION.get();
        ticks = Math.min(ticks, ChronoConfig.WITHER_MAX_FREEZE_TICKS.get());
        if (secondPhase && ChronoConfig.WITHER_PHASE_HALVES.get()) ticks *= 0.5D;
        return Math.max(10, (int) Math.round(ticks));
    }

    /**
     * Tenta congelar o Wither. Avisa quem lançou (pode ser null) se ele estiver resistindo ao tempo.
     *
     * @return true se congelou
     */
    public static boolean freeze(WitherEntity wither, int normalTicks, PlayerEntity caster) {
        if (!ChronoConfig.WITHER_ENABLED.get() || wither.level.isClientSide || !wither.isAlive()) return false;
        long now = wither.level.getGameTime();
        if (FROZEN.containsKey(wither.getId())) return false;                    // já está parado
        Long immune = IMMUNE_UNTIL.get(wither.getUUID());
        if (immune != null && now < immune) {
            if (caster != null) Msg.bar(caster, TextFormatting.GRAY, "wither_resists", Msg.f1((immune - now) / 20.0));
            return false;
        }
        int ticks = duration(normalTicks, wither.isPowered());
        CompoundNBT data = wither.getPersistentData();
        data.putBoolean(TAG, true);
        data.putBoolean(TAG_GRAVITY, wither.isNoGravity());
        if (!wither.isNoAi()) {
            wither.setNoAi(true);
            data.putBoolean(TAG + "_ai", true);                                    // fui eu que desliguei a IA
        }
        wither.setNoGravity(true);
        wither.setDeltaMovement(Vector3d.ZERO);
        FROZEN.put(wither.getId(), new Frozen(wither, now + ticks));
        sendBubble(wither, ticks);
        wither.level.playSound(null, wither.getX(), wither.getY(), wither.getZ(), ChronoBubbleMod.STASIS_START.get(),
                SoundCategory.HOSTILE, 1.2F, 0.6F);
        if (caster != null) Msg.bar(caster, TextFormatting.AQUA, "wither_frozen", Msg.f1(ticks / 20.0));
        return true;
    }

    private static void sendBubble(WitherEntity wither, int ticks) {
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> wither),
                new StasisPacket(wither.getId(), ticks));
    }

    /** Solta o Wither e começa a resistência ao tempo. */
    private static void release(WitherEntity wither, boolean immunity) {
        CompoundNBT data = wither.getPersistentData();
        if (data.getBoolean(TAG + "_ai")) wither.setNoAi(false);
        wither.setNoGravity(data.getBoolean(TAG_GRAVITY));
        data.remove(TAG);
        data.remove(TAG + "_ai");
        data.remove(TAG_GRAVITY);
        if (wither.level.isClientSide) return;
        sendBubble(wither, 0);
        if (immunity) {
            IMMUNE_UNTIL.put(wither.getUUID(), wither.level.getGameTime() + ChronoConfig.WITHER_IMMUNITY_SECONDS.get() * 20L);
        }
        wither.level.playSound(null, wither.getX(), wither.getY(), wither.getZ(), ChronoBubbleMod.STASIS_END.get(),
                SoundCategory.HOSTILE, 1.0F, 0.7F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FROZEN.isEmpty()) return;
        for (Iterator<Frozen> it = FROZEN.values().iterator(); it.hasNext(); ) {
            Frozen f = it.next();
            WitherEntity w = f.wither;
            if (!w.isAlive()) { it.remove(); continue; }
            if (w.level.getGameTime() >= f.until) {
                it.remove();
                release(w, true);
                TimeAging.onBossFreezeEnded(w);                             // acabou sozinho: o tempo dele avan\u00e7a
                continue;
            }
            // parado no lugar (sem IA ele não atira nem voa, mas empurrões ainda o moveriam)
            w.setDeltaMovement(Vector3d.ZERO);
            if (w.position().distanceToSqr(f.at) > 0.01D) w.teleportTo(f.at.x, f.at.y, f.at.z);
        }
    }

    /** Ver o estado de um Wither parado (quem chega perto também vê a bolha). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof WitherEntity) || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        Frozen f = FROZEN.get(event.getTarget().getId());
        if (f == null) return;
        int left = (int) Math.max(1, f.until - f.wither.level.getGameTime());
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()),
                new StasisPacket(f.wither.getId(), left));
    }

    /** Segurança: um Wither salvo congelado (jogo fechado no meio) volta solto. */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof WitherEntity)) return;
        WitherEntity w = (WitherEntity) event.getEntity();
        if (w.getPersistentData().getBoolean(TAG) && !FROZEN.containsKey(w.getId())) release(w, false);
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        for (Frozen f : FROZEN.values()) if (f.wither.isAlive()) release(f.wither, false);
        FROZEN.clear();
        IMMUNE_UNTIL.clear();
    }
}
