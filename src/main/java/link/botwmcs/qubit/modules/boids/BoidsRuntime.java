package link.botwmcs.qubit.modules.boids;

import link.botwmcs.qubit.Config;
import link.botwmcs.qubit.utils.boids.BoidsSimulation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public final class BoidsRuntime {
    private BoidsRuntime() {}

    // Separation controls. All angles are in degrees
    public static final float SEPARATION_INFLUENCE = 0.6f;
    public static final float SEPARATION_RANGE     = 2.5f;
    public static final float SEPARATION_ANGLE_DEG = 70f;

    // Alignment controls
    public static final float ALIGNMENT_INFLUENCE  = 0.4f;
    public static final float ALIGNMENT_ANGLE_DEG  = 100f;

    // Cohesion controls
    public static final float COHESION_INFLUENCE   = 0.4f;
    public static final float COHESION_ANGLE_DEG   = 70f;

    // Speed limits
    public static final float MIN_SPEED            = 0.2f;
    public static final float MAX_SPEED            = 0.3f;

    // Random influence
    public static final float RANDOMNESS           = 0.005f;

    /** 是否启用 Boids（由配置驱动） */
    private static volatile boolean ENABLED = true;

    // ======= 默认实体配置（常量化） =======

    public enum DefaultEntities {
        DEFAULT(List.of(EntityType.SALMON, EntityType.COD, EntityType.TROPICAL_FISH)),
        NONE(Collections.emptyList());

        public final Collection<EntityType<?>> types;

        DefaultEntities(Collection<EntityType<?>> types) {
            this.types = types;
        }
    }

    /** 额外包含/排除的实体（写死为可编辑常量） */
//    public static final List<String> INCLUDED_ENTITIES_IDS = List.of(
//            // 例： "minecraft:salmon"
//    );
    public static final List<String> EXCLUDED_ENTITIES_IDS = List.of(
            // 例： "minecraft:cod"
    );

    /** 哪组默认实体生效（写死） */
    public static final DefaultEntities DEFAULT_ENTITIES = DefaultEntities.DEFAULT;

    // ======= 从常量派生出的运行期对象 =======
    public static final BoidsSimulation SETTINGS = new BoidsSimulation(
            SEPARATION_INFLUENCE,
            SEPARATION_RANGE,
            cosDeg(SEPARATION_ANGLE_DEG),

            ALIGNMENT_INFLUENCE,
            cosDeg(ALIGNMENT_ANGLE_DEG),

            COHESION_INFLUENCE,
            cosDeg(COHESION_ANGLE_DEG),

            RANDOMNESS
    );

    private static float cosDeg(float deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }

    /** 受影响实体集合（默认 + include – exclude），在类加载时根据注册表计算一次 */
    public static final Set<EntityType<?>> AFFECTED_ENTITIES = computeAffectedEntities();

    // ======= 工具方法 =======
    private static Set<EntityType<?>> computeAffectedEntities() {
        ENABLED = Boolean.TRUE.equals(Config.BOID_FEATURES.get());
        List<EntityType<?>> base = new ArrayList<>(DEFAULT_ENTITIES.types);
        base.addAll(resolveEntityTypes(Config.BOID_MOBS.get()));
        base.removeAll(resolveEntityTypes(EXCLUDED_ENTITIES_IDS));
        return Set.copyOf(base);
    }

    private static Collection<EntityType<?>> resolveEntityTypes(List<? extends String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream()
                .map(ResourceLocation::tryParse)
                .filter(Objects::nonNull)
                .map(BuiltInRegistries.ENTITY_TYPE::getOptional)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** 是否属于受影响实体（便于直接调用） */
    public static boolean isAffected(Entity entity) {
        return entity != null && AFFECTED_ENTITIES.contains(entity.getType());
    }



}
