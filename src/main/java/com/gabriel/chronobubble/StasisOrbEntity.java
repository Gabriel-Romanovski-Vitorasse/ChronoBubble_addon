package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.network.IPacket;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Esfera de Chrono Stasis arremessada: congela um \u00fanico alvo vivo que ela acertar. */
public class StasisOrbEntity extends ThrowableEntity {
    /** Dura\u00e7\u00e3o do congelamento, igual \u00e0 do soco original (100 ticks = 5 s). */
    public static int FREEZE_TICKS = 100;
    private static final int MAX_LIFE_TICKS = 80;
    private static final ResourceLocation EFFECT_ID = new ResourceLocation("block_clover", "chrono_stasis");

    /** S\u00f3 no servidor: esfera lan\u00e7ada com o rel\u00f3gio na m\u00e3o (congelamento sustentado). */
    public boolean sustained;
    /** S\u00f3 no servidor: esfera de Time Steal (converte vida do alvo em Time do dono). */
    public boolean steal;

    public StasisOrbEntity(EntityType<? extends StasisOrbEntity> type, World world) {
        super(type, world);
    }

    public StasisOrbEntity(EntityType<? extends StasisOrbEntity> type, World world, LivingEntity owner) {
        super(type, owner, world);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected float getGravity() {
        return 0.01F;      // voa quase reto
    }

    @Override
    public void tick() {
        super.tick();
        if (!level.isClientSide && tickCount > MAX_LIFE_TICKS) remove();
    }

    @Override
    protected void onHitEntity(EntityRayTraceResult result) {
        super.onHitEntity(result);
        if (level.isClientSide) return;
        Entity hit = result.getEntity();
        if (!(hit instanceof LivingEntity) || hit == getOwner()) return;
        Effect effect = ForgeRegistries.POTIONS.getValue(EFFECT_ID);
        if (effect == null) return;
        LivingEntity target = (LivingEntity) hit;
        if (steal && getOwner() instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            ChronoThrowHandler.stealLife((net.minecraft.entity.player.ServerPlayerEntity) getOwner(), this, target);
            return;
        }
        if (sustained && getOwner() instanceof net.minecraft.entity.player.ServerPlayerEntity) {
            SustainedStasis.begin((net.minecraft.entity.player.ServerPlayerEntity) getOwner(), target);
            return;
        }
        if (!target.hasEffect(effect)) {
            target.addEffect(new EffectInstance(effect, FREEZE_TICKS));
        }
    }

    @Override
    protected void onHit(RayTraceResult result) {
        super.onHit(result);
        if (!level.isClientSide) remove();       // acertou algo (mob ou bloco): a esfera se desfaz
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
