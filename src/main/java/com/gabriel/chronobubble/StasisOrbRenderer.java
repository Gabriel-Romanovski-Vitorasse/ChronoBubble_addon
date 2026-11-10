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

    public StasisOrbRenderer(EntityRendererManager manager) {
        super(manager);
    }

    @Override
    public void render(StasisOrbEntity entity, float yaw, float partialTicks, MatrixStack stack,
                       IRenderTypeBuffer buffers, int light) {
        float time = entity.tickCount + partialTicks;
        stack.pushPose();
        stack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        StasisBubbleRenderer.drawSphere(stack,
                buffers.getBuffer(RenderType.entityTranslucent(StasisBubbleRenderer.SPHERE_TEX)), RADIUS, 0.85F);
        stack.mulPose(Vector3f.ZP.rotationDegrees(30.0F));
        stack.mulPose(Vector3f.YP.rotationDegrees(time * 14.0F));
        StasisBubbleRenderer.drawRing(stack,
                buffers.getBuffer(RenderType.entityTranslucent(StasisBubbleRenderer.RING_TEX)),
                RADIUS * 1.15F, RADIUS * 0.55F, 0.95F);
        stack.popPose();
        super.render(entity, yaw, partialTicks, stack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StasisOrbEntity entity) {
        return StasisBubbleRenderer.SPHERE_TEX;
    }
}
