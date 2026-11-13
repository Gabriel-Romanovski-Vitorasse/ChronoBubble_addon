package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Cliente: tom apodrecido no próprio modelo de quem está apodrecendo pelo soco do Time Steal.
 * <p>
 * Uma camada a mais em cada renderer de mob/jogador desenha o modelo de novo por cima, com a textura da entidade
 * tingida de marrom-acinzentado e semitransparente: a entidade escurece aos poucos durante o apodrecimento, dá um
 * "pulso" a cada roubo de Time, e volta ao normal no fim. A camada é acrescentada na primeira vez que cada renderer
 * desenha algo (vale para mobs de outros mods também).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRot {
    /** Cor do apodrecimento e opacidade máxima da camada. */
    private static final float[] ROT_RGB = { 0.50F, 0.50F, 0.16F };     // tira o azul: tudo puxa para oliva/podre
    private static final float MAX_ALPHA = 0.75F;
    /** Ticks para o tom sumir depois do fim. */
    private static final float FADE_OUT = 12.0F;

    /** id da entidade -> {tick de início, tick de fim} */
    private static final Map<Integer, long[]> TIMES = new HashMap<>();
    /** Renderers que já ganharam a camada (sem segurar referências). */
    private static final Set<LivingRenderer<?, ?>> PATCHED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void set(int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ticks <= 0) return;
        long now = mc.level.getGameTime();
        TIMES.put(entityId, new long[] { now, now + ticks });
    }

    /** Intensidade do tom (0 = nada) para esta entidade agora. */
    static float intensity(Entity entity, float partial) {
        long[] t = TIMES.get(entity.getId());
        if (t == null) return 0.0F;
        float now = entity.level.getGameTime() + partial;
        float total = Math.max(1.0F, t[1] - t[0]);
        float k = (now - t[0]) / total;
        if (k >= 1.0F) {
            float out = 1.0F - (now - t[1]) / FADE_OUT;
            if (out <= 0.0F) {
                TIMES.remove(entity.getId());
                return 0.0F;
            }
            return out;
        }
        float grow = Math.min(1.0F, 0.35F + 0.65F * k);                     // escurece ao longo do apodrecimento
        float since = (now - t[0]) % TimeRot.PULSE_TICKS;                    // "pulso" a cada roubo de Time
        float pulse = 0.25F * Math.max(0.0F, 1.0F - since / 4.0F);
        return Math.min(1.0F, grow * (0.8F + pulse));
    }

    /** Acrescenta a camada em cada renderer na primeira vez que ele desenha (antes de percorrer as camadas). */
    @SubscribeEvent
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingRenderer renderer = event.getRenderer();
        if (PATCHED.add(renderer)) renderer.addLayer(new RotLayer(renderer));
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        TIMES.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        TIMES.clear();
    }

    /** O modelo de novo, com a textura da entidade tingida de apodrecido e semitransparente. */
    private static final class RotLayer<T extends LivingEntity, M extends EntityModel<T>> extends LayerRenderer<T, M> {
        RotLayer(IEntityRenderer<T, M> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack stack, IRenderTypeBuffer buffers, int light, T entity, float limbSwing,
                           float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!ChronoConfig.ROT_TINT.get()) return;
            float k = intensity(entity, partialTicks);
            if (k <= 0.01F || entity.isInvisible()) return;
            IVertexBuilder vb = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
            getParentModel().renderToBuffer(stack, vb, light, LivingRenderer.getOverlayCoords(entity, 0.0F),
                    ROT_RGB[0], ROT_RGB[1], ROT_RGB[2], MAX_ALPHA * k);
        }
    }
}
