package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cliente: n\u00fameros romanos que sobem ao lado da barra de Time quando o Julius rouba tempo.
 * A barra do Block Clover fica em posi\u00e7\u00e3o fixa (x=63, y=130..232 em coordenadas da GUI).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTimeGain {
    // ===== Ajustes f\u00e1ceis =====
    private static final int ANCHOR_X = 76;      // \u00e0 direita da barra
    private static final int ANCHOR_Y = 214;     // perto da base da barra / do n\u00famero de Time
    private static final float RISE = 46.0F;     // quanto sobe, em pixels da GUI
    private static final float LIFE = 50.0F;     // dura\u00e7\u00e3o em ticks
    private static final int COLOR = 0x6FD6FF;

    private static final class Pop {
        final String text;
        final long start;
        final float dx;
        Pop(String text, long start, float dx) { this.text = text; this.start = start; this.dx = dx; }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static int counter;

    public static void add(int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || amount <= 0) return;
        counter++;
        POPS.add(new Pop("+" + roman(amount), mc.level.getGameTime(), (counter % 3) * 9));
    }

    static String roman(int n) {
        if (n <= 0) return "";
        int[] v = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] s = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) { b.append(s[i]); n -= v[i]; }
        return b.toString();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT || POPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FontRenderer font = mc.font;
        MatrixStack ms = event.getMatrixStack();
        float now = mc.level.getGameTime() + event.getPartialTicks();

        Iterator<Pop> it = POPS.iterator();
        while (it.hasNext()) {
            Pop p = it.next();
            float t = (now - p.start) / LIFE;
            if (t >= 1.0F) { it.remove(); continue; }
            if (t < 0.0F) t = 0.0F;
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            float fade = t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F;
            int alpha = Math.max(4, (int) (255 * fade));
            int color = (alpha << 24) | COLOR;
            float y = ANCHOR_Y - RISE * ease;
            RenderSystem.enableBlend();
            font.drawShadow(ms, p.text, ANCHOR_X + p.dx, y, color);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        POPS.clear();
    }
}
