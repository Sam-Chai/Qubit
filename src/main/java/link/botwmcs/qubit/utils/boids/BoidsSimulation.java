package link.botwmcs.qubit.utils.boids;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// https://github.com/Tomate0613/boids/blob/main/src/main/java/dev/doublekekse/boids/BoidsSimulation.java
// 避免对零向量
public record BoidsSimulation(
        float separationInfluence,
        float separationRange,
        float separationAngle,   // 这里建议传入的是 cos(角度) 阈值

        float alignmentInfluence,
        float alignmentAngle,    // cos(角度)

        float cohesionInfluence,
        float cohesionAngle,     // cos(角度)

        float randomness
) {
    /** 将角度(度)转换为其余弦值，便于与 dot 做阈值比较 */
    public static float cosDeg(float angleDeg) {
        return (float) Math.cos(Math.toRadians(angleDeg));
    }

    public Vec3 apply(Mob mob, List<? extends Mob> nearbyMobs) {
        Vec3 separation = Vec3.ZERO;
        Vec3 alignment  = Vec3.ZERO;
        Vec3 cohesion   = Vec3.ZERO;

        var rng = mob.getRandom();
        Vec3 random = new Vec3(
                rng.nextGaussian() * randomness,
                rng.nextGaussian() * randomness,
                rng.nextGaussian() * randomness
        );

        int alignmentCount = 0;
        int cohesionCount  = 0;

        Vec3 mobPos   = mob.position();
        Vec3 mobLookN = safeNormalize(mob.getLookAngle()); // 视线单位向量

        for (Mob other : nearbyMobs) {
            if (mob == other) continue;

            Vec3 delta = other.position().subtract(mobPos);
            double dist = Math.max(1.0e-5, delta.length());
            Vec3 deltaN = delta.scale(1.0 / dist); // 手动归一，避免零长度 normalize()

            // 夹角用点积阈值（单位向量点积 = cosθ）
            double angleCos = mobLookN.dot(deltaN);

            if (dist < separationRange && angleCos >= separationAngle) {
                // 越近推斥越强，距离超过 separationRange 则为 0
                separation = separation.add(deltaN.scale(-((1.0 / dist) - (1.0 / separationRange))));
            }

            if (angleCos >= alignmentAngle) {
                Vec3 vel = other.getDeltaMovement();
                Vec3 velN = safeNormalize(vel);
                if (!velN.equals(Vec3.ZERO)) {
                    alignment = alignment.add(velN);
                    alignmentCount++;
                }
            }

            if (angleCos >= cohesionAngle) {
                cohesion = cohesion.add(other.position());
                cohesionCount++;
            }
        }

        if (alignmentCount > 0) {
            alignment = alignment.scale(1.0 / alignmentCount);
        } else {
            alignment = Vec3.ZERO;
        }

        if (cohesionCount > 0) {
            cohesion = cohesion.scale(1.0 / cohesionCount).subtract(mobPos);
        } else {
            cohesion = Vec3.ZERO;
        }

        return alignment.scale(alignmentInfluence)
                .add(separation.scale(separationInfluence))
                .add(cohesion.scale(cohesionInfluence))
                .add(random);
    }

    private static Vec3 safeNormalize(Vec3 v) {
        double len = v.length();
        if (len < 1.0e-8) return Vec3.ZERO;
        return v.scale(1.0 / len);
    }


}
