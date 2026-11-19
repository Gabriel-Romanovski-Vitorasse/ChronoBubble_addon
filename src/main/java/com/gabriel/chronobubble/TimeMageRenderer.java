package com.gabriel.chronobubble;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Desenha o Mago do Tempo com o modelo de jogador. A skin vem de {@code config/chrono_bubble/time_mage.png}
 * (64x64, formato de skin de jogador) se o arquivo existir neste computador; senão, a skin padrão do Minecraft.
 * O addon não traz skin de personagem nenhum: cada um coloca a sua.
 */
public class TimeMageRenderer extends BipedRenderer<TimeMageEntity, PlayerModel<TimeMageEntity>> {
    private static final ResourceLocation CUSTOM = new ResourceLocation(ChronoBubbleMod.MODID, "dynamic/time_mage");
    private static ResourceLocation skin;

    public TimeMageRenderer(EntityRendererManager manager) {
        super(manager, new PlayerModel<>(0.0F, false), 0.5F);
    }

    /** Caminho da skin personalizada. */
    public static Path skinFile() {
        return FMLPaths.CONFIGDIR.get().resolve(ChronoBubbleMod.MODID).resolve("time_mage.png");
    }

    @Override
    public ResourceLocation getTextureLocation(TimeMageEntity entity) {
        if (skin == null) skin = loadSkin();
        return skin;
    }

    private static ResourceLocation loadSkin() {
        Path file = skinFile();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                Minecraft.getInstance().getTextureManager().register(CUSTOM, new DynamicTexture(image));
                return CUSTOM;
            } catch (Exception e) {
                LogManager.getLogger().warn("[ChronoBubble] Nao consegui ler a skin do Mago do Tempo em {}: {}", file, e.toString());
            }
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
