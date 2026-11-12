package com.gabriel.chronobubble;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registra o renderer da esfera arremess\u00e1vel (s\u00f3 no cliente). */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientZone.registerKey();
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.fml.ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parent) -> new ChronoConfigScreen(parent));
        event.enqueueWork(() -> net.minecraft.item.ItemModelsProperties.register(ChronoBubbleMod.SWALLOWTAIL.get(),
                new net.minecraft.util.ResourceLocation(ChronoBubbleMod.MODID, "charge"),
                (stack, world, entity) -> Math.min(1.0F, SwallowtailItem.getStored(stack) / (float) Math.max(1, ChronoConfig.SWALLOW_CAPACITY.get()))));
        RenderingRegistry.registerEntityRenderingHandler(ChronoBubbleMod.STASIS_ORB.get(), StasisOrbRenderer::new);
    }
}
