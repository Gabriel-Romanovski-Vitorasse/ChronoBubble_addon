package com.gabriel.chronobubble;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

/** Desenha a esfera arremessada: uma bolha pequena com um anel de numerais girando. */
public class StasisOrbRenderer extends EntityRenderer<StasisOrbEntity> {
    private static final float RADIUS = 0.2F;
    /** Largura da faixa em fra\u00e7\u00e3o do raio e multiplicador da velocidade de giro (a esfera \u00e9 pequena e r\u00e1pida). */
    private static final float ORB_BAND = 0.32F;
    private static final float ORB_SPIN = 6.0F;

    public StasisOrbRenderer(EntityRendererManager manager) {
        super(manager);
    }

    @Override
    public void render(StasisOrbEntity entity, float yaw, float partialTicks, MatrixStack stack,
                       IRenderTypeBuffer buffers, int light) {
        float time = entity.tickCount + partialTicks;
        stack.pushPose();
        stack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        // a mesma bolha dos mobs congelados, em miniatura: esfera com a faixa de numerais (mais larga, para aparecer
        // num objeto t\u00e3o pequeno, e girando mais r\u00e1pido, para dar a sensa\u00e7\u00e3o de movimento)
        StasisBubbleRenderer.drawBubble(stack, buffers, RADIUS, 1.0F, time * ORB_SPIN, ORB_BAND);
        stack.popPose();
        super.render(entity, yaw, partialTicks, stack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StasisOrbEntity entity) {
        return StasisBubbleRenderer.SPHERE_TEX;
    }
}
