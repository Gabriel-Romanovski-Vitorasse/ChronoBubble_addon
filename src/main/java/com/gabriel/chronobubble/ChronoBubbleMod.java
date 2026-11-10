package com.gabriel.chronobubble;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Addon: esfera de tempo com an\u00e9is ao redor de entidades sob o efeito Chrono Stasis. */
@Mod(ChronoBubbleMod.MODID)
public class ChronoBubbleMod {
    public static final String MODID = "chrono_bubble";
    private static final String PROTOCOL = "1";

    /** Canal usado para o servidor avisar os clientes de quem est\u00e1 congelado. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> PROTOCOL, v -> true, v -> true);

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> STASIS_START = SOUNDS.register("stasis_start",
            () -> new SoundEvent(new ResourceLocation(MODID, "stasis_start")));
    public static final RegistryObject<SoundEvent> STASIS_END = SOUNDS.register("stasis_end",
            () -> new SoundEvent(new ResourceLocation(MODID, "stasis_end")));

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, MODID);
    public static final RegistryObject<EntityType<StasisOrbEntity>> STASIS_ORB = ENTITIES.register("stasis_orb",
            () -> EntityType.Builder.<StasisOrbEntity>of(StasisOrbEntity::new, EntityClassification.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(10).build(MODID + ":stasis_orb"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> SWALLOWTAIL = ITEMS.register("swallowtail", SwallowtailItem::new);

    public ChronoBubbleMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ChronoConfig.SPEC, "chrono_bubble-common.toml");
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        CHANNEL.registerMessage(0, StasisPacket.class, StasisPacket::encode, StasisPacket::decode, StasisPacket::handle);
        CHANNEL.registerMessage(1, RebirthPacket.class, RebirthPacket::encode, RebirthPacket::decode, RebirthPacket::handle);
        CHANNEL.registerMessage(2, TimeGainPacket.class, TimeGainPacket::encode, TimeGainPacket::decode, TimeGainPacket::handle);
        CHANNEL.registerMessage(3, UnstablePacket.class, UnstablePacket::encode, UnstablePacket::decode, UnstablePacket::handle);
        CHANNEL.registerMessage(4, ZoneTogglePacket.class, ZoneTogglePacket::encode, ZoneTogglePacket::decode, ZoneTogglePacket::handle);
        CHANNEL.registerMessage(5, ZoneStatePacket.class, ZoneStatePacket::encode, ZoneStatePacket::decode, ZoneStatePacket::handle);
        CHANNEL.registerMessage(6, HoldKeyPacket.class, HoldKeyPacket::encode, HoldKeyPacket::decode, HoldKeyPacket::handle);
        CHANNEL.registerMessage(7, ProjectileOrbPacket.class, ProjectileOrbPacket::encode, ProjectileOrbPacket::decode, ProjectileOrbPacket::handle);
        CHANNEL.registerMessage(8, PrecogPacket.class, PrecogPacket::encode, PrecogPacket::decode, PrecogPacket::handle);
        CHANNEL.registerMessage(9, AccelTogglePacket.class, AccelTogglePacket::encode, AccelTogglePacket::decode, AccelTogglePacket::handle);
        CHANNEL.registerMessage(10, HopGhostPacket.class, HopGhostPacket::encode, HopGhostPacket::decode, HopGhostPacket::handle);
        ChronoThrowHandler.register();
    }
}
