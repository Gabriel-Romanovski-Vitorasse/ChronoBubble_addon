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
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
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
