package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Lado do servidor:
 *  1) avisa os clientes quando o Chrono Stasis entra/sai de uma entidade (para a esfera aparecer);
 *  2) enquanto um mob estiver congelado, desliga a IA dele (n\u00e3o anda, n\u00e3o pula, n\u00e3o ataca, n\u00e3o atira).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StasisSync {
    private static final ResourceLocation EFFECT_ID = new ResourceLocation("block_clover", "chrono_stasis");
    /** Marca no NBT do mob: "fui eu (este addon) que desliguei a IA dele". S\u00f3 ent\u00e3o a IA \u00e9 religada. */
    private static final String TAG = "chrono_bubble_froze_ai";

    private static boolean isStasis(Effect effect) {
        return effect != null && EFFECT_ID.equals(effect.getRegistryName());
    }

    private static void send(LivingEntity entity, int duration) {
        if (entity.level.isClientSide) return;
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new StasisPacket(entity.getId(), duration));
    }

    private static void playSound(LivingEntity entity, SoundEvent sound) {
        if (entity.level.isClientSide) return;
        entity.level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private static void freezeAi(LivingEntity entity) {
        if (entity.level.isClientSide || !(entity instanceof MobEntity)) return;
        MobEntity mob = (MobEntity) entity;
        if (!mob.isNoAi()) {                       // n\u00e3o mexe em mobs que j\u00e1 tinham IA desligada
            mob.setNoAi(true);
            mob.getPersistentData().putBoolean(TAG, true);
        }
    }

    private static void unfreezeAi(LivingEntity entity) {
        if (entity.level.isClientSide || !(entity instanceof MobEntity)) return;
        MobEntity mob = (MobEntity) entity;
        if (mob.getPersistentData().getBoolean(TAG)) {
            mob.setNoAi(false);
            mob.getPersistentData().remove(TAG);
        }
    }

    @SubscribeEvent
    public static void onAdded(PotionEvent.PotionAddedEvent event) {
        EffectInstance inst = event.getPotionEffect();
        if (inst != null && isStasis(inst.getEffect())) {
            LivingEntity entity = event.getEntityLiving();
            send(entity, inst.getDuration());
            freezeAi(entity);
            if (!entity.level.isClientSide) {
                StasisField.FROZEN.put(entity.getId(), entity);
                if (event.getOldPotionEffect() == null) playSound(entity, ChronoBubbleMod.STASIS_START.get());
            }
        }
    }

    @SubscribeEvent
    public static void onExpired(PotionEvent.PotionExpiryEvent event) {
        EffectInstance inst = event.getPotionEffect();
        if (inst != null && isStasis(inst.getEffect())) {
            send(event.getEntityLiving(), 0);
            unfreezeAi(event.getEntityLiving());
            StasisField.FROZEN.remove(event.getEntityLiving().getId());
            playSound(event.getEntityLiving(), ChronoBubbleMod.STASIS_END.get());
        }
    }

    @SubscribeEvent
    public static void onRemoved(PotionEvent.PotionRemoveEvent event) {
        if (isStasis(event.getPotion())) {
            send(event.getEntityLiving(), 0);
            unfreezeAi(event.getEntityLiving());
            StasisField.FROZEN.remove(event.getEntityLiving().getId());
        }
    }

    /**
     * Quem passa a ver uma entidade j\u00e1 congelada (chegou perto, trocou de dimens\u00e3o, renasceu) recebe o estado dela.
     * Antes, a esfera s\u00f3 aparecia para quem j\u00e1 estava por perto no momento em que o efeito come\u00e7ou.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity) || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        Effect effect = ForgeRegistries.POTIONS.getValue(EFFECT_ID);
        if (effect == null) return;
        LivingEntity target = (LivingEntity) event.getTarget();
        EffectInstance inst = target.getEffect(effect);
        if (inst == null) return;
        ServerPlayerEntity viewer = (ServerPlayerEntity) event.getPlayer();
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new StasisPacket(target.getId(), inst.getDuration()));
    }

    /** Seguran\u00e7a: se um mob marcado volta ao mundo (recarregou o jogo) sem o efeito, religa a IA dele. */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) event.getEntity();
        Effect effect = ForgeRegistries.POTIONS.getValue(EFFECT_ID);
        boolean frozen = effect != null && living.hasEffect(effect);
        if (frozen) StasisField.FROZEN.put(living.getId(), living);
        if (living instanceof MobEntity && !frozen && living.getPersistentData().getBoolean(TAG)) {
            unfreezeAi(living);
        }
    }
}
