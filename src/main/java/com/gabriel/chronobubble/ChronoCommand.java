package com.gabriel.chronobubble;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /chronobubble status [jogador]}: mostra o estado do addon para um jogador (Time, mana, renascimento,
 * Swallowtail e habilidades ativas). Sem argumento, mostra o seu; ver o de outro jogador exige permissão de operador.
 * Útil para testar e para entender o que está acontecendo num servidor.
 */
public final class ChronoCommand {
    private ChronoCommand() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal(ChronoBubbleMod.MODID.replace("_", ""))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("npc").requires(src -> src.hasPermission(2))
                        .executes(ctx -> spawnMage(ctx.getSource(), null, 30, false))
                        .then(Commands.literal("remove").executes(ctx -> removeMages(ctx.getSource())))
                        .then(Commands.argument("time", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100000))
                                .executes(ctx -> spawnMage(ctx.getSource(), null,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "time"), false))
                                .then(Commands.argument("lethal", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> spawnMage(ctx.getSource(), null,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "time"),
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "lethal")))
                                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> spawnMage(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "time"),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "lethal"))))))));
    }

    /**
     * {@code /chronobubble npc [time] [lethal] [nome]}: cria o Mago do Tempo de teste onde o jogador est\u00e1 olhando
     * (2 blocos \u00e0 frente). {@code time} \u00e9 o Time de mentira dele para o tempo avan\u00e7ado (padr\u00e3o 30);
     * {@code lethal} deixa o tempo avan\u00e7ado matar.
     */
    private static int spawnMage(CommandSource src, String name, int time, boolean lethal) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrException();
        TimeMageEntity mage = ChronoBubbleMod.TIME_MAGE.get().create(p.level);
        if (mage == null) return 0;
        net.minecraft.util.math.vector.Vector3d look = p.getViewVector(1.0F);
        mage.moveTo(p.getX() + look.x * 2.0D, p.getY(), p.getZ() + look.z * 2.0D, p.yRot + 180.0F, 0.0F);
        mage.configure(time, lethal);
        mage.setCustomName(name == null ? new net.minecraft.util.text.TranslationTextComponent("entity.chrono_bubble.time_mage")
                : new StringTextComponent(name));
        mage.setCustomNameVisible(true);
        p.level.addFreshEntity(mage);
        src.sendSuccess(Msg.tr(TextFormatting.AQUA, "mage_spawned", time, lethal ? Msg.tr(TextFormatting.RED, "mage_lethal")
                : Msg.tr(TextFormatting.GREEN, "mage_safe")), false);
        return 1;
    }

    /** {@code /chronobubble npc remove}: tira os Magos do Tempo num raio de 64 blocos. */
    private static int removeMages(CommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrException();
        java.util.List<TimeMageEntity> list = p.level.getEntitiesOfClass(TimeMageEntity.class, p.getBoundingBox().inflate(64.0D));
        for (TimeMageEntity m : list) m.remove();
        src.sendSuccess(Msg.tr(TextFormatting.YELLOW, "mage_removed", list.size()), false);
        return list.size();
    }

    private static int status(CommandSource src, ServerPlayerEntity p) throws CommandSyntaxException {
        src.sendSuccess(Msg.tr(TextFormatting.AQUA, "status_header", p.getDisplayName()), false);
        if (!BlockCloverBridge.isAvailable()) {
            src.sendSuccess(Msg.tr(TextFormatting.RED, "status_no_block_clover"), false);
            return 1;
        }
        line(src, "status_time_user", yesNo(BlockCloverBridge.isTimeUser(p)));
        line(src, "status_time", BlockCloverBridge.getTime(p), ChronoConfig.MAX_TIME.get() > 0
                ? String.valueOf(ChronoConfig.MAX_TIME.get()) : "∞");
        line(src, "status_mana", Msg.f1(BlockCloverBridge.getMana(p)));
        line(src, "status_combat", yesNo(BlockCloverBridge.isInCombat(p)));
        line(src, "status_stasis", yesNo(BlockCloverBridge.hasChronoStasis(p)));
        line(src, "status_swallowtail", SwallowtailItem.totalStored(p));
        line(src, "status_magic_zone", yesNo(BlockCloverBridge.hasTimeMagicZone(p)));
        line(src, "status_ressurection", yesNo(BlockCloverBridge.isRessurectionReady(p)));
        line(src, "status_reborn", RebirthHandler.isReborn(p)
                ? Msg.tr(TextFormatting.YELLOW, "status_reborn_yes", ChronoConfig.REBIRTH_UNLOCK_TIME.get())
                : yesNo(false));
        line(src, "status_zone", yesNo(ChronoZone.isActive(p)));
        line(src, "status_accel", yesNo(TimeAcceleration.isActive(p)));
        line(src, "status_sustained", SustainedStasis.targetCount(p));
        return 1;
    }

    private static void line(CommandSource src, String key, Object... args) {
        src.sendSuccess(new StringTextComponent(" - ").withStyle(TextFormatting.DARK_GRAY)
                .append(Msg.tr(TextFormatting.GRAY, key, args)), false);
    }

    private static IFormattableTextComponent yesNo(boolean b) {
        return b ? Msg.tr(TextFormatting.GREEN, "yes") : Msg.tr(TextFormatting.RED, "no");
    }
}
