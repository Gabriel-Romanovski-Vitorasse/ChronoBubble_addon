package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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
 * Grim\u00f3rio do Tempo do Julius: um bloco cil\u00edndrico de p\u00e1ginas brancas que, visto de cima, forma um redemoinho
 * (as bordas das p\u00e1ginas fazem uma espiral, mais fechada no centro), com aura ciano e uma estrela cintilando no centro
 * do redemoinho. Flutua ao lado do jogador quando o grim\u00f3rio est\u00e1 equipado e em modo de combate.
 * <p>
 * Desenho: tampa de cima, tampa de baixo e lateral, fatiadas por p\u00e1gina. A textura de cada fatia tem a linha escura
 * da borda da p\u00e1gina; nas tampas a fatia segue a espiral (a linha vira o redemoinho) e na lateral vira a borda
 * vertical. Renascido (grim\u00f3rio de uma p\u00e1gina), aparece s\u00f3 uma p\u00e1gina solta; ao restaurar, as fatias voltam uma a
 * uma. Texturas geradas por {@code tools/textures/make_grimoire.py}.
 * Para esconder o livro original, ative tamb\u00e9m o resource pack "ChronoBubble_GrimorioPack".
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeGrimoireRenderer {

    // ===== Ajustes f\u00e1ceis =====
    public static final boolean ENABLED = true;
    private static final int   PAGES   = 120;     // p\u00e1ginas do grim\u00f3rio completo
    private static final int   RADIAL  = 12;      // segmentos de cada espiral (suavidade da curva)
    /**
     * A linha escura da borda aparece a cada tantas p\u00e1ginas. Com uma linha por p\u00e1gina (120), no tamanho em que o
     * grim\u00f3rio aparece na tela as linhas se embaralham (moir\u00e9) e o redemoinho some; 30 linhas ficam leg\u00edveis.
     */
    private static final int   LINE_EVERY = 4;
    /** Onde come\u00e7a a parte branca da textura (depois da linha da borda), para as fatias sem linha. */
    private static final float PLAIN_U = 0.3F;
    private static final float RADIUS  = 0.26F;   // raio do cilindro
    private static final float HEIGHT  = 0.52F;   // altura
    /** Quanto cada p\u00e1gina se enrola do centro at\u00e9 a borda (radianos). Mais alto = redemoinho mais fechado. */
    private static final float TWIST   = 2.6F;
    /** O redemoinho "respira": a tor\u00e7\u00e3o oscila um pouco em volta de TWIST. */
    private static final float TWIST_BREATH = 0.12F;
    private static final float SPIN    = 1.6F;    // graus por tick de rota\u00e7\u00e3o do bloco
    private static final double SIDE   = -0.85D;  // deslocamento lateral (negativo = lado direito)
    private static final double FRONT  = 0.15D;   // deslocamento para a frente
    private static final double LIFT   = 1.15D;   // altura em rela\u00e7\u00e3o aos p\u00e9s
    /** Aura ciano: camadas cada vez maiores e mais fracas, mais fortes na silhueta (mistura aditiva). */
    private static final float[] AURA_SCALE = { 1.07F, 1.17F, 1.32F };
    private static final float[] AURA_POWER = { 0.60F, 0.38F, 0.20F };
    private static final float[] AURA_RGB = { 0.45F, 0.85F, 1.00F };
    private static final int   AURA_SEGMENTS = 48;
    private static final float STAR_SIZE = 0.15F;
    private static final int FULL_BRIGHT = 15728880;

    private static final ResourceLocation PAGES_TEX = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/grimoire_pages.png");
    private static final ResourceLocation STAR_TEX  = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/grimoire_star.png");
    private static final RenderType PAGES_TYPE = RenderType.entityCutoutNoCull(PAGES_TEX);
    /** "eyes" = mistura aditiva (como os olhos da aranha): preto some, cores somam luz. */
    private static final RenderType AURA_TYPE = RenderType.eyes(StasisBubbleRenderer.SPHERE_TEX);
    private static final RenderType STAR_TYPE = RenderType.eyes(STAR_TEX);

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!ENABLED) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof PlayerEntity)) return;
        if (!BlockCloverBridge.hasTimeGrimoireOut(entity)) return;

        MatrixStack stack = event.getMatrixStack();
        IRenderTypeBuffer buffers = event.getBuffers();
        float partial = event.getPartialRenderTick();
        float time = entity.tickCount + partial;
        float yaw = entity.yBodyRotO + (entity.yBodyRot - entity.yBodyRotO) * partial;

        stack.pushPose();
        stack.mulPose(Vector3f.YP.rotationDegrees(-yaw));                         // acompanha o corpo do jogador
        stack.translate(SIDE, LIFT + 0.05D * Math.sin(time * 0.08D), FRONT);       // flutua ao lado, balan\u00e7ando
        stack.mulPose(Vector3f.XP.rotationDegrees(-12.0F));                        // leve inclina\u00e7\u00e3o
        stack.mulPose(Vector3f.YP.rotationDegrees(time * SPIN));                   // gira devagar

        // 1 p\u00e1gina enquanto o jogador est\u00e1 "renascido"; todas no grim\u00f3rio completo (com anima\u00e7\u00e3o ao restaurar)
        int pages = ClientRebirth.pageCount(entity, partial, PAGES);
        float twist = TWIST * (1.0F + TWIST_BREATH * (float) Math.sin(time * 0.07F));
        float full = pages / (float) PAGES;

        IVertexBuilder pageBuf = buffers.getBuffer(PAGES_TYPE);
        drawBlock(stack, pageBuf, pages, twist);
        if (pages < PAGES) drawSheets(stack, pageBuf, pages, twist);              // p\u00e1ginas soltas aparecem como folhas

        IVertexBuilder auraBuf = buffers.getBuffer(AURA_TYPE);
        for (int i = 0; i < AURA_SCALE.length; i++) drawAura(stack, auraBuf, AURA_SCALE[i], AURA_POWER[i] * full);

        if (pages > 8) {
            float twinkle = 0.85F + 0.15F * (float) Math.sin(time * 0.25F) + 0.35F * (float) Math.max(0.0, Math.sin(time * 0.045F) - 0.9) * 10.0F;
            drawStar(stack, buffers.getBuffer(STAR_TYPE), HEIGHT * 0.5F + 0.03F, STAR_SIZE * twinkle * full, time * 0.6F);
        }
        stack.popPose();
    }

    // ---------- geometria ----------

    /** \u00c2ngulo da espiral da p\u00e1gina {@code i} na fra\u00e7\u00e3o {@code f} do raio (0 = centro): enrola mais perto do centro. */
    private static double spiralAngle(int i, float f, float twist) {
        return 2.0D * Math.PI * i / PAGES + twist * Math.sqrt(f);
    }

    private static float radiusAt(float f) {
        return RADIUS * (0.02F + 0.98F * f);
    }

    /** Tampas (com o redemoinho) e lateral, uma fatia por p\u00e1gina. */
    private static void drawBlock(MatrixStack stack, IVertexBuilder b, int pages, float twist) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float half = HEIGHT * 0.5F;

        for (int i = 0; i < pages; i++) {
            float u0 = i % LINE_EVERY == 0 ? 0.0F : PLAIN_U;                      // fatia com ou sem a linha da borda
            for (int s = 0; s < RADIAL; s++) {
                float f0 = (float) s / RADIAL, f1 = (float) (s + 1) / RADIAL;
                float r0 = radiusAt(f0), r1 = radiusAt(f1);
                double a00 = spiralAngle(i, f0, twist), a01 = spiralAngle(i, f1, twist);
                double a10 = spiralAngle(i + 1, f0, twist), a11 = spiralAngle(i + 1, f1, twist);
                float x00 = r0 * (float) Math.cos(a00), z00 = r0 * (float) Math.sin(a00);
                float x01 = r1 * (float) Math.cos(a01), z01 = r1 * (float) Math.sin(a01);
                float x10 = r0 * (float) Math.cos(a10), z10 = r0 * (float) Math.sin(a10);
                float x11 = r1 * (float) Math.cos(a11), z11 = r1 * (float) Math.sin(a11);
                for (int side = -1; side <= 1; side += 2) {                    // tampa de baixo e de cima
                    float y = half * side;
                    vertex(b, pose, normal, x00, y, z00, u0, f0, 255, 0, side, 0);
                    vertex(b, pose, normal, x01, y, z01, u0, f1, 255, 0, side, 0);
                    vertex(b, pose, normal, x11, y, z11, 1.0F, f1, 255, 0, side, 0);
                    vertex(b, pose, normal, x10, y, z10, 1.0F, f0, 255, 0, side, 0);
                }
            }
            // lateral: da borda desta p\u00e1gina at\u00e9 a da pr\u00f3xima
            double a0 = spiralAngle(i, 1.0F, twist), a1 = spiralAngle(i + 1, 1.0F, twist);
            float x0 = RADIUS * (float) Math.cos(a0), z0 = RADIUS * (float) Math.sin(a0);
            float x1 = RADIUS * (float) Math.cos(a1), z1 = RADIUS * (float) Math.sin(a1);
            float nx = (float) Math.cos((a0 + a1) * 0.5D), nz = (float) Math.sin((a0 + a1) * 0.5D);
            vertex(b, pose, normal, x0, -half, z0, u0, 1.0F, 255, nx, 0, nz);
            vertex(b, pose, normal, x1, -half, z1, 1.0F, 1.0F, 255, nx, 0, nz);
            vertex(b, pose, normal, x1, half, z1, 1.0F, 0.0F, 255, nx, 0, nz);
            vertex(b, pose, normal, x0, half, z0, u0, 0.0F, 255, nx, 0, nz);
        }
    }

    /** Folhas verticais ao longo da espiral (s\u00f3 quando faltam p\u00e1ginas: renascido ou restaurando). */
    private static void drawSheets(MatrixStack stack, IVertexBuilder b, int pages, float twist) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float half = HEIGHT * 0.5F;
        for (int i = 0; i < pages; i++) {
            for (int s = 0; s < RADIAL; s++) {
                float f0 = (float) s / RADIAL, f1 = (float) (s + 1) / RADIAL;
                double a0 = spiralAngle(i, f0, twist), a1 = spiralAngle(i, f1, twist);
                float x0 = radiusAt(f0) * (float) Math.cos(a0), z0 = radiusAt(f0) * (float) Math.sin(a0);
                float x1 = radiusAt(f1) * (float) Math.cos(a1), z1 = radiusAt(f1) * (float) Math.sin(a1);
                float nx = (float) -Math.sin(a0), nz = (float) Math.cos(a0);
                // u = 0.6: parte branca da textura (sem a linha da borda)
                vertex(b, pose, normal, x0, -half, z0, 0.6F, 1.0F, 245, nx, 0, nz);
                vertex(b, pose, normal, x1, -half, z1, 0.6F, 1.0F, 245, nx, 0, nz);
                vertex(b, pose, normal, x1, half, z1, 0.6F, 0.0F, 245, nx, 0, nz);
                vertex(b, pose, normal, x0, half, z0, 0.6F, 0.0F, 245, nx, 0, nz);
            }
        }
    }

    /**
     * Uma camada da aura: c\u00f3pia maior do cilindro, mais clara onde a superf\u00edcie est\u00e1 de lado para quem olha
     * (silhueta) e quase apagada de frente, para brilhar em volta sem lavar as p\u00e1ginas.
     */
    private static void drawAura(MatrixStack stack, IVertexBuilder b, float scale, float power) {
        if (power <= 0.01F) return;
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float r = RADIUS * scale, half = HEIGHT * 0.5F + RADIUS * (scale - 1.0F);
        for (int j = 0; j < AURA_SEGMENTS; j++) {
            double a0 = 2.0D * Math.PI * j / AURA_SEGMENTS, a1 = 2.0D * Math.PI * (j + 1) / AURA_SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            auraVertex(b, pose, normal, r * c0, -half, r * s0, c0, 0, s0, power);
            auraVertex(b, pose, normal, r * c1, -half, r * s1, c1, 0, s1, power);
            auraVertex(b, pose, normal, r * c1, half, r * s1, c1, 0, s1, power);
            auraVertex(b, pose, normal, r * c0, half, r * s0, c0, 0, s0, power);
            for (int side = -1; side <= 1; side += 2) {                        // tampas (tri\u00e2ngulos como quads)
                float y = half * side;
                auraVertex(b, pose, normal, 0, y, 0, 0, side, 0, power);
                auraVertex(b, pose, normal, r * c0, y, r * s0, 0, side, 0, power);
                auraVertex(b, pose, normal, r * c1, y, r * s1, 0, side, 0, power);
                auraVertex(b, pose, normal, 0, y, 0, 0, side, 0, power);
            }
        }
    }

    private static void auraVertex(IVertexBuilder b, Matrix4f pose, Matrix3f normalMat,
                                   float x, float y, float z, float nx, float ny, float nz, float power) {
        float rim = 1.0F - StasisBubbleRenderer.facing(pose, normalMat, x, y, z, nx, ny, nz);
        float k = power * (0.15F + 0.85F * rim * rim);
        b.vertex(pose, x, y, z)
                .color(byteOf(AURA_RGB[0] * k), byteOf(AURA_RGB[1] * k), byteOf(AURA_RGB[2] * k), 255)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normalMat, nx, ny, nz)
                .endVertex();
    }

    /**
     * Estrela de quatro pontas no centro do redemoinho, sempre virada para a c\u00e2mera. A posi\u00e7\u00e3o vem da pilha atual
     * (centro do topo); o desenho \u00e9 feito direto no espa\u00e7o da c\u00e2mera, onde o plano XY olha para quem v\u00ea.
     */
    private static void drawStar(MatrixStack stack, IVertexBuilder b, float y, float size, float spinDeg) {
        if (size <= 0.005F) return;
        Vector4f c = new Vector4f(0.0F, y, 0.0F, 1.0F);
        c.transform(stack.last().pose());
        Matrix4f pose = Matrix4f.createTranslateMatrix(c.x(), c.y(), c.z());
        Matrix3f normal = new Matrix3f();
        normal.setIdentity();
        double a = Math.toRadians(spinDeg);
        float cs = (float) Math.cos(a) * size, sn = (float) Math.sin(a) * size;
        // cantos (-1,-1) (1,-1) (1,1) (-1,1) girados em spinDeg
        b.vertex(pose, -cs + sn, -sn - cs, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(normal, 0, 0, 1).endVertex();
        b.vertex(pose, cs + sn, sn - cs, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(normal, 0, 0, 1).endVertex();
        b.vertex(pose, cs - sn, sn + cs, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(normal, 0, 0, 1).endVertex();
        b.vertex(pose, -cs - sn, -sn + cs, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(normal, 0, 0, 1).endVertex();
    }

    private static void vertex(IVertexBuilder b, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v, int gray,
                               float nx, float ny, float nz) {
        b.vertex(pose, x, y, z)
                .color(gray, gray, Math.min(255, gray + 4), 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    private static int byteOf(float f) {
        return Math.max(0, Math.min(255, (int) (f * 255.0F)));
    }
}
