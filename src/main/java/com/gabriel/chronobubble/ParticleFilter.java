package com.gabriel.chronobubble;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particles.IParticleData;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Cliente: esconde as part\u00edculas do Chrono Stasis (e do Grigora, que usa o mesmo efeito).
 * Elas usam o mesmo tipo de part\u00edcula das outras magias de tempo, mas s\u00e3o as \u00fanicas com vida de 400 ticks,
 * ent\u00e3o \u00e9 isso que as identifica; as part\u00edculas de Time Heal, Hop, Steal e Resurrection continuam normais.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ParticleFilter {
    // ===== Ajustes f\u00e1ceis =====
    private static final int STASIS_PARTICLE_LIFE = 400;

    private static final ResourceLocation TIME_PARTICLE = BlockCloverBridge.TIME_PARTICLE_ID;

    private static boolean installed;
    private static boolean lifeFailed;
    private static Field lifeField;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (installed || !ChronoConfig.HIDE_PARTICLES.get() || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;          // espera o mundo carregar (o Block Clover j\u00e1 registrou as part\u00edculas)
        installed = true;
        try {
            install(mc.particleEngine);
        } catch (Throwable ignored) {
            // se algo mudar nas classes do jogo, as part\u00edculas simplesmente continuam aparecendo
        }
    }

    /** Troca a f\u00e1brica do tipo de part\u00edcula "block_clover:time" por uma que descarta as de vida 400. */
    @SuppressWarnings("unchecked")
    private static void install(ParticleManager manager) throws Exception {
        for (Field f : ParticleManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(f.getType())) continue;
            if (!f.getGenericType().toString().contains("IParticleFactory")) continue;
            f.setAccessible(true);
            Map<ResourceLocation, IParticleFactory<?>> factories = (Map<ResourceLocation, IParticleFactory<?>>) f.get(manager);
            final IParticleFactory<IParticleData> original = (IParticleFactory<IParticleData>) factories.get(TIME_PARTICLE);
            if (original == null) return;
            IParticleFactory<IParticleData> wrapper = (data, world, x, y, z, xSpeed, ySpeed, zSpeed) ->
                    isStasisParticle(data) ? null : original.createParticle(data, world, x, y, z, xSpeed, ySpeed, zSpeed);
            factories.put(TIME_PARTICLE, wrapper);
            return;
        }
    }

    private static boolean isStasisParticle(IParticleData data) {
        if (lifeFailed) return false;
        try {
            if (lifeField == null) {
                lifeField = data.getClass().getDeclaredField("life");
                lifeField.setAccessible(true);
            }
            return lifeField.getInt(data) == STASIS_PARTICLE_LIFE;
        } catch (Throwable t) {
            lifeFailed = true;
            return false;
        }
    }
}
