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
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Swallowtail: artefato que armazena Time. Clique direito guarda Time nele; Shift + clique direito
 * devolve Time para voc\u00ea. A capacidade padr\u00e3o \u00e9 5000 (configur\u00e1vel).
 */
public class SwallowtailItem extends Item {
    // ===== Ajustes f\u00e1ceis (o .toml tem prioridade) =====
    public static int CAPACITY = 5000;
    /** Quanto de Time passa por clique. */
    public static int TRANSFER_PER_USE = 100;

    private static final String KEY = "StoredTime";

    public SwallowtailItem() {
        super(new Item.Properties().stacksTo(1).tab(ItemGroup.TAB_MISC).rarity(Rarity.EPIC));
    }

    public static int getStored(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KEY);
    }

    static void setStored(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(KEY, Math.max(0, Math.min(CAPACITY, value)));
    }

    /**
     * Tira at\u00e9 {@code cost} de Time dos Swallowtails que o jogador carrega (usado ao renascer).
     * Devolve quanto foi realmente tirado.
     */
    public static int consumeReserve(PlayerEntity player, int cost) {
        int left = cost;
        List<ItemStack> all = new java.util.ArrayList<>();
        all.addAll(player.inventory.items);
        all.addAll(player.inventory.offhand);
        all.addAll(player.inventory.armor);
        for (ItemStack s : all) {
            if (left <= 0) break;
            if (!(s.getItem() instanceof SwallowtailItem)) continue;
            int take = Math.min(left, getStored(s));
            if (take > 0) {
                setStored(s, getStored(s) - take);
                left -= take;
            }
        }
        return cost - left;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) return ActionResult.success(stack);

        if (!BlockCloverBridge.isTimeUser(player)) {
            player.displayClientMessage(new StringTextComponent("\u00a77O Swallowtail s\u00f3 responde a quem usa magia de Tempo."), true);
            return ActionResult.fail(stack);
        }
        int stored = getStored(stack);
        int time = Math.max(0, BlockCloverBridge.getTime(player));
        boolean withdraw = player.isShiftKeyDown();
        int amount = withdraw ? Math.min(TRANSFER_PER_USE, stored)
                              : Math.min(TRANSFER_PER_USE, Math.min(time, CAPACITY - stored));
        if (amount <= 0) {
            player.displayClientMessage(new StringTextComponent(withdraw ? "\u00a77O Swallowtail est\u00e1 vazio."
                    : (stored >= CAPACITY ? "\u00a77O Swallowtail est\u00e1 cheio." : "\u00a77Voc\u00ea n\u00e3o tem Time para guardar.")), true);
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
        player.getCooldowns().addCooldown(this, 4);

        player.displayClientMessage(new StringTextComponent("\u00a7bSwallowtail \u00a77" + getStored(stack) + " / " + CAPACITY
                + (withdraw ? "  \u00a7a+" : "  \u00a7c-") + amount + " Time"), true);
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
        tooltip.add(new StringTextComponent("\u00a7bTempo armazenado: \u00a7f" + getStored(stack) + " / " + CAPACITY));
        tooltip.add(new StringTextComponent("\u00a77Clique direito: guardar " + TRANSFER_PER_USE + " de Time"));
        tooltip.add(new StringTextComponent("\u00a77Shift + clique direito: devolver " + TRANSFER_PER_USE + " de Time"));
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
        return 1.0D - (double) getStored(stack) / Math.max(1, CAPACITY);
    }

    @Override
    public int getRGBDurabilityForDisplay(ItemStack stack) {
        return 0x4FD0FF;
    }
}
