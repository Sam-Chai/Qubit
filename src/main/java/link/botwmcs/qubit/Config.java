package link.botwmcs.qubit;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue MODERN_SHOWTIME = BUILDER
            .comment("Enable modern context feature (Fizzy mod is required)")
            .define("enableModernShowtime", true);
    public static final ModConfigSpec.BooleanValue BOID_FEATURES = BUILDER
            .comment("Enable boids feature")
            .define("enableBoidFeature", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOID_MOBS = BUILDER
            .comment("Boid mobs list, such as minecraft:salmon")
            .defineListAllowEmpty(
                    List.of("included_entities"),
                    // 默认空列表：仅使用 BoidsConsts.DEFAULT_ENTITIES
                    () -> List.of(),
                    o -> o instanceof String s && !s.isBlank()
            );
    public static final ModConfigSpec.BooleanValue QUARK_TOTEM = BUILDER
            .comment("Enable Quark's TotemOfHolding")
            .define("enableQuarkTotem", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TOTEM_WORLD = BUILDER
            .comment("Allow what worlds that can use this feature")
            .defineListAllowEmpty(
                    List.of("totem_world"),
                    () -> List.of(),
                    o -> o instanceof String s && !s.isBlank()
            );

    public static final ModConfigSpec.BooleanValue AUTO_RESTART = BUILDER
            .comment("Enable a classical auto restart feature")
            .define("enableAutoRestart", true);
    public static final ModConfigSpec.IntValue RESTART_HOUR = BUILDER
            .comment("Set an hour for restart")
            .defineInRange("restartHours", 2, 0, 24);
    public static final ModConfigSpec.IntValue RESTART_MINUTE = BUILDER
            .comment("Set a minute for restart")
            .defineInRange("restartMinutes", 0, 0, 60);
    public static final ModConfigSpec.ConfigValue<String> RESTART_COMMAND = BUILDER
            .comment("What command execute when restart ('/' is no needed)")
            .define("restartCommand", "stop");
    public static final ModConfigSpec.BooleanValue PLECO = BUILDER
            .comment("Enable pleco feature")
            .define("enablePleco", true);
    public static final ModConfigSpec.IntValue PLECO_HOUR = BUILDER
            .comment("Set an hour for clean the drop items")
            .defineInRange("plecoHours", 1, 0, 24);
    public static final ModConfigSpec.IntValue PLECO_MINUTE = BUILDER
            .comment("Set a minute for clean the drop items")
            .defineInRange("plecoMinutes", 0, 0, 60);
    public static final ModConfigSpec.BooleanValue IGNORE_NAMED_ITEMS = BUILDER
            .comment("Ignore named items")
            .define("ignoreNamedItems", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST_ITEM_IDS = BUILDER
            .comment("Whitelist for pleco cleaner (minecraft:dirt)")
            .defineListAllowEmpty(
                    List.of("whitelistItemIds"),
                    () -> List.of(),
                    o -> o instanceof String s && !s.isBlank()
            );
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ITEM_IDS = BUILDER
            .comment("Blacklist for pleco cleaner (minecraft:dirt)")
            .defineListAllowEmpty(
                    List.of("blacklistItemIds"),
                    () -> List.of(),
                    o -> o instanceof String s && !s.isBlank()
            );


//    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
//            .comment("Whether to log the dirt block on common setup")
//            .define("logDirtBlock", true);
//
//    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
//            .comment("A magic number")
//            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);
//
//    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
//            .comment("What you want the introduction message to be for the magic number")
//            .define("magicNumberIntroduction", "The magic number is... ");
//
//    // a list of strings that are treated as resource locations for items
//    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
//            .comment("A list of items to log on common setup.")
//            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

}
