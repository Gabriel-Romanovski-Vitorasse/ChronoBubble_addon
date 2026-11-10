package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Cliente: a bolha de Chrono Stasis com a skin do jogador, parada no ponto de partida do Time Hop. */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientHopGhost {
    private static final float RADIUS = 1.35F;
    private static final float SPHERE_ALPHA = 0.42F;
    private static final float RING_ALPHA = 0.8F;
    private static final float POP_TICKS = 6.0F;

    private static final class Ghost {
        final HopGhostPacket p; final long start;
        Ghost(HopGhostPacket p, long start) { this.p = p; this.start = start; }
    }
    private static final List<Ghost> GHOSTS = new ArrayList<>();

    public static void add(HopGhostPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        GHOSTS.add(new Ghost(p, mc.level.getGameTime()));
    }

    private static float pop(float age, float total) {
        float in = Math.min(1.0F, age / POP_TICKS);
        float out = Math.min(1.0F, Math.max(0.0F, (total - age) / POP_TICKS));
        float s = Math.min(in, out);
        return s * s * (3.0F - 2.0F * s);
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        if (GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { GHOSTS.clear(); return; }
        MatrixStack stack = event.getMatrixStack();
        float pt = event.getPartialTicks();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buffers = mc.renderBuffers().bufferSource();
        RenderType sphereType = RenderType.entityTranslucent(StasisBubbleRenderer.SPHERE_TEX);
        RenderType ringType = RenderType.entityTranslucent(StasisBubbleRenderer.RING_TEX);

        for (Iterator<Ghost> it = GHOSTS.iterator(); it.hasNext(); ) {
            Ghost g = it.next();
            float age = mc.level.getGameTime() + pt - g.start;
            if (age >= g.p.ticks) { it.remove(); continue; }
            float s = pop(age, g.p.ticks);
            float radius = RADIUS * s;
            double x = g.p.x - cam.x, y = g.p.y - cam.y, z = g.p.z - cam.z;

            // 1) a skin do jogador, parada na pose em que ele estava
            Entity ent = mc.level.getEntity(g.p.playerId);
            if (ent instanceof PlayerEntity && s > 0.3F) {
                LivingEntity le = (LivingEntity) ent;
                float yRot = ent.yRot, yRotO = ent.yRotO, xRot = ent.xRot, xRotO = ent.xRotO;
                float body = le.yBodyRot, bodyO = le.yBodyRotO, head = le.yHeadRot, headO = le.yHeadRotO;
                ent.yRot = ent.yRotO = g.p.yRot;
                ent.xRot = ent.xRotO = g.p.xRot;
                le.yBodyRot = le.yBodyRotO = g.p.bodyRot;
                le.yHeadRot = le.yHeadRotO = g.p.headRot;
                try {
                    mc.getEntityRenderDispatcher().render(ent, x, y, z, g.p.yRot, 1.0F, stack, buffers, 15728880);
                } catch (Throwable t) {
                    // se o render falhar, a bolha continua sendo desenhada
                } finally {
                    ent.yRot = yRot; ent.yRotO = yRotO; ent.xRot = xRot; ent.xRotO = xRotO;
                    le.yBodyRot = body; le.yBodyRotO = bodyO; le.yHeadRot = head; le.yHeadRotO = headO;
                }
                buffers.endBatch();
            }

            // 2) a bolha do Chrono Stasis por cima
            if (radius < 0.05F) continue;
            stack.pushPose();
            stack.translate(x, y + 0.9D, z);
            StasisBubbleRenderer.drawSphere(stack, buffers.getBuffer(sphereType), radius, SPHERE_ALPHA);
            stack.pushPose();
            stack.mulPose(Vector3f.ZP.rotationDegrees(28.0F));
            stack.mulPose(Vector3f.YP.rotationDegrees(age * 3.0F));
            StasisBubbleRenderer.drawRing(stack, buffers.getBuffer(ringType), radius * 1.06F, radius * 0.22F, RING_ALPHA);
            stack.popPose();
            stack.pushPose();
            stack.mulPose(Vector3f.ZP.rotationDegrees(-62.0F));
            stack.mulPose(Vector3f.YP.rotationDegrees(-age * 1.8F));
            StasisBubbleRenderer.drawRing(stack, buffers.getBuffer(ringType), radius * 1.10F, radius * 0.18F, RING_ALPHA * 0.85F);
            stack.popPose();
            stack.popPose();
            buffers.endBatch(sphereType);
            buffers.endBatch(ringType);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        GHOSTS.clear();
    }
}
