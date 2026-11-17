package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Renascimento" do Julius: quem usa magia de Tempo e morre com Time guardado na Swallowtail volta com o grim\u00f3rio de
 * uma p\u00e1gina. Sem Time na Swallowtail n\u00e3o h\u00e1 renascimento: a morte \u00e9 uma morte comum.
 * Ao renascer, tudo zera: o Time do jogador e o Time de todas as Swallowtails que ele carregava.
 * Enquanto o Tempo estiver abaixo de UNLOCK_TIME: o Tempo s\u00f3 sobe roubando (sem regenera\u00e7\u00e3o passiva e sem sacar
 * da Swallowtail) e Chrono Stasis / Chrono Stasis Grigora ficam bloqueados. Ao chegar l\u00e1, tudo \u00e9 devolvido.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RebirthHandler {
    private static final String TAG_REBORN = "chrono_bubble_reborn";
    private static final String TAG_PENDING = "chrono_bubble_pending";
    private static final String TAG_REMOVED = "chrono_bubble_removed";
    /** Quanto Time a Swallowtail gastou para trazer o jogador de volta (s\u00f3 para a mensagem ao renascer). */
    private static final String TAG_RESERVE = "chrono_bubble_reserve";
    /** P\u00e1ginas que o grim\u00f3rio j\u00e1 recuperou enquanto renascido (1, 4, 16 ou 64). */
    private static final String TAG_PAGES = "chrono_bubble_pages";
    /** O Chrono Stasis normal j\u00e1 voltou durante o renascimento (sem matar no fim; o Grigora segue bloqueado). */
    private static final String TAG_STASIS_BACK = "chrono_bubble_stasis_back";
    /** Slot da combat bar onde cada habilidade bloqueada estava ("stasis:3,grigora:5"), para voltar ao mesmo lugar. */
    private static final String TAG_SLOTS = "chrono_bubble_slots";

    private static final Map<UUID, Integer> LAST_TIME = new HashMap<>();
    /** \u00daltima carga lida da Time Ressurection, para notar quando a pr\u00f3pria passiva do Block Clover age. */
    private static final Map<UUID, Double> LAST_STRAIN = new HashMap<>();

    /** O jogador est\u00e1 com o grim\u00f3rio de uma p\u00e1gina (renascido e ainda sem os poderes de volta). */
    public static boolean isReborn(PlayerEntity player) {
        return player.getPersistentData().getBoolean(TAG_REBORN);
    }

    /**
     * Um ganho de Time leg\u00edtimo (roubo feito por este addon) acabou de acontecer: aceita o valor atual como base,
     * para o bloqueio da regenera\u00e7\u00e3o passiva n\u00e3o desfazer um roubo pequeno (+1 ou +2).
     */
    public static void acceptTime(PlayerEntity player) {
        if (!isReborn(player)) return;
        int time = BlockCloverBridge.getTime(player);
        if (time >= 0) LAST_TIME.put(player.getUUID(), time);
    }

    // ---------- Morte e respawn ----------

    /**
     * Golpe fatal com a passiva Time Ressurection do Block Clover pronta: ela age primeiro, com as regras dela
     * (carga cheia, mais de 300 de Time, consome todo o Time, cura a vida toda), e a Swallowtail fica intacta.
     * <p>
     * O Block Clover s\u00f3 checa a passiva no tick, quando a vida j\u00e1 est\u00e1 abaixo de 1 cora\u00e7\u00e3o, ent\u00e3o um golpe que
     * mata de uma vez passava direto por ela e gastava a Swallowtail. HIGHEST: se ela salvar, o evento \u00e9 cancelado
     * antes de qualquer outro tratamento de morte (zona, sustentado, etc. continuam ligados).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFatalBlow(LivingDeathEvent event) {
        if (!ChronoConfig.REBIRTH_ENABLED.get() || !ChronoConfig.REBIRTH_RESSURECTION_FIRST.get()) return;
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) return;
        if (event.getSource().isBypassInvul()) return;           // vazio e /kill: nem a passiva salva
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();
        if (!BlockCloverBridge.isRessurectionReady(player)) return;

        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        BlockCloverBridge.setTime(player, 0);
        BlockCloverBridge.resetRessurection(player);
        BlockCloverBridge.syncToClient(player);
        if (player.level instanceof ServerWorld) {
            ((ServerWorld) player.level).sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.08D);
        }
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 1.0F, 0.5F);
        LAST_STRAIN.put(player.getUUID(), 0.0D);                 // a detec\u00e7\u00e3o no tick n\u00e3o conta este de novo
        if (ChronoConfig.REBIRTH_RESSURECTION_REBORN.get()) {
            enterReborn(player);
            Msg.chat(player, TextFormatting.AQUA, "rebirth_ressurection", ChronoConfig.REBIRTH_UNLOCK_TIME.get());
        } else {
            Msg.chat(player, TextFormatting.AQUA, "ressurection_saved");
        }
    }

    /**
     * A passiva do pr\u00f3prio Block Clover salvou o jogador (vida baixa, sem golpe fatal): a carga, que estava cheia,
     * foi zerada junto com o Time. Checado a cada 10 ticks.
     */
    private static void watchRessurection(ServerPlayerEntity player) {
        UUID id = player.getUUID();
        double strain = BlockCloverBridge.ressurectionStrain(player);
        Double last = LAST_STRAIN.put(id, strain);
        if (strain < 0.0D || last == null) return;
        if (last >= BlockCloverBridge.RESSURECTION_FULL_STRAIN && strain < BlockCloverBridge.RESSURECTION_FULL_STRAIN
                && BlockCloverBridge.getTime(player) == 0 && player.isAlive()) {
            enterReborn(player);
            Msg.chat(player, TextFormatting.AQUA, "rebirth_ressurection", ChronoConfig.REBIRTH_UNLOCK_TIME.get());
        }
    }

    /** LOWEST e sem receber eventos cancelados: se outro mod impedir a morte, a Swallowtail n\u00e3o \u00e9 esvaziada. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!ChronoConfig.REBIRTH_ENABLED.get() || !(event.getEntityLiving() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();
        if (!BlockCloverBridge.isTimeUser(player)) return;
        // a Swallowtail gasta TODO o Time guardado agora, antes de o invent\u00e1rio cair no ch\u00e3o
        int used = SwallowtailItem.drainAll(player);
        if (used <= 0) return;                         // nada guardado: sem renascimento
        player.getPersistentData().putBoolean(TAG_PENDING, true);
        player.getPersistentData().putInt(TAG_RESERVE, used);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundNBT from = event.getOriginal().getPersistentData();
        CompoundNBT to = event.getPlayer().getPersistentData();
        to.putBoolean(TAG_REBORN, from.getBoolean(TAG_REBORN));
        to.putBoolean(TAG_PENDING, from.getBoolean(TAG_PENDING));
        to.putString(TAG_REMOVED, from.getString(TAG_REMOVED));
        to.putInt(TAG_RESERVE, from.getInt(TAG_RESERVE));
        to.putInt(TAG_PAGES, from.getInt(TAG_PAGES));
        to.putBoolean(TAG_STASIS_BACK, from.getBoolean(TAG_STASIS_BACK));
        to.putString(TAG_SLOTS, from.getString(TAG_SLOTS));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        broadcastState(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        broadcastState(event.getPlayer());
        // c\u00f3pias velhas do Chrono Stasis/Grigora presas na combat bar (deixadas por vers\u00f5es anteriores do addon)
        if (BlockCloverBridge.repairEquipped(event.getPlayer())) BlockCloverBridge.syncToClient(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TIME.remove(event.getPlayer().getUUID());
        LAST_STRAIN.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        broadcastState(event.getPlayer());
    }

    /** Quando um jogador passa a ver outro, avisa se o outro est\u00e1 com o grim\u00f3rio de uma p\u00e1gina. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity) || !(event.getTarget() instanceof PlayerEntity)) return;
        PlayerEntity target = (PlayerEntity) event.getTarget();
        if (target.getPersistentData().getBoolean(TAG_REBORN)) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()),
                    new RebirthPacket(target.getId(), pagesOf(target)));
        }
    }

    // ---------- Tick do servidor ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ChronoConfig.REBIRTH_ENABLED.get() || event.phase != TickEvent.Phase.END || event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        CompoundNBT data = player.getPersistentData();

        if (data.getBoolean(TAG_PENDING) && player.isAlive() && player.tickCount > 20) {
            applyRebirth(player);
        }
        if (player.tickCount % 10 == 0 && ChronoConfig.REBIRTH_RESSURECTION_REBORN.get() && !data.getBoolean(TAG_REBORN)) {
            watchRessurection(player);
        }
        if (!data.getBoolean(TAG_REBORN)) return;

        int time = BlockCloverBridge.getTime(player);
        if (time < 0) return;
        if (time >= ChronoConfig.REBIRTH_UNLOCK_TIME.get()) {
            unlock(player);
            return;
        }
        growPages(player, time);
        if (!data.getBoolean(TAG_STASIS_BACK) && time >= ChronoConfig.REBIRTH_STASIS_TIME.get()) {
            giveStasisBack(player);
        }

        // Bloqueia a regenera\u00e7\u00e3o passiva (+1/s). Compara com o tick anterior em TODO tick: antes s\u00f3 olhava quando
        // tickCount % 20 == 0, e a regenera\u00e7\u00e3o do Block Clover que ca\u00eda em outro tick passava sem ser vista.
        // Roubos do Block Clover (+10) s\u00e3o maiores e continuam valendo; os roubos deste addon chamam acceptTime().
        UUID id = player.getUUID();
        Integer last = LAST_TIME.get(id);
        if (ChronoConfig.REBIRTH_BLOCK_REGEN.get() && last != null) {
            int delta = time - last;
            if (delta >= 1 && delta <= 2) {
                BlockCloverBridge.setTime(player, last);
                BlockCloverBridge.syncToClient(player);
                time = last;
            }
        }
        LAST_TIME.put(id, time);

        // Se o jogador reaprender os poderes bloqueados (menus do mod), tira de novo
        if (player.tickCount % 20 == 10) {
            lock(player, data.getBoolean(TAG_STASIS_BACK));
        }
    }

    // ---------- Estados ----------

    private static void applyRebirth(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.putBoolean(TAG_PENDING, false);
        int used = data.getInt(TAG_RESERVE);
        data.putInt(TAG_RESERVE, 0);

        // renasceu: tudo zera (Time do jogador e qualquer Swallowtail que ainda esteja com ele, ex.: keepInventory)
        SwallowtailItem.drainAll(player);
        enterReborn(player);
        Msg.chat(player, TextFormatting.AQUA, "rebirth", used, ChronoConfig.REBIRTH_UNLOCK_TIME.get());
        ChronoAdvancements.grant(player, ChronoAdvancements.REBIRTH);
    }

    /** Grim\u00f3rio de uma p\u00e1gina: Time zerado e Chrono Stasis bloqueado at\u00e9 roubar Time de novo. */
    private static void enterReborn(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.putBoolean(TAG_REBORN, true);
        data.putInt(TAG_PAGES, 1);
        data.putBoolean(TAG_STASIS_BACK, false);
        BlockCloverBridge.setTime(player, 0);
        LAST_TIME.put(player.getUUID(), 0);
        lock(player, false);
        BlockCloverBridge.syncToClient(player);
        broadcastState(player);
    }

    /** P\u00e1ginas do grim\u00f3rio de um jogador renascido (ou {@link GrimoireStages#FULL} se ele n\u00e3o est\u00e1). */
    private static int pagesOf(PlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        if (!data.getBoolean(TAG_REBORN)) return GrimoireStages.FULL;
        return Math.max(1, data.getInt(TAG_PAGES));
    }

    /** O Time subiu de fase: o grim\u00f3rio ganha p\u00e1ginas (e nunca perde enquanto o jogador continua renascido). */
    private static void growPages(ServerPlayerEntity player, int time) {
        int now = pagesOf(player);
        int next = GrimoireStages.keepMax(now, GrimoireStages.pagesFor(time, ChronoConfig.REBIRTH_PAGES_4.get(),
                ChronoConfig.REBIRTH_PAGES_16.get(), ChronoConfig.REBIRTH_PAGES_64.get(), Integer.MAX_VALUE));
        if (next == now) return;
        player.getPersistentData().putInt(TAG_PAGES, next);
        broadcastState(player);
        player.level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 0.6F, 1.2F + 0.1F * Integer.numberOfTrailingZeros(next));
        Msg.chat(player, TextFormatting.AQUA, "rebirth_pages", next, ChronoConfig.REBIRTH_UNLOCK_TIME.get());
    }

    /**
     * Chrono Stasis normal de volta no meio do renascimento (s\u00f3 se o jogador o tinha antes). Enquanto ele estiver
     * renascido, o tempo avan\u00e7ado no fim do Stasis n\u00e3o mata ({@link TimeAging}). O Grigora espera o grim\u00f3rio completo.
     */
    private static void giveStasisBack(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.putBoolean(TAG_STASIS_BACK, true);
        String stored = data.getString(TAG_REMOVED);
        if (!Arrays.asList(stored.split(",")).contains(BlockCloverBridge.KEY_STASIS)) return;   // n\u00e3o tinha
        BlockCloverBridge.restoreAbilities(player, Arrays.asList(BlockCloverBridge.KEY_STASIS), parseSlots(data.getString(TAG_SLOTS)));
        BlockCloverBridge.syncToClient(player);
        Msg.chat(player, TextFormatting.AQUA, "rebirth_stasis_back", ChronoConfig.REBIRTH_UNLOCK_TIME.get());
    }

    private static void unlock(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        String stored = data.getString(TAG_REMOVED);
        List<String> keys = stored.isEmpty() ? new ArrayList<String>() : Arrays.asList(stored.split(","));
        BlockCloverBridge.restoreAbilities(player, keys, parseSlots(data.getString(TAG_SLOTS)));
        data.putString(TAG_SLOTS, "");
        data.putBoolean(TAG_REBORN, false);
        data.putInt(TAG_PAGES, 0);
        data.putBoolean(TAG_STASIS_BACK, false);
        data.putString(TAG_REMOVED, "");
        LAST_TIME.remove(player.getUUID());
        BlockCloverBridge.syncToClient(player);
        broadcastState(player);
        player.level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        Msg.bar(player, TextFormatting.AQUA, "rebirth_unlocked");
    }

    /** Bloqueia as habilidades e guarda em que slot da combat bar cada uma estava. */
    private static void lock(ServerPlayerEntity player, boolean keepStasis) {
        CompoundNBT data = player.getPersistentData();
        Map<String, Integer> slots = parseSlots(data.getString(TAG_SLOTS));
        int before = slots.size();
        List<String> removed = BlockCloverBridge.lockStasisAbilities(player, keepStasis, slots);
        addRemoved(data, removed);
        if (slots.size() != before) data.putString(TAG_SLOTS, formatSlots(slots));
        if (!removed.isEmpty() || slots.size() != before) BlockCloverBridge.syncToClient(player);
    }

    /** "stasis:3,grigora:5" para um mapa (entradas inv\u00e1lidas s\u00e3o ignoradas). */
    static Map<String, Integer> parseSlots(String stored) {
        Map<String, Integer> slots = new java.util.LinkedHashMap<>();
        if (stored == null || stored.isEmpty()) return slots;
        for (String part : stored.split(",")) {
            int sep = part.indexOf(':');
            if (sep <= 0) continue;
            try {
                slots.put(part.substring(0, sep), Integer.parseInt(part.substring(sep + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }

    static String formatSlots(Map<String, Integer> slots) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : slots.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append(':').append(e.getValue());
        }
        return b.toString();
    }

    private static void addRemoved(CompoundNBT data, List<String> newlyRemoved) {
        if (newlyRemoved.isEmpty()) return;
        List<String> all = new ArrayList<>();
        String stored = data.getString(TAG_REMOVED);
        if (!stored.isEmpty()) all.addAll(Arrays.asList(stored.split(",")));
        for (String k : newlyRemoved) if (!all.contains(k)) all.add(k);
        data.putString(TAG_REMOVED, String.join(",", all));
    }

    /** Avisa o pr\u00f3prio jogador e quem o v\u00ea sobre o estado atual (grim\u00f3rio de uma p\u00e1gina ou normal). */
    private static void broadcastState(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return;
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new RebirthPacket(player.getId(), pagesOf(player)));
    }
}
