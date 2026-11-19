package com.gabriel.chronobubble;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Mago do Tempo: NPC de TESTE que usa as magias de tempo do ChronoBubble em jogadores, para ver como elas ficam do
 * lado de quem recebe. Só aparece pelo comando {@code /chronobubble npc} (operadores). Parado, olha para o jogador
 * mais perto e, a cada {@link #CAST_EVERY} ticks, lança a próxima magia da roda:
 * <ol>
 *   <li>Chrono Stasis direto (congela; no fim, o tempo avança);</li>
 *   <li>esfera de Chrono Stasis arremessada (idem);</li>
 *   <li>apodrecimento (o golpe do Alt + Time Steal).</li>
 * </ol>
 * Ele não tem os dados do Block Clover: o "Time" dele é de mentira ({@code virtualTime}, só para o tempo
 * avançado) e nada é cobrado. Só mata pelo tempo avançado se for criado como letal.
 */
public class TimeMageEntity extends CreatureEntity {
    static final int CAST_EVERY = 120;
    static final double RANGE = 16.0D;

    private int virtualTime = 30;
    private boolean lethal;
    private int nextSpell;
    private int castTimer = 60;

    public TimeMageEntity(EntityType<? extends TimeMageEntity> type, World world) {
        super(type, world);
        setPersistenceRequired();
        setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.BOOK));
    }

    public static AttributeModifierMap.MutableAttribute attributes() {
        return CreatureEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, RANGE * 2.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new SwimGoal(this));
        goalSelector.addGoal(1, new LookAtGoal(this, PlayerEntity.class, (float) RANGE * 2.0F, 1.0F));
        goalSelector.addGoal(2, new LookRandomlyGoal(this));
    }

    public int virtualTime() {
        return virtualTime;
    }

    public boolean isLethal() {
        return lethal;
    }

    void configure(int time, boolean lethal) {
        this.virtualTime = Math.max(0, time);
        this.lethal = lethal;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level.isClientSide) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, getRandomX(0.6D), getRandomY(), getRandomZ(0.6D), 0.0D, 0.05D, 0.0D);
            }
            return;
        }
        if (--castTimer > 0) return;
        castTimer = CAST_EVERY;
        PlayerEntity target = level.getNearestPlayer(this, RANGE);
        if (target == null || target.isSpectator() || !canSee(target)) return;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        swing(net.minecraft.util.Hand.MAIN_HAND);
        switch (nextSpell) {
            case 0: freeze(target); break;
            case 1: throwOrb(target); break;
            default: rot(target); break;
        }
        nextSpell = (nextSpell + 1) % 3;
    }

    private int freezeTicks() {
        return (int) Math.round(ChronoConfig.THROW_FREEZE_SECONDS.get() * 20.0D);
    }

    /** Chrono Stasis direto no alvo (o efeito do Block Clover). */
    private void freeze(PlayerEntity target) {
        Effect effect = BlockCloverBridge.stasisEffect();
        if (effect == null) {
            Msg.bar(target, TextFormatting.RED, "mage_no_block_clover");
            return;
        }
        if (target.hasEffect(effect)) return;
        target.addEffect(new EffectInstance(effect, freezeTicks()));
        if (target.hasEffect(effect)) TimeAging.markByMage(target, this);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ChronoBubbleMod.STASIS_START.get(),
                SoundCategory.HOSTILE, 1.0F, 1.0F);
        ((ServerWorld) level).sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0D, target.getZ(),
                40, 0.5D, 0.8D, 0.5D, 0.5D);
        Msg.bar(target, TextFormatting.GOLD, "mage_cast_freeze", getDisplayName());
    }

    /** Esfera de Chrono Stasis arremessada no alvo. */
    private void throwOrb(LivingEntity target) {
        StasisOrbEntity orb = new StasisOrbEntity(ChronoBubbleMod.STASIS_ORB.get(), level, this);
        double dx = target.getX() - getX(), dy = target.getY(0.5D) - orb.getY(), dz = target.getZ() - getZ();
        orb.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.05D, dz, ChronoConfig.f(ChronoConfig.THROW_SPEED), 1.0F);
        level.addFreshEntity(orb);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_PEARL_THROW, SoundCategory.HOSTILE, 0.8F, 1.3F);
        if (target instanceof PlayerEntity) Msg.bar((PlayerEntity) target, TextFormatting.GOLD, "mage_cast_orb", getDisplayName());
    }

    /** Apodrecimento (o golpe do Alt + Time Steal), com o dano e a duração da config. */
    private void rot(PlayerEntity target) {
        int ticks = (int) Math.round(ChronoConfig.ROT_STEAL_DURATION.get() * 20.0D);
        TimeRot.startBy(target, this, ChronoConfig.f(ChronoConfig.ROT_STEAL_DAMAGE), ticks);
        Msg.bar(target, TextFormatting.GOLD, "mage_cast_rot", getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("VirtualTime", virtualTime);
        nbt.putBoolean("Lethal", lethal);
        nbt.putInt("NextSpell", nextSpell);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT nbt) {
        super.readAdditionalSaveData(nbt);
        virtualTime = nbt.getInt("VirtualTime");
        lethal = nbt.getBoolean("Lethal");
        nextSpell = nbt.getInt("NextSpell");
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
