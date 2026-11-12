package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Desenha a bolha do Chrono Stasis: uma esfera transl\u00facida com efeito "bolha" (fresnel: borda azul, centro quase
 * transparente, brilho suave) e uma faixa fina de numerais romanos, pouco inclinada, girando devagar.
 * <p>
 * {@link #drawBubble} \u00e9 usado por todos: mobs congelados (aqui), zona e proj\u00e9teis parados ({@link ClientZone}),
 * bolha fantasma do Time Hop ({@link ClientHopGhost}) e a esfera arremessada ({@link StasisOrbRenderer}).
 * A cor e a transpar\u00eancia da esfera s\u00e3o calculadas por v\u00e9rtice a partir do \u00e2ngulo de vis\u00e3o, ent\u00e3o ela fica igual
 * de qualquer lado; a textura da esfera \u00e9 branca e lisa. A textura da faixa \u00e9 gerada por
 * {@code tools/textures/make_ring.py}.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StasisBubbleRenderer {

    // ===== Ajustes f\u00e1ceis =====
    /** Opacidade m\u00e1xima da esfera (na borda). */
    private static final float SPHERE_ALPHA = 0.80F;
    /** Opacidade da esfera no centro (de frente para quem olha), em fra\u00e7\u00e3o da borda. */
    private static final float CENTER_ALPHA = 0.10F;
    /** Quanto mais alto, mais fina a borda azul. */
    private static final float RIM_POWER = 1.8F;
    private static final float RING_ALPHA = 0.90F;
    /**
     * Inclina\u00e7\u00e3o da faixa (graus): na dire\u00e7\u00e3o de quem olha (a faixa aparece como uma elipse aberta, como no
     * anime, em vez de uma linha vista de lado) e de lado (a faixa fica um pouco torta). Velocidade de giro em graus/tick.
     */
    private static final float RING_TILT_TOWARD = 22.0F;
    private static final float RING_TILT_SIDE = 12.0F;
    /**
     * Varia\u00e7\u00e3o por bolha (como no anime, cada uma tem a faixa num \u00e2ngulo diferente): sorteada a partir do id da
     * entidade, ent\u00e3o \u00e9 fixa para cada mob e n\u00e3o fica pulando. Faixas em graus em volta dos valores acima.
     */
    private static final float TOWARD_VARIATION = 14.0F;
    private static final float SIDE_VARIATION = 28.0F;
    private static final float RING_SPEED = 0.8F;
    /** Largura da faixa dos mobs congelados, em fra\u00e7\u00e3o do raio. */
    private static final float MOB_BAND = 0.18F;
    /** Propor\u00e7\u00e3o largura/altura de uma repeti\u00e7\u00e3o da textura da faixa (ring.png: 1024 x 64). */
    private static final float RING_TEX_ASPECT = 16.0F;
    private static final int LAT_SEGMENTS = 18;
    private static final int LON_SEGMENTS = 32;
    private static final int RING_SEGMENTS = 64;
    private static final int FULL_BRIGHT = 15728880;

    /** Cores da esfera: centro (quase branco) e borda (azul). */
    private static final float[] CENTER_RGB = { 0.84F, 0.93F, 1.00F };
    private static final float[] EDGE_RGB = { 0.30F, 0.60F, 1.00F };
    /** Dire\u00e7\u00e3o do brilho, no espa\u00e7o da c\u00e2mera (de cima, da esquerda, um pouco para quem olha). */
    private static final Vector3f HIGHLIGHT_DIR = normalized(-0.45F, 0.65F, 0.6F);

    static final ResourceLocation SPHERE_TEX = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/sphere.png");
    static final ResourceLocation RING_TEX = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/ring.png");
    /** Mesmas inst\u00e2ncias para todos (o buffer agrupa por tipo; quem chama endBatch usa estas). */
    static final RenderType SPHERE_TYPE = RenderType.entityTranslucent(SPHERE_TEX);
    static final RenderType RING_TYPE = RenderType.entityTranslucent(RING_TEX);

    private static Effect stasisEffect;

    private static Effect getEffect() {
        if (stasisEffect == null) {
            stasisEffect = BlockCloverBridge.stasisEffect(); // null se o mod Block Clover n\u00e3o estiver carregado
        }
        return stasisEffect;
    }

    private static Vector3f normalized(float x, float y, float z) {
        Vector3f v = new Vector3f(x, y, z);
        v.normalize();
        return v;
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Effect effect = getEffect();
        // O servidor avisa o cliente (ClientStasis); hasEffect cobre o pr\u00f3prio jogador.
        boolean frozen = ClientStasis.isFrozen(entity) || (effect != null && entity.hasEffect(effect));
        if (!frozen) return;

        MatrixStack stack = event.getMatrixStack();
        float time = entity.tickCount + event.getPartialRenderTick();

        // Raio compartilhado com a l\u00f3gica dos proj\u00e9teis (StasisField) e anima\u00e7\u00e3o de entrada/sa\u00edda
        float radius = StasisField.radius(entity) * ClientStasis.getScale(entity, event.getPartialRenderTick());
        if (radius < 0.02F) return;

        stack.pushPose();
        stack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        float pulse = 1.0F + 0.015F * (float) Math.sin(time * 0.15F);       // leve pulsa\u00e7\u00e3o
        drawBubble(stack, event.getBuffers(), radius * pulse, ClientStasis.flicker(entity, time), time, MOB_BAND, entity.getId());
        stack.popPose();
    }

    /**
     * Esfera + faixa de numerais (e o segundo anel, se ligado na config), centrada na origem da pilha.
     *
     * @param fade      0..1, multiplica a opacidade de tudo (anima\u00e7\u00f5es, piscar do tempo inst\u00e1vel)
     * @param time      ticks, para o giro da faixa
     * @param bandRatio largura da faixa em fra\u00e7\u00e3o do raio (esferas pequenas usam faixa proporcionalmente mais larga)
     * @param seed      n\u00famero fixo da bolha (ex.: id da entidade): escolhe a inclina\u00e7\u00e3o, o sentido e a fase da faixa
     */
    public static void drawBubble(MatrixStack stack, IRenderTypeBuffer buffers, float radius, float fade, float time,
                                  float bandRatio, int seed) {
        float toward = RING_TILT_TOWARD + TOWARD_VARIATION * signedNoise(seed, 1);
        float side = RING_TILT_SIDE + SIDE_VARIATION * signedNoise(seed, 2);
        float spin = (signedNoise(seed, 3) < 0.0F ? -1.0F : 1.0F) * time * RING_SPEED + 360.0F * signedNoise(seed, 4);

        drawSphere(stack, buffers.getBuffer(SPHERE_TYPE), radius, SPHERE_ALPHA * fade);

        // vira a faixa para quem olha (s\u00f3 em volta do eixo vertical): +Z local passa a apontar para a c\u00e2mera
        float yaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        stack.pushPose();
        stack.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));

        stack.pushPose();
        stack.mulPose(Vector3f.XP.rotationDegrees(toward));                 // lado perto de quem olha desce
        stack.mulPose(Vector3f.ZP.rotationDegrees(side));
        stack.mulPose(Vector3f.YP.rotationDegrees(spin));
        drawRing(stack, buffers.getBuffer(RING_TYPE), radius * 1.04F, radius * bandRatio, RING_ALPHA * fade);
        stack.popPose();

        if (ChronoConfig.SECOND_RING.get()) {
            stack.pushPose();
            stack.mulPose(Vector3f.XP.rotationDegrees(-toward));
            stack.mulPose(Vector3f.ZP.rotationDegrees(side - 65.0F));
            stack.mulPose(Vector3f.YP.rotationDegrees(-spin * 0.6F));
            drawRing(stack, buffers.getBuffer(RING_TYPE), radius * 1.08F, radius * bandRatio * 0.75F, RING_ALPHA * 0.8F * fade);
            stack.popPose();
        }
        stack.popPose();
    }

    /** N\u00famero "aleat\u00f3rio" em [-1, 1], sempre o mesmo para o mesmo (seed, canal). Sem criar objetos a cada quadro. */
    static float signedNoise(int seed, int channel) {
        int h = seed * 0x9E3779B1 + channel * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x7FFFFF - 1.0F;
    }

    /**
     * Quanto uma superf\u00edcie est\u00e1 de frente para quem olha: 1 = de frente, 0 = de lado (silhueta). Recebe a posi\u00e7\u00e3o e a
     * normal no espa\u00e7o local da pilha; a pose j\u00e1 inclui a c\u00e2mera, que fica na origem. Usado pela esfera e pela
     * aura do grim\u00f3rio.
     */
    static float facing(Matrix4f pose, Matrix3f normalMat, float x, float y, float z, float nx, float ny, float nz) {
        Vector4f p = new Vector4f(x, y, z, 1.0F);
        p.transform(pose);
        Vector3f n = new Vector3f(nx, ny, nz);
        n.transform(normalMat);
        n.normalize();
        float dist = (float) Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z());
        if (dist < 1.0E-4F) return 1.0F;
        return Math.min(1.0F, Math.abs(n.x() * p.x() + n.y() * p.y() + n.z() * p.z()) / dist);
    }

    /**
     * Esfera com efeito fresnel. Para cada v\u00e9rtice, compara a normal com a dire\u00e7\u00e3o de quem olha: de frente, quase
     * transparente e clara; de lado (na borda), azul e mais opaca. Um brilho suave fixo na c\u00e2mera completa o ar de bolha.
     */
    static void drawSphere(MatrixStack stack, IVertexBuilder b, float r, float alphaF) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();

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

                bubbleVertex(b, pose, normal, r, x00 * c0, y0, z00 * c0, u0, v0, alphaF);
                bubbleVertex(b, pose, normal, r, x01 * c0, y0, z01 * c0, u1, v0, alphaF);
                bubbleVertex(b, pose, normal, r, x01 * c1, y1, z01 * c1, u1, v1, alphaF);
                bubbleVertex(b, pose, normal, r, x00 * c1, y1, z00 * c1, u0, v1, alphaF);
            }
        }
    }

    /** V\u00e9rtice da esfera de raio {@code r} na dire\u00e7\u00e3o unit\u00e1ria (nx, ny, nz), com cor e opacidade do fresnel. */
    private static void bubbleVertex(IVertexBuilder b, Matrix4f pose, Matrix3f normalMat, float r,
                                     float nx, float ny, float nz, float u, float v, float alphaF) {
        float rim = (float) Math.pow(1.0F - facing(pose, normalMat, r * nx, r * ny, r * nz, nx, ny, nz), RIM_POWER);
        Vector3f n = new Vector3f(nx, ny, nz);
        n.transform(normalMat);
        n.normalize();
        float spec = (float) Math.pow(Math.max(0.0F, n.dot(HIGHLIGHT_DIR)), 24.0F) * 0.7F;

        float red = lerp(CENTER_RGB[0], EDGE_RGB[0], rim) + spec;
        float green = lerp(CENTER_RGB[1], EDGE_RGB[1], rim) + spec;
        float blue = lerp(CENTER_RGB[2], EDGE_RGB[2], rim) + spec;
        float alpha = alphaF * (CENTER_ALPHA + (1.0F - CENTER_ALPHA) * rim) + spec * alphaF;

        b.vertex(pose, r * nx, r * ny, r * nz)
                .color(byteOf(red), byteOf(green), byteOf(blue), byteOf(alpha))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normalMat, nx, ny, nz)
                .endVertex();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int byteOf(float f) {
        return Math.max(0, Math.min(255, (int) (f * 255.0F)));
    }

    /**
     * Faixa de numerais: um cilindro baixo. A textura se repete em volta quantas vezes for preciso para os numerais
     * n\u00e3o ficarem esticados, seja qual for o tamanho da esfera.
     */
    static void drawRing(MatrixStack stack, IVertexBuilder b, float radius, float height, float alphaF) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        int alpha = byteOf(alphaF);
        float half = height * 0.5F;
        int repeats = ringRepeats(radius, height);

        for (int j = 0; j < RING_SEGMENTS; j++) {
            double a0 = 2 * Math.PI * j / RING_SEGMENTS;
            double a1 = 2 * Math.PI * (j + 1) / RING_SEGMENTS;
            float x0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            // u decresce com o \u00e2ngulo: assim os numerais da frente (lado de fora) se leem da esquerda para a direita
            float u0 = repeats * (1.0F - (float) j / RING_SEGMENTS);
            float u1 = repeats * (1.0F - (float) (j + 1) / RING_SEGMENTS);

            vertex(b, pose, normal, radius * x0, -half, radius * z0, u0, 1.0F, x0, 0, z0, alpha);
            vertex(b, pose, normal, radius * x1, -half, radius * z1, u1, 1.0F, x1, 0, z1, alpha);
            vertex(b, pose, normal, radius * x1, half, radius * z1, u1, 0.0F, x1, 0, z1, alpha);
            vertex(b, pose, normal, radius * x0, half, radius * z0, u0, 0.0F, x0, 0, z0, alpha);
        }
    }

    /** Quantas vezes a textura da faixa cabe em volta sem esticar (pelo menos 1). */
    static int ringRepeats(float radius, float height) {
        if (height <= 0.0F) return 1;
        return Math.max(1, Math.round((float) (2.0 * Math.PI * radius) / (height * RING_TEX_ASPECT)));
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
}
