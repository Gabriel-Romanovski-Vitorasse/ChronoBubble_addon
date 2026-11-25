package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Cliente: quando um congelamento acaba, a bolha se parte em cacos curvos (pedaços da própria esfera) que voam
 * para fora girando, caem um pouco e somem. Desenhado no mundo, sem depender da entidade (ela pode já ter morrido).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientShatter {
    /** Cacos: faixas de latitude x fatias de longitude. */
    private static final int ROWS = 6, COLS = 10;
    /** Quanto cada caco encolhe nas bordas (deixa frestas entre eles, como vidro partido). */
    private static final float GAP = 0.08F;

    private static final class Burst {
        final double x, y, z;
        final float radius;
        final long start;
        final int seed;

        Burst(double x, double y, double z, float radius, long start, int seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.start = start;
            this.seed = seed;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    /** id da entidade -> tick em que a bolha dela se partiu (para não desenhar a bolha de novo logo depois). */
    private static final Map<Integer, Long> RECENT = new HashMap<>();

    private ClientShatter() {
    }

    /** A bolha de {@code e} acabou de se partir. */
    static void burst(Entity e) {
        if (e.level == null) return;
        long now = e.level.getGameTime();
        Long last = RECENT.get(e.getId());
        if (last != null && now - last < 5) return;                          // já se partiu agora
        RECENT.put(e.getId(), now);
        float r = StasisField.radius(e);
        double cy = e.getY() + e.getBbHeight() * 0.5D;
        BURSTS.add(new Burst(e.getX(), cy, e.getZ(), r, now, e.getId() * 31 + (int) now));
        for (int i = 0; i < 16; i++) {
            double ang = Math.PI * 2.0D * i / 16.0D;
            e.level.addParticle(ParticleTypes.END_ROD, e.getX() + Math.cos(ang) * r * 0.8D, cy + (i % 3 - 1) * r * 0.4D,
                    e.getZ() + Math.sin(ang) * r * 0.8D, Math.cos(ang) * 0.08D, 0.02D, Math.sin(ang) * 0.08D);
        }
    }

    /** A bolha desta entidade se partiu há pouco (o efeito do próprio jogador pode demorar um instante a sair). */
    static boolean recentlyShattered(Entity e) {
        Long t = RECENT.get(e.getId());
        return t != null && e.level.getGameTime() - t < 20;
    }

    static void clear() {
        BURSTS.clear();
        RECENT.clear();
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        RECENT.values().removeIf(t -> now - t > 40);
        if (BURSTS.isEmpty()) return;
        MatrixStack stack = event.getMatrixStack();
        float pt = event.getPartialTicks();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buffers = mc.renderBuffers().bufferSource();
        IVertexBuilder b = buffers.getBuffer(StasisBubbleRenderer.SPHERE_TYPE);

        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst burst = it.next();
            float ticks = now + pt - burst.start;
            if (ticks >= BubbleFx.SHATTER_TICKS) { it.remove(); continue; }
            stack.pushPose();
            stack.translate(burst.x - cam.x, burst.y - cam.y, burst.z - cam.z);
            drawShards(stack, b, burst, ticks);
            stack.popPose();
        }
        buffers.endBatch(StasisBubbleRenderer.SPHERE_TYPE);
    }

    private static void drawShards(MatrixStack stack, IVertexBuilder b, Burst burst, float ticks) {
        float r = burst.radius;
        float age = ticks / BubbleFx.SHATTER_TICKS;
        float alpha = 0.8F * BubbleFx.shardAlpha(age);
        float glow = Math.max(0.0F, 1.0F - ticks / 3.0F);                     // clarão branco no instante da quebra
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int id = row * COLS + col;
                float n1 = StasisBubbleRenderer.signedNoise(burst.seed + id, 1);
                float n2 = StasisBubbleRenderer.signedNoise(burst.seed + id, 2);
                float n3 = StasisBubbleRenderer.signedNoise(burst.seed + id, 3);
                // limites do caco (desencontrados entre as faixas, como tijolos, e com frestas)
                double latStep = Math.PI / ROWS, lonStep = 2.0D * Math.PI / COLS;
                double lat0 = -Math.PI / 2.0D + row * latStep, lat1 = lat0 + latStep;
                double lon0 = col * lonStep + (row % 2) * lonStep * 0.5D + n1 * lonStep * 0.15D, lon1 = lon0 + lonStep;
                // frestas de largura diferente em cada lado: os cacos ficam irregulares, n\u00e3o um quadriculado
                lat0 += latStep * (GAP + 0.10F * Math.abs(n2));
                lat1 -= latStep * (GAP + 0.10F * Math.abs(n3));
                lon0 += lonStep * (GAP + 0.12F * Math.abs(n3));
                lon1 -= lonStep * (GAP + 0.12F * Math.abs(n1));
                double latM = (lat0 + lat1) * 0.5D, lonM = (lon0 + lon1) * 0.5D;
                float dx = (float) (Math.cos(latM) * Math.cos(lonM)), dy = (float) Math.sin(latM), dz = (float) (Math.cos(latM) * Math.sin(lonM));

                float speed = 0.10F + 0.05F * n2;
                float out = BubbleFx.shardTravel(ticks, speed) * r;
                stack.pushPose();
                stack.translate(dx * (r + out), dy * (r + out) - BubbleFx.shardDrop(ticks), dz * (r + out));
                Vector3f axis = new Vector3f(n1, n2, n3);
                if (axis.dot(axis) < 1.0E-4F) axis = Vector3f.YP.copy();
                axis.normalize();
                stack.mulPose(axis.rotationDegrees(ticks * (12.0F + 10.0F * n3)));
                drawPatch(stack, b, r, lat0, lat1, lon0, lon1, dx, dy, dz, alpha, glow);
                stack.popPose();
            }
        }
    }

    /** Um caco: pedaço curvo da esfera (2 x 2 quads), centrado na origem da pilha. */
    private static void drawPatch(MatrixStack stack, IVertexBuilder b, float r, double lat0, double lat1, double lon0, double lon1,
                                  float cx, float cy, float cz, float alpha, float glow) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        // forma de caco: a borda de cima \u00e9 mais estreita que a de baixo (um trap\u00e9zio torto, n\u00e3o um ret\u00e2ngulo)
        double latM = (lat0 + lat1) * 0.5D, lonM = (lon0 + lon1) * 0.5D, half = (lon1 - lon0) * 0.5D;
        double topHalf = half * 0.55D, skew = half * 0.25D;
        corner(b, pose, normal, r, lat0, lon0, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, lat0, lon1, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, latM, lon1 - half * 0.1D, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, latM, lon0 + half * 0.1D, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, latM, lon0 + half * 0.1D, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, latM, lon1 - half * 0.1D, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, lat1, lonM + topHalf + skew, cx, cy, cz, alpha, glow);
        corner(b, pose, normal, r, lat1, lonM - topHalf + skew, cx, cy, cz, alpha, glow);
    }

    private static void corner(IVertexBuilder b, Matrix4f pose, Matrix3f normal, float r, double lat, double lon,
                               float cx, float cy, float cz, float alpha, float glow) {
        float nx = (float) (Math.cos(lat) * Math.cos(lon)), ny = (float) Math.sin(lat), nz = (float) (Math.cos(lat) * Math.sin(lon));
        StasisBubbleRenderer.fresnelVertex(b, pose, normal, r * (nx - cx), r * (ny - cy), r * (nz - cz), nx, ny, nz,
                0.0F, 0.0F, alpha, glow);
    }
}
