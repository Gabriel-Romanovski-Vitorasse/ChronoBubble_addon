package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cliente: mostra o que vai te atingir. Proj\u00e9til = trilha pontilhada at\u00e9 o ponto do impacto, com um anel
 * no lugar onde ele te acertaria. Mob prestes a atacar = anel pulsando aos p\u00e9s dele.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientPrecog {
    private static final class Threat {
        final byte kind;
        long lastSeen;
        Threat(byte kind, long lastSeen) { this.kind = kind; this.lastSeen = lastSeen; }
    }

    private static final Map<Integer, Threat> THREATS = new HashMap<>();
    private static final long EXPIRE_TICKS = 6;
    private static final float DOT_ALPHA = 0.9F;
    private static final int DOTS = 10;

    public static void update(int[] ids, byte[] kinds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        for (int i = 0; i < ids.length; i++) {
            Threat t = THREATS.get(ids[i]);
            if (t == null) {
                THREATS.put(ids[i], new Threat(kinds[i], now));
                mc.getSoundManager().play(SimpleSound.forUI(SoundEvents.NOTE_BLOCK_CHIME, kinds[i] == 0 ? 1.9F : 1.2F));
            } else {
                t.lastSeen = now;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        if (THREATS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long nowTick = mc.level.getGameTime();
        float pt = event.getPartialTicks();
        float time = nowTick + pt;
        MatrixStack stack = event.getMatrixStack();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buffers = mc.renderBuffers().bufferSource();
        RenderType sphereType = RenderType.entityTranslucent(StasisBubbleRenderer.SPHERE_TEX);
        RenderType ringType = RenderType.entityTranslucent(StasisBubbleRenderer.RING_TEX);

        boolean drew = false;
        Iterator<Map.Entry<Integer, Threat>> it = THREATS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Threat> en = it.next();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || nowTick - en.getValue().lastSeen > EXPIRE_TICKS) { it.remove(); continue; }

            if (en.getValue().kind == 0 && e instanceof ProjectileEntity) {
                Vector3d pos = new Vector3d(MathHelper.lerp(pt, e.xo, e.getX()), MathHelper.lerp(pt, e.yo, e.getY()), MathHelper.lerp(pt, e.zo, e.getZ()));
                Vector3d m = e.getDeltaMovement();
                Vector3d player = new Vector3d(mc.player.getX(), mc.player.getY() + mc.player.getBbHeight() * 0.5D, mc.player.getZ());
                int impact = Precognition.HORIZON_TICKS;
                for (int t = 1; t <= Precognition.HORIZON_TICKS; t++) {
                    if (Precognition.predict(pos, m, e.isNoGravity(), t).distanceToSqr(player) <= 1.44D) { impact = t; break; }
                }
                for (int i = 1; i <= DOTS; i++) {
                    int t = Math.max(1, Math.round(impact * i / (float) DOTS));
                    Vector3d at = Precognition.predict(pos, m, e.isNoGravity(), t);
                    float size = 0.05F + 0.05F * (i / (float) DOTS);
                    stack.pushPose();
                    stack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                    StasisBubbleRenderer.drawSphere(stack, buffers.getBuffer(sphereType), size, DOT_ALPHA);
                    stack.popPose();
                }
                Vector3d hit = Precognition.predict(pos, m, e.isNoGravity(), impact);
                float pulse = 0.55F + 0.1F * (float) Math.sin(time * 0.6F);
                stack.pushPose();
                stack.translate(hit.x - cam.x, hit.y - cam.y, hit.z - cam.z);
                stack.mulPose(Vector3f.XP.rotationDegrees(90.0F));
                stack.mulPose(Vector3f.YP.rotationDegrees(time * 6.0F));
                StasisBubbleRenderer.drawRing(stack, buffers.getBuffer(ringType), pulse, pulse * 0.12F, 0.9F);
                stack.popPose();
                drew = true;
            } else if (en.getValue().kind == 1) {
                double x = MathHelper.lerp(pt, e.xo, e.getX()) - cam.x;
                double y = MathHelper.lerp(pt, e.yo, e.getY()) + 0.08D - cam.y;
                double z = MathHelper.lerp(pt, e.zo, e.getZ()) - cam.z;
                float r = Math.max(0.9F, e.getBbWidth() * 1.1F) * (0.9F + 0.1F * (float) Math.sin(time * 0.7F));
                stack.pushPose();
                stack.translate(x, y, z);
                stack.mulPose(Vector3f.YP.rotationDegrees(time * 4.0F));
                StasisBubbleRenderer.drawRing(stack, buffers.getBuffer(ringType), r, r * 0.1F, 0.85F);
                stack.popPose();
                drew = true;
            }
        }
        if (drew) {
            buffers.endBatch(sphereType);
            buffers.endBatch(ringType);
        }
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        THREATS.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        THREATS.clear();
    }
}
