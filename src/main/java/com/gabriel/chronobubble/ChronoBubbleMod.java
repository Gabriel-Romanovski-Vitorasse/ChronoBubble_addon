package com.gabriel.chronobubble;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Addon: esfera de tempo com an\u00e9is ao redor de entidades sob o efeito Chrono Stasis. */
@Mod(ChronoBubbleMod.MODID)
public class ChronoBubbleMod {
    public static final String MODID = "chrono_bubble";
    /**
     * Vers\u00e3o do protocolo de rede. Suba o n\u00famero sempre que um pacote mudar de formato.
     * Cliente e servidor precisam ter o addon na MESMA vers\u00e3o: quem n\u00e3o tiver (ou tiver outra) \u00e9 recusado ao
     * entrar, com uma mensagem clara, em vez de entrar e receber pacotes que n\u00e3o entende.
     */
    private static final String PROTOCOL = "3";

    /** Canal usado para o servidor avisar os clientes de quem est\u00e1 congelado. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> STASIS_START = SOUNDS.register("stasis_start",
            () -> new SoundEvent(new ResourceLocation(MODID, "stasis_start")));
    /**
     * Proj\u00e9til desfeito pela zona (tempo acelerado). Usa o som de stasis_end mais agudo (sounds.json); para trocar,
     * ponha um .ogg em assets/chrono_bubble/sounds/ e aponte "time_dissolve" para ele.
     */
    public static final RegistryObject<SoundEvent> TIME_DISSOLVE = SOUNDS.register("time_dissolve",
            () -> new SoundEvent(new ResourceLocation(MODID, "time_dissolve")));
    public static final RegistryObject<SoundEvent> STASIS_END = SOUNDS.register("stasis_end",
            () -> new SoundEvent(new ResourceLocation(MODID, "stasis_end")));

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, MODID);
    public static final RegistryObject<EntityType<StasisOrbEntity>> STASIS_ORB = ENTITIES.register("stasis_orb",
            () -> EntityType.Builder.<StasisOrbEntity>of(StasisOrbEntity::new, EntityClassification.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(10).build(MODID + ":stasis_orb"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> SWALLOWTAIL = ITEMS.register("swallowtail", SwallowtailItem::new);

    public ChronoBubbleMod() {
        ChronoConfig.register();
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLCommonSetupEvent e) -> BlockCloverBridge.logStatus());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ChronoCommand.register(e.getDispatcher()));
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
        CHANNEL.registerMessage(11, RotPacket.class, RotPacket::encode, RotPacket::decode, RotPacket::handle);
        ChronoThrowHandler.register();
        MinecraftForge.EVENT_BUS.register(ChronoZone.INSTANCE);         // habilidades com mana por segundo
        MinecraftForge.EVENT_BUS.register(TimeAcceleration.INSTANCE);
    }
}
