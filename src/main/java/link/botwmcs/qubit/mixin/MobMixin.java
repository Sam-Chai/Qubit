package link.botwmcs.qubit.mixin;

import link.botwmcs.qubit.Config;
import link.botwmcs.qubit.modules.boids.BoidsRuntime;
import link.botwmcs.qubit.modules.boids.MobDuck;
import link.botwmcs.qubit.modules.boids.goals.LimitSpeedAndLookInVelocityDirectionGoal;
import link.botwmcs.qubit.modules.boids.goals.StayInWaterGoal;
import link.botwmcs.qubit.utils.boids.NearbyMobTracker;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity implements MobDuck {
    @Unique
    NearbyMobTracker boids$nearbyMobs;
    @Unique
    Goal boids$stayInWaterGoal;
    @Unique
    Goal boids$limitSpeedGoal;

    @Unique
    private static final EntityTypePredicate IS_WATER_MOB = EntityTypePredicate.of(EntityTypeTags.AQUATIC);

    protected MobMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);

    }

    @Inject(method = "<init>", at = @At("TAIL"))
    void init(EntityType<?> entityType, Level level, CallbackInfo ci) {
        if (!BoidsRuntime.isAffected(this)) {
            return;
        }

        if (!BoidsRuntime.isEnabled()) {
            return;
        }

        boids$enable();
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    void serverAiStep(CallbackInfo ci) {
        if (boids$nearbyMobs == null) { // Not affected
            return;
        }

        ci.cancel();

        addDeltaMovement(BoidsRuntime.SETTINGS.apply((Mob) (Object) this, boids$nearbyMobs.tick()));

        if (boids$stayInWaterGoal != null) {
            boids$stayInWaterGoal.tick();
        }

        boids$limitSpeedGoal.tick();
    }

    @Override
    public void boids$enable() {
        if (IS_WATER_MOB.matches(getType())) {
            boids$stayInWaterGoal = new StayInWaterGoal((Mob) (Object) this);
        }


        boids$limitSpeedGoal = new LimitSpeedAndLookInVelocityDirectionGoal((Mob) (Object) this, BoidsRuntime.MIN_SPEED, BoidsRuntime.MAX_SPEED);
        boids$nearbyMobs = new NearbyMobTracker((Mob) (Object) this);
    }

    @Override
    public void boids$disable() {
        boids$stayInWaterGoal = null;
        boids$limitSpeedGoal = null;
        boids$nearbyMobs = null;
    }

}
