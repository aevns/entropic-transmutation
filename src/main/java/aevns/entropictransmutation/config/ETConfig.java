package aevns.entropictransmutation.config;

import io.github.aevns.fastmaxent.TableParams;
import io.github.aevns.fastmaxent.priors.Axis;
import io.github.aevns.fastmaxent.priors.DensityPrior;
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

    private static final double DEFAULT_VALUE_SIMILARITY = 0.2;
    private static final double DEFAULT_WEBER_FRACTION = 0.15;
    private static final double DEFAULT_VALUE_TRANSITION_SCALE = 10.0;
    private static final double DEFAULT_NULL_STATE_PRIOR = 1.0;
    private static final double DEFAULT_COUNT_WEBER_FRACTION = 0.15;
    private static final double DEFAULT_COUNT_TRANSITION_SCALE = 1.0;

    static {
        TableParams tableDefaults = TableParams.DEFAULT;
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
                .defineInRange("valueSimilarity", DEFAULT_VALUE_SIMILARITY, 0.0, 1.0);
        WEBER_FRACTION = builder
                .comment("Relative difference below which two item values count as neighbours.")
                .defineInRange("weberFraction", DEFAULT_WEBER_FRACTION, 1e-6, 10.0);
        VALUE_TRANSITION_SCALE = builder
                .comment("EMC value below which differences count in absolute terms, and above which they count as ratios.")
                .defineInRange("valueTransitionScale", DEFAULT_VALUE_TRANSITION_SCALE, 1e-6, 1e12);
        NULL_STATE_PRIOR = builder
                .comment("Prior weight of an output slot being empty, relative to each stack size's average weight.")
                .defineInRange("nullStatePrior", DEFAULT_NULL_STATE_PRIOR, 1e-6, 1e6);
        COUNT_WEBER_FRACTION = builder
                .comment("Relative difference below which two stack sizes count as neighbours.",
                        "Near 0, every stack size is equally likely a priori.")
                .defineInRange("countWeberFraction", DEFAULT_COUNT_WEBER_FRACTION, 1e-6, 10.0);
        COUNT_TRANSITION_SCALE = builder
                .comment("Transition scale for stack sizes. Smaller values make small stacks more distinct.")
                .defineInRange("countTransitionScale", DEFAULT_COUNT_TRANSITION_SCALE, 1e-6, 1e3);
        builder.pop();

        builder.comment("Precision of the sampler's tables, trading accuracy against memory and rebuild time.")
                .push("tables");
        TARGET_ROWS = builder
                .comment("Stored rows per expected stack size.")
                .defineInRange("targetRowsPerExpectedCount", tableDefaults.targetRowsPerConstraintPreset(), 2, 4096);
        BETA_CANDIDATES = builder
                .comment("Candidate points used to place those rows. A high number significantly increases rebuild time.")
                .defineInRange("betaCandidatesPerSign", tableDefaults.betaCandidatesPerSign(), 2, 100000);
        LABEL_TABLE_MEGABYTES = builder
                .comment("Memory budget for the item table, in megabytes.")
                .defineInRange("labelTableMegabytes", (int) (tableDefaults.categoryTableBudgetBytes() / 1_000_000L), 1, 1024);
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

    /** The prior over item values: values within the Weber fraction of each other share weight. */
    public static DensityPrior valuePrior() {
        return new DensityPrior(new Axis.Symlog(VALUE_TRANSITION_SCALE.get()), WEBER_FRACTION.get(),
                VALUE_SIMILARITY.get(), false, false);
    }

    /**
     * The prior over stack sizes. A stack size has no identity beyond its magnitude, so similarity is 1, and the
     * sizes continue past the largest so that it isn't mistaken for uncrowded.
     */
    public static DensityPrior countPrior() {
        return new DensityPrior(new Axis.Symlog(COUNT_TRANSITION_SCALE.get()), COUNT_WEBER_FRACTION.get(), 1, false, true);
    }

    /** The null state's weight, relative to an average stack size's. */
    public static double nullStateWeight() {
        return NULL_STATE_PRIOR.get();
    }

    /** The table precision settings, with the library's defaults for those not in the config. */
    public static TableParams tableParams() {
        TableParams defaults = TableParams.DEFAULT;
        return new TableParams(defaults.saturationTolerance(), TARGET_ROWS.get(), defaults.categoryBlockSize(),
                LABEL_TABLE_MEGABYTES.get() * 1_000_000L, BETA_CANDIDATES.get());
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
