package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Desenha, em volta de qualquer entidade com o efeito block_clover:chrono_stasis
 * (usado pelo Chrono Stasis e pelo Chrono Stasis Grigora), uma esfera transl\u00facida
 * e dois an\u00e9is inclinados com numerais romanos girando.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StasisBubbleRenderer {

    // ===== Ajustes f\u00e1ceis =====
    private static final float SPHERE_ALPHA   = 0.55F;  // 0..1 transpar\u00eancia da esfera
    private static final float RING_ALPHA     = 0.90F;  // 0..1 transpar\u00eancia dos an\u00e9is
    private static final float RING_SPEED     = 2.0F;   // graus por tick de rota\u00e7\u00e3o do anel
    private static final float RING_HEIGHT    = 0.22F;  // altura (largura) da faixa, em fra\u00e7\u00e3o do raio
    private static final int   LAT_SEGMENTS   = 14;
    private static final int   LON_SEGMENTS   = 24;
    private static final int   RING_SEGMENTS  = 48;
    private static final int   FULL_BRIGHT    = 15728880;

    static final ResourceLocation SPHERE_TEX = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/sphere.png");
    static final ResourceLocation RING_TEX   = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/ring.png");

    private static Effect stasisEffect;

    private static Effect getEffect() {
        if (stasisEffect == null) {
            stasisEffect = BlockCloverBridge.stasisEffect(); // null se o mod Block Clover n\u00e3o estiver carregado
        }
        return stasisEffect;
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Effect effect = getEffect();
        // O servidor avisa o cliente (ClientStasis); hasEffect cobre o pr\u00f3prio jogador.
        boolean frozen = ClientStasis.isFrozen(entity) || (effect != null && entity.hasEffect(effect));
        if (!frozen) return;

        MatrixStack stack = event.getMatrixStack();
        IRenderTypeBuffer buffers = event.getBuffers();
        float time = entity.tickCount + event.getPartialRenderTick();

        // Raio compartilhado com a l\u00f3gica dos proj\u00e9teis (StasisField) e anima\u00e7\u00e3o de entrada/sa\u00edda
        float radius = StasisField.radius(entity) * ClientStasis.getScale(entity, event.getPartialRenderTick());
        if (radius < 0.02F) return;

        stack.pushPose();
        stack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);

        float flick = ClientStasis.flicker(entity, time);

        // Esfera (leve pulsa\u00e7\u00e3o)
        float pulse = 1.0F + 0.015F * (float) Math.sin(time * 0.15F);
        drawSphere(stack, buffers.getBuffer(RenderType.entityTranslucent(SPHERE_TEX)),
                radius * pulse, SPHERE_ALPHA * flick);

        // Anel 1: inclinado, girando num sentido
        stack.pushPose();
        stack.mulPose(Vector3f.ZP.rotationDegrees(28.0F));
        stack.mulPose(Vector3f.YP.rotationDegrees(time * RING_SPEED));
        drawRing(stack, buffers.getBuffer(RenderType.entityTranslucent(RING_TEX)), radius * 1.06F, radius * RING_HEIGHT, RING_ALPHA * flick);
        stack.popPose();

        // Anel 2: inclinado para o outro lado, girando ao contr\u00e1rio e mais devagar
        stack.pushPose();
        stack.mulPose(Vector3f.ZP.rotationDegrees(-62.0F));
        stack.mulPose(Vector3f.YP.rotationDegrees(-time * RING_SPEED * 0.6F));
        drawRing(stack, buffers.getBuffer(RenderType.entityTranslucent(RING_TEX)), radius * 1.10F, radius * RING_HEIGHT * 0.8F, RING_ALPHA * 0.85F * flick);
        stack.popPose();

        stack.popPose();
    }

    private static void vertex(IVertexBuilder b, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int alpha) {
        b.vertex(pose, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    /** Esfera por latitude/longitude, textura mapeada de forma cont\u00ednua. */
    static void drawSphere(MatrixStack stack, IVertexBuilder b, float r, float alphaF) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        int alpha = (int) (alphaF * 255.0F);

        for (int i = 0; i < LAT_SEGMENTS; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / LAT_SEGMENTS);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / LAT_SEGMENTS);
            float y0 = (float) Math.sin(lat0), c0 = (float) Math.cos(lat0);
            float y1 = (float) Math.sin(lat1), c1 = (float) Math.cos(lat1);
            float v0 = 1.0F - (float) i / LAT_SEGMENTS;
            float v1 = 1.0F - (float) (i + 1) / LAT_SEGMENTS;

            for (int j = 0; j < LON_SEGMENTS; j++) {
                double lon0 = 2 * Math.PI * j / LON_SEGMENTS;
                double lon1 = 2 * Math.PI * (j + 1) / LON_SEGMENTS;
                float x00 = (float) Math.cos(lon0), z00 = (float) Math.sin(lon0);
                float x01 = (float) Math.cos(lon1), z01 = (float) Math.sin(lon1);
                float u0 = (float) j / LON_SEGMENTS;
                float u1 = (float) (j + 1) / LON_SEGMENTS;

                vertex(b, pose, normal, r * x00 * c0, r * y0, r * z00 * c0, u0, v0, x00 * c0, y0, z00 * c0, alpha);
                vertex(b, pose, normal, r * x01 * c0, r * y0, r * z01 * c0, u1, v0, x01 * c0, y0, z01 * c0, alpha);
                vertex(b, pose, normal, r * x01 * c1, r * y1, r * z01 * c1, u1, v1, x01 * c1, y1, z01 * c1, alpha);
                vertex(b, pose, normal, r * x00 * c1, r * y1, r * z00 * c1, u0, v1, x00 * c1, y1, z00 * c1, alpha);
            }
        }
    }

    /** Faixa cil\u00edndrica (aberta) em volta do eixo Y, com os numerais repetidos ao longo da volta. */
    static void drawRing(MatrixStack stack, IVertexBuilder b, float radius, float height, float alphaF) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        int alpha = (int) (alphaF * 255.0F);
        float half = height * 0.5F;

        for (int j = 0; j < RING_SEGMENTS; j++) {
            double a0 = 2 * Math.PI * j / RING_SEGMENTS;
            double a1 = 2 * Math.PI * (j + 1) / RING_SEGMENTS;
            float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            float u0 = (float) j / RING_SEGMENTS;
            float u1 = (float) (j + 1) / RING_SEGMENTS;

            vertex(b, pose, normal, radius * x0, -half, radius * z0, u0, 1.0F, x0, 0, z0, alpha);
            vertex(b, pose, normal, radius * x1, -half, radius * z1, u1, 1.0F, x1, 0, z1, alpha);
            vertex(b, pose, normal, radius * x1,  half, radius * z1, u1, 0.0F, x1, 0, z1, alpha);
            vertex(b, pose, normal, radius * x0,  half, radius * z0, u0, 0.0F, x0, 0, z0, alpha);
        }
    }
}
