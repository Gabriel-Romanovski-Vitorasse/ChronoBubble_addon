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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grim\u00f3rio do Tempo em forma de cilindro de p\u00e1ginas em espiral, flutuando ao lado do jogador
 * (no lugar do livro gen\u00e9rico). Aparece quando o grim\u00f3rio est\u00e1 equipado e em modo de combate.
 * Para esconder o livro original, ative tamb\u00e9m o resource pack "ChronoBubble_GrimorioPack".
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeGrimoireRenderer {

    // ===== Ajustes f\u00e1ceis =====
    private static final int   PAGES   = 72;      // quantidade de p\u00e1ginas do grim\u00f3rio completo
    private static final int   RADIAL  = 6;       // segmentos de cada p\u00e1gina (curvatura do redemoinho)
    private static final float RADIUS  = 0.26F;   // raio do cilindro
    private static final float HEIGHT  = 0.52F;   // altura
    private static final float TWIST   = 1.1F;    // quanto as p\u00e1ginas se enrolam (radianos)
    private static final float SPIN    = 1.2F;    // graus por tick de rota\u00e7\u00e3o
    // posi\u00e7\u00e3o e tamanho: config do cliente (visual.grimoire_side/height/front/scale), ajust\u00e1vel na tecla K
    private static final float HALO_ALPHA = 0.28F;
    private static final int FULL_BRIGHT = 15728880;

    private static final ResourceLocation PAGES_TEX  = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/grimoire_pages.png");
    private static final ResourceLocation EMBLEM_TEX = new ResourceLocation(ChronoBubbleMod.MODID, "textures/effect/grimoire_emblem.png");

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!ChronoConfig.GRIMOIRE_ENABLED.get()) return;
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
        stack.translate(ChronoConfig.GRIMOIRE_SIDE.get(),
                ChronoConfig.GRIMOIRE_HEIGHT.get() + 0.05D * Math.sin(time * 0.08D),     // flutua ao lado, balan\u00e7ando
                ChronoConfig.GRIMOIRE_FRONT.get());
        float scale = ChronoConfig.f(ChronoConfig.GRIMOIRE_SCALE);
        stack.scale(scale, scale, scale);
        stack.mulPose(Vector3f.XP.rotationDegrees(-12.0F));                        // leve inclina\u00e7\u00e3o
        stack.mulPose(Vector3f.YP.rotationDegrees(time * SPIN));                   // gira devagar

        // 1 p\u00e1gina enquanto o jogador est\u00e1 "renascido"; 72 no grim\u00f3rio completo (com anima\u00e7\u00e3o ao restaurar)
        int pages = ClientRebirth.pageCount(entity, partial, PAGES);
        drawPages(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(PAGES_TEX)), 1.0F, 255, false, pages);
        if (pages > 8) drawEmblem(stack, buffers.getBuffer(RenderType.entityTranslucent(EMBLEM_TEX)), 0.11F, HEIGHT * 0.5F + 0.003F);
        // brilho em volta (c\u00f3pia maior, azulada e transl\u00facida)
        drawPages(stack, buffers.getBuffer(RenderType.entityTranslucent(PAGES_TEX)), 1.12F, (int) (HALO_ALPHA * 255.0F), true, pages);

        stack.popPose();
    }

    private static void vertex(IVertexBuilder b, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               int r, int g, int bl, int a, float nx, float ny, float nz) {
        b.vertex(pose, x, y, z)
                .color(r, g, bl, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    /** P\u00e1ginas retas na vertical, cada uma levemente curvada em espiral do centro para a borda. */
    private static void drawPages(MatrixStack stack, IVertexBuilder b, float scale, int alpha, boolean halo, int pages) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        float half = HEIGHT * 0.5F * scale;

        for (int i = 0; i < pages; i++) {
            double base = 2.0D * Math.PI * i / pages;
            // sombreamento por p\u00e1gina: d\u00e1 a apar\u00eancia de l\u00e2minas empilhadas
            float shade = 0.78F + 0.22F * (float) (0.5D + 0.5D * Math.cos(base - 0.9D));
            int cr = halo ? 190 : (int) (255 * shade);
            int cg = halo ? 225 : (int) (252 * shade);
            int cb = halo ? 255 : (int) (240 * shade);

            for (int s = 0; s < RADIAL; s++) {
                float f0 = (float) s / RADIAL;
                float f1 = (float) (s + 1) / RADIAL;
                float r0 = RADIUS * scale * (0.03F + 0.97F * f0);
                float r1 = RADIUS * scale * (0.03F + 0.97F * f1);
                double a0 = base + TWIST * f0;
                double a1 = base + TWIST * f1;
                float x0 = r0 * (float) Math.cos(a0), z0 = r0 * (float) Math.sin(a0);
                float x1 = r1 * (float) Math.cos(a1), z1 = r1 * (float) Math.sin(a1);
                float nx = (float) -Math.sin(a0), nz = (float) Math.cos(a0);

                vertex(b, pose, normal, x0, -half, z0, f0, 1.0F, cr, cg, cb, alpha, nx, 0, nz);
                vertex(b, pose, normal, x1, -half, z1, f1, 1.0F, cr, cg, cb, alpha, nx, 0, nz);
                vertex(b, pose, normal, x1,  half, z1, f1, 0.0F, cr, cg, cb, alpha, nx, 0, nz);
                vertex(b, pose, normal, x0,  half, z0, f0, 0.0F, cr, cg, cb, alpha, nx, 0, nz);
            }
        }
    }

    /** Estrela brilhante no centro do topo do cilindro. */
    private static void drawEmblem(MatrixStack stack, IVertexBuilder b, float size, float y) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        vertex(b, pose, normal, -size, y, -size, 0.0F, 0.0F, 255, 255, 255, 255, 0, 1, 0);
        vertex(b, pose, normal, -size, y,  size, 0.0F, 1.0F, 255, 255, 255, 255, 0, 1, 0);
        vertex(b, pose, normal,  size, y,  size, 1.0F, 1.0F, 255, 255, 255, 255, 0, 1, 0);
        vertex(b, pose, normal,  size, y, -size, 1.0F, 0.0F, 255, 255, 255, 255, 0, 1, 0);
    }
}
