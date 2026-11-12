package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Cliente: tecla da zona (padr\u00e3o B) e o desenho da esfera grande em volta de quem a ligou. */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientZone {
    // ===== Ajustes f\u00e1ceis =====
    /** A esfera da zona \u00e9 grande: um pouco mais apagada que a dos mobs, com a faixa fina. */
    private static final float ZONE_FADE = 0.6F;
    private static final float ZONE_BAND = 0.06F;
    /** Orbe em volta de cada proj\u00e9til parado (esfera pequena: faixa proporcionalmente mais larga). */
    private static final float PROJ_FADE = 0.85F;
    private static final float PROJ_BAND = 0.25F;
    private static final float POP_TICKS = 8.0F;

    public static KeyBinding KEY;
    /** Tecla modificadora do Chrono Stasis sustentado (Alt esquerdo por padr\u00e3o). */
    public static KeyBinding HOLD_KEY;
    /** Abre a tela de configura\u00e7\u00e3o (padr\u00e3o: K). */
    public static KeyBinding CONFIG_KEY;
    /** Acelerar o tempo (padr\u00e3o: V). */
    public static KeyBinding ACCEL_KEY;
    private static boolean holdSent;

    /** id da entidade -> tick em que a zona ligou. */
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    /** id do proj\u00e9til -> tick em que foi parado (orbe do Chrono Stasis desenhado nele). */
    private static final Map<Integer, Long> PROJ = new HashMap<>();

    /** Chamado no setup do cliente (ClientSetup). */
    public static void registerKey() {
        KEY = new KeyBinding("key.chrono_bubble.zone", KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, 66 /* B */, "key.categories.chrono_bubble");
        ClientRegistry.registerKeyBinding(KEY);
        HOLD_KEY = new KeyBinding("key.chrono_bubble.sustain", KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, 342 /* Alt esquerdo */, "key.categories.chrono_bubble");
        ClientRegistry.registerKeyBinding(HOLD_KEY);
        CONFIG_KEY = new KeyBinding("key.chrono_bubble.config", KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, 75 /* K */, "key.categories.chrono_bubble");
        ClientRegistry.registerKeyBinding(CONFIG_KEY);
        ACCEL_KEY = new KeyBinding("key.chrono_bubble.accel", KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, 86 /* V */, "key.categories.chrono_bubble");
        ClientRegistry.registerKeyBinding(ACCEL_KEY);
    }

    public static void setProjectileOrb(int entityId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (on) PROJ.put(entityId, mc.level.getGameTime()); else PROJ.remove(entityId);
    }

    public static void set(int entityId, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (active) ACTIVE.put(entityId, mc.level.getGameTime());
        else ACTIVE.remove(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || KEY == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (HOLD_KEY != null && mc.player != null) {
            boolean down = HOLD_KEY.isDown();
            if (down != holdSent) {
                holdSent = down;
                ChronoBubbleMod.CHANNEL.sendToServer(new HoldKeyPacket(down));
            }
        }
        while (ACCEL_KEY != null && ACCEL_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) ChronoBubbleMod.CHANNEL.sendToServer(new AccelTogglePacket());
        }
        while (CONFIG_KEY != null && CONFIG_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new ChronoConfigScreen(null));
        }
        while (KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                ChronoBubbleMod.CHANNEL.sendToServer(new ZoneTogglePacket());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        if (ACTIVE.isEmpty() && PROJ.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MatrixStack stack = event.getMatrixStack();
        float pt = event.getPartialTicks();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buffers = mc.renderBuffers().bufferSource();

        boolean drew = false;
        for (Map.Entry<Integer, Long> en : ACTIVE.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) continue;
            float age = mc.level.getGameTime() + pt - en.getValue();
            float s = Math.max(0.0F, Math.min(1.0F, age / POP_TICKS));
            float radius = ChronoConfig.f(ChronoConfig.ZONE_RADIUS) * s * s * (3.0F - 2.0F * s);
            if (radius < 0.05F) continue;

            double x = MathHelper.lerp(pt, e.xo, e.getX()) - cam.x;
            double y = MathHelper.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5D - cam.y;
            double z = MathHelper.lerp(pt, e.zo, e.getZ()) - cam.z;

            stack.pushPose();
            stack.translate(x, y, z);
            StasisBubbleRenderer.drawBubble(stack, buffers, radius, ZONE_FADE, age, ZONE_BAND, e.getId());
            stack.popPose();
            drew = true;
        }
        // orbe do Chrono Stasis em cada proj\u00e9til parado
        Iterator<Map.Entry<Integer, Long>> pit = PROJ.entrySet().iterator();
        while (pit.hasNext()) {
            Map.Entry<Integer, Long> en = pit.next();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !e.isAlive()) { pit.remove(); continue; }
            float age = mc.level.getGameTime() + pt - en.getValue();
            float s = Math.max(0.0F, Math.min(1.0F, age / 5.0F));
            float r = (0.35F + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5F) * s * s * (3.0F - 2.0F * s);
            double x = e.getX() - cam.x, y = e.getY() + e.getBbHeight() * 0.5D - cam.y, z = e.getZ() - cam.z;
            stack.pushPose();
            stack.translate(x, y, z);
            StasisBubbleRenderer.drawBubble(stack, buffers, r, PROJ_FADE, age, PROJ_BAND, e.getId());
            stack.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(StasisBubbleRenderer.SPHERE_TYPE);
            buffers.endBatch(StasisBubbleRenderer.RING_TYPE);
        }
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        ACTIVE.clear();
        PROJ.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ACTIVE.clear();
        PROJ.clear();
        holdSent = false;
    }
}
