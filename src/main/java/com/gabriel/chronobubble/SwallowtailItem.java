package com.gabriel.chronobubble;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Swallowtail: artefato que armazena Time. Clique direito guarda Time nele; Shift + clique direito
 * devolve Time para voc\u00ea. A capacidade padr\u00e3o \u00e9 5000 (configur\u00e1vel).
 * Ao morrer, o Time guardado \u00e9 o que permite renascer (ver RebirthHandler): ele \u00e9 gasto por inteiro.
 */
public class SwallowtailItem extends Item {
    private static final String KEY = "StoredTime";

    public SwallowtailItem() {
        super(new Item.Properties().stacksTo(1).tab(ItemGroup.TAB_MISC).rarity(Rarity.EPIC));
    }

    public static int getStored(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KEY);
    }

    static void setStored(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(KEY, Math.max(0, Math.min(ChronoConfig.SWALLOW_CAPACITY.get(), value)));
    }

    /**
     * Esvazia todas as Swallowtails que o jogador carrega (invent\u00e1rio, m\u00e3o secund\u00e1ria e armadura).
     * Usado ao morrer/renascer. Devolve quanto Time havia guardado no total.
     */
    public static int drainAll(PlayerEntity player) {
        int total = 0;
        for (ItemStack s : carried(player)) {
            if (!(s.getItem() instanceof SwallowtailItem)) continue;
            int stored = getStored(s);
            if (stored <= 0) continue;
            total += stored;
            setStored(s, 0);
        }
        return total;
    }

    /** Soma do Time guardado em todas as Swallowtails que o jogador carrega. */
    public static int totalStored(PlayerEntity player) {
        int total = 0;
        for (ItemStack s : carried(player)) if (s.getItem() instanceof SwallowtailItem) total += getStored(s);
        return total;
    }

    /** Invent\u00e1rio, m\u00e3o secund\u00e1ria e armadura. */
    private static List<ItemStack> carried(PlayerEntity player) {
        List<ItemStack> all = new java.util.ArrayList<>();
        all.addAll(player.inventory.items);
        all.addAll(player.inventory.offhand);
        all.addAll(player.inventory.armor);
        return all;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) return ActionResult.success(stack);

        if (!BlockCloverBridge.isTimeUser(player)) {
            Msg.bar(player, TextFormatting.GRAY, "swallow_not_time_user");
            return ActionResult.fail(stack);
        }
        int stored = getStored(stack);
        int time = Math.max(0, BlockCloverBridge.getTime(player));
        boolean withdraw = player.isShiftKeyDown();
        if (withdraw && RebirthHandler.isReborn(player)) {
            // renascido: o Time s\u00f3 volta roubando, ent\u00e3o n\u00e3o d\u00e1 para sacar da Swallowtail
            Msg.bar(player, TextFormatting.RED, "swallow_reborn_locked");
            return ActionResult.fail(stack);
        }
        int amount = withdraw ? Math.min(ChronoConfig.SWALLOW_TRANSFER.get(), stored)
                              : Math.min(ChronoConfig.SWALLOW_TRANSFER.get(), Math.min(time, ChronoConfig.SWALLOW_CAPACITY.get() - stored));
        if (amount <= 0) {
            Msg.bar(player, TextFormatting.GRAY, withdraw ? "swallow_empty" : (stored >= ChronoConfig.SWALLOW_CAPACITY.get() ? "swallow_full" : "swallow_no_time"));
            return ActionResult.fail(stack);
        }

        if (withdraw) {
            setStored(stack, stored - amount);
            BlockCloverBridge.setTime(player, time + amount);
        } else {
            setStored(stack, stored + amount);
            BlockCloverBridge.setTime(player, time - amount);
        }
        BlockCloverBridge.syncToClient(player);
        if (getStored(stack) >= ChronoConfig.SWALLOW_CAPACITY.get()) ChronoAdvancements.grant(player, ChronoAdvancements.FULL_SWALLOWTAIL);
        player.getCooldowns().addCooldown(this, 4);

        Msg.bar(player, withdraw ? TextFormatting.GREEN : TextFormatting.AQUA,
                withdraw ? "swallow_withdraw" : "swallow_deposit", getStored(stack), ChronoConfig.SWALLOW_CAPACITY.get(), amount);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundCategory.PLAYERS, 0.7F, withdraw ? 1.5F : 0.8F);
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2D, player.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.5D);
        }
        return ActionResult.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(Msg.tr(TextFormatting.AQUA, "swallow_tip_stored", getStored(stack), ChronoConfig.SWALLOW_CAPACITY.get()));
        tooltip.add(Msg.tr(TextFormatting.GRAY, "swallow_tip_deposit", ChronoConfig.SWALLOW_TRANSFER.get()));
        tooltip.add(Msg.tr(TextFormatting.GRAY, "swallow_tip_withdraw", ChronoConfig.SWALLOW_TRANSFER.get()));
        tooltip.add(Msg.tr(TextFormatting.DARK_AQUA, "swallow_tip_rebirth"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStored(stack) > 0;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getStored(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0D - (double) getStored(stack) / Math.max(1, ChronoConfig.SWALLOW_CAPACITY.get());
    }

    @Override
    public int getRGBDurabilityForDisplay(ItemStack stack) {
        return 0x4FD0FF;
    }
}
