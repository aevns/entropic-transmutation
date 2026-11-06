package aevns.entropictransmutation.config;

import aevns.entropictransmutation.samplers.maxent.Params;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Read whenever the sampler is rebuilt, so edits apply on the next EMC remap: a /reload, or a restart. */
public final class ETConfig {
    public enum FilterMode {BLACKLIST, WHITELIST}

    private static final ModConfigSpec.EnumValue<FilterMode> FILTER_MODE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_TAGS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEMS;

    private static final ModConfigSpec.DoubleValue VALUE_SIMILARITY;
    private static final ModConfigSpec.DoubleValue WEBER_FRACTION;
    private static final ModConfigSpec.DoubleValue VALUE_TRANSITION_SCALE;
    private static final ModConfigSpec.DoubleValue NULL_STATE_PRIOR;
    private static final ModConfigSpec.DoubleValue COUNT_WEBER_FRACTION;
    private static final ModConfigSpec.DoubleValue COUNT_TRANSITION_SCALE;

    private static final ModConfigSpec.IntValue TARGET_ROWS;
    private static final ModConfigSpec.IntValue BETA_CANDIDATES;
    private static final ModConfigSpec.IntValue LABEL_TABLE_MEGABYTES;

    public static final ModConfigSpec SPEC;

    static {
        Params defaults = new Params();
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Which items can appear in transmutation outputs. Other mods can also change this per item.")
                .push("outputs");
        FILTER_MODE = builder
                .comment("BLACKLIST excludes everything listed here. WHITELIST allows only what is listed here.")
                .defineEnum("mode", FilterMode.BLACKLIST);
        NAMESPACES = builder
                .comment("Mod namespaces, such as \"minecraft\" or \"projecte\".")
                .defineListAllowEmpty("namespaces", List.of(), () -> "", ETConfig::isString);
        ITEM_TAGS = builder
                .comment("Item tags, without a leading #, such as \"minecraft:planks\".")
                .defineListAllowEmpty("itemTags", List.of(), () -> "", ETConfig::isResourceLocation);
        ITEMS = builder
                .comment("Item ids, such as \"minecraft:dirt\".")
                .defineListAllowEmpty("items", List.of(), () -> "", ETConfig::isResourceLocation);
        builder.pop();

        builder.comment("The sampler's prior: how outcomes are weighted before matching the input's value.")
                .push("prior");
        VALUE_SIMILARITY = builder
                .comment("How interchangeable two items of equal value feel. 0 treats all items as fully distinct,",
                        "1 treats items of equal value as identical. A crowded value counts as at most 1 / this many items.")
                .defineInRange("valueSimilarity", defaults.valueSimilarity, 0.0, 1.0);
        WEBER_FRACTION = builder
                .comment("Relative difference below which two item values count as neighbours.")
                .defineInRange("weberFraction", defaults.weberFraction, 1e-6, 10.0);
        VALUE_TRANSITION_SCALE = builder
                .comment("EMC value below which differences count in absolute terms, and above which they count as ratios.")
                .defineInRange("valueTransitionScale", defaults.valueTransitionScale, 1e-6, 1e12);
        NULL_STATE_PRIOR = builder
                .comment("Prior weight of an output slot being empty, relative to each stack size's average weight.")
                .defineInRange("nullStatePrior", defaults.nullStatePrior, 1e-6, 1e6);
        COUNT_WEBER_FRACTION = builder
                .comment("Relative difference below which two stack sizes count as neighbours.",
                        "Near 0, every stack size is equally likely a priori.")
                .defineInRange("countWeberFraction", defaults.countWeberFraction, 1e-6, 10.0);
        COUNT_TRANSITION_SCALE = builder
                .comment("As valueTransitionScale, for stack sizes. Smaller values make small stacks more distinct.")
                .defineInRange("countTransitionScale", defaults.countTransitionScale, 1e-6, 1e3);
        builder.pop();

        builder.comment("Precision of the sampler's tables, trading accuracy against memory and rebuild time.")
                .push("tables");
        TARGET_ROWS = builder
                .comment("Stored rows per expected stack size.")
                .defineInRange("targetRowsPerExpectedCount", defaults.targetRowsPerExpectedCount, 2, 4096);
        BETA_CANDIDATES = builder
                .comment("Points used to place those rows. This is the largest part of the rebuild time.")
                .defineInRange("betaCandidatesPerSign", defaults.betaCandidatesPerSign, 2, 100000);
        LABEL_TABLE_MEGABYTES = builder
                .comment("Memory budget for the item table, in megabytes.")
                .defineInRange("labelTableMegabytes", (int) (defaults.labelTableBudgetBytes / 1_000_000L), 1, 1024);
        builder.pop();

        SPEC = builder.build();
    }

    private ETConfig() {
    }

    private static boolean isString(Object value) {
        return value instanceof String;
    }

    private static boolean isResourceLocation(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    /** The configured sampler parameters. */
    public static Params samplerParams() {
        Params params = new Params();
        params.valueSimilarity = VALUE_SIMILARITY.get();
        params.weberFraction = WEBER_FRACTION.get();
        params.valueTransitionScale = VALUE_TRANSITION_SCALE.get();
        params.nullStatePrior = NULL_STATE_PRIOR.get();
        params.countWeberFraction = COUNT_WEBER_FRACTION.get();
        params.countTransitionScale = COUNT_TRANSITION_SCALE.get();
        params.targetRowsPerExpectedCount = TARGET_ROWS.get();
        params.betaCandidatesPerSign = BETA_CANDIDATES.get();
        params.labelTableBudgetBytes = LABEL_TABLE_MEGABYTES.get() * 1_000_000L;
        return params;
    }

    /** Tests whether an item may appear in outputs. Tags must be bound by the time it's called. */
    public static Predicate<ItemInfo> outputFilter() {
        Set<String> namespaces = Set.copyOf(NAMESPACES.get());
        Set<ResourceLocation> items = Set.copyOf(ITEMS.get().stream().map(ResourceLocation::parse).toList());
        List<TagKey<Item>> tags = ITEM_TAGS.get().stream()
                .map(id -> TagKey.create(Registries.ITEM, ResourceLocation.parse(id))).toList();
        boolean whitelist = FILTER_MODE.get() == FilterMode.WHITELIST;
        return info -> {
            Holder<Item> holder = info.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(holder.value());
            boolean listed = namespaces.contains(id.getNamespace()) || items.contains(id) || tags.stream().anyMatch(holder::is);
            return listed == whitelist;
        };
    }
}
