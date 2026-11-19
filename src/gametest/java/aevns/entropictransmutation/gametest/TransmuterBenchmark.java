package aevns.entropictransmutation.gametest;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.config.ETConfig;
import aevns.entropictransmutation.mixins.EMCMappingHandlerMixin;
import aevns.entropictransmutation.registry.ETBlocks;
import aevns.entropictransmutation.samplers.EmcSampler;
import com.mojang.logging.LogUtils;
import io.github.aevns.fastmaxent.JointModel;
import io.github.aevns.fastmaxent.TableParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Performance benchmarks, run with the gameTestServer Gradle task. They report timings in the log and always pass.
 * Each test method is one GameTest: the server builds its structure (here an empty area), calls the method once, and
 * then ticks the test until it succeeds or times out. Tests in different batches run one after another.
 */
@GameTestHolder(EntropicTransmutation.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EntropicTransmutation.MODID)
public class TransmuterBenchmark {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The machines form a GRID x GRID square, which the benchmark_area structure is sized to hold. */
    private static final int GRID = 32;
    private static final int IDLE_TICKS = 100;
    private static final int WARMUP_TICKS = 200;
    private static final int MEASURED_TICKS = 600;

    /** The level whose tick time is being added up, or null when nothing is being measured. */
    @Nullable
    private static ServerLevel measuredLevel;
    private static long levelTickStart;
    private static long levelTickNanos;

    @SubscribeEvent
    static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() == measuredLevel) levelTickStart = System.nanoTime();
    }

    @SubscribeEvent
    static void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel() == measuredLevel) levelTickNanos += System.nanoTime() - levelTickStart;
    }

    /** ProjectE builds EMC values on the first datapack sync, which a server without players never has. */
    private static void ensureEmcValues(GameTestHelper helper) {
        if (!EmcSampler.isReady()) {
            NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), null));
        }
        helper.assertTrue(EmcSampler.isReady(), "EMC values never arrived");
    }

    /**
     * A grid of transmuters, each emptied and refilled every tick through its item handler, as fast item pipes would.
     * Measures the test level's tick time while the machines sit idle and then while they're fed, the time the
     * feeding itself takes (which includes any client syncs and comparator updates the inventory changes trigger),
     * and how many inventory changes each transmutation causes.
     */
    @GameTest(template = "benchmark_area", timeoutTicks = IDLE_TICKS + WARMUP_TICKS + MEASURED_TICKS + 100,
            batch = "benchmark_grid")
    public static void transmuterGrid(GameTestHelper helper) {
        ensureEmcValues(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        List<BlockCapabilityCache<IItemHandler, Direction>> machines = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                BlockPos pos = helper.absolutePos(new BlockPos(1 + x, 1, 1 + z));
                level.setBlock(pos, ETBlocks.TRANSMUTER.get().defaultBlockState(), Block.UPDATE_ALL);
                machines.add(BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, pos, Direction.UP));
            }
        }

        long feedStart = IDLE_TICKS, measureStart = feedStart + WARMUP_TICKS, end = measureStart + MEASURED_TICKS;
        long[] counts = new long[3]; // inserts (one per transmutation), inventory changes, items extracted
        long[] feederNanos = new long[1];
        double[] idle = new double[2], fed = new double[2]; // level ms per tick, server ms per tick

        measuredLevel = level;
        levelTickNanos = 0;
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == feedStart) {
                idle[0] = levelTickNanos / 1e6 / tick;
                idle[1] = server.getAverageTickTimeNanos() / 1e6;
            }
            if (tick >= feedStart) {
                boolean measuring = tick >= measureStart;
                if (tick == measureStart) levelTickNanos = 0;
                long start = System.nanoTime();
                for (BlockCapabilityCache<IItemHandler, Direction> machine : machines) {
                    IItemHandler handler = machine.getCapability();
                    if (handler == null) continue;
                    for (int slot = 1; slot < handler.getSlots(); slot++) {
                        ItemStack taken;
                        // Unstackable outputs can exceed their max stack size, and come out one at a time
                        while (!(taken = handler.extractItem(slot, 64, false)).isEmpty()) {
                            if (measuring) { counts[1]++; counts[2] += taken.getCount(); }
                        }
                    }
                    if (handler.getStackInSlot(0).isEmpty()
                            && handler.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty() && measuring) {
                        counts[0]++;
                        counts[1]++;
                    }
                }
                if (measuring) feederNanos[0] += System.nanoTime() - start;
            }
            if (tick == end) {
                measuredLevel = null;
                fed[0] = levelTickNanos / 1e6 / MEASURED_TICKS;
                fed[1] = server.getAverageTickTimeNanos() / 1e6;
                double transmutations = Math.max(1, counts[0]);
                LOGGER.info("BENCHMARK {} transmuters, fed every tick: outputs emptied, one diamond inserted when empty",
                        machines.size());
                LOGGER.info("BENCHMARK idle: level tick {} ms, server tick {} ms (100-tick average)",
                        ms(idle[0]), ms(idle[1]));
                LOGGER.info("BENCHMARK fed: {} transmutations in {} ticks ({} per tick); level tick {} ms, of which "
                                + "about {} us per transmutation; feeding {} ms per tick; server tick {} ms",
                        counts[0], MEASURED_TICKS, ms(counts[0] / (double) MEASURED_TICKS), ms(fed[0]),
                        ms((fed[0] - idle[0]) * 1000 * MEASURED_TICKS / transmutations),
                        ms(feederNanos[0] / 1e6 / MEASURED_TICKS), ms(fed[1]));
                LOGGER.info("BENCHMARK per transmutation: {} inventory changes from feeding, {} items extracted",
                        ms(counts[1] / transmutations), ms(counts[2] / transmutations));
                helper.succeed();
            }
        });
    }

    /** How long the sampler takes to rebuild, and how much of that the betaCandidatesPerSign setting accounts for. */
    @GameTest(template = "benchmark_area", batch = "benchmark_rebuild")
    public static void rebuildTime(GameTestHelper helper) throws Exception {
        ensureEmcValues(helper);
        Method rebuild = EmcSampler.class.getDeclaredMethod("onEmcRemap", moze_intel.projecte.api.event.EMCRemapEvent.class);
        rebuild.setAccessible(true);
        for (int run = 0; run < 3; run++) {
            long start = System.nanoTime();
            rebuild.invoke(null, new Object[]{null});
            LOGGER.info("BENCHMARK full rebuild with the current config (betaCandidatesPerSign = {}): {} ms",
                    ETConfig.tableParams().betaCandidatesPerSign(), ms((System.nanoTime() - start) / 1e6));
        }

        // The model alone, built the way the rebuild builds it, at different settings
        double[] values = EMCMappingHandlerMixin.getMap().values().longStream().mapToDouble(v -> v).sorted().toArray();
        double[] counts = IntStream.rangeClosed(1, 64).asDoubleStream().toArray();
        double[] countWeights = ETConfig.countPrior().weights(counts);
        for (int i = 0; i < countWeights.length; i++) countWeights[i] *= countWeights.length;
        double[] valueWeights = ETConfig.valuePrior().weights(values);
        TableParams current = ETConfig.tableParams();
        for (int candidates : new int[]{500, 3000, 500, 3000}) {
            TableParams params = new TableParams(current.saturationTolerance(), current.targetRowsPerConstraintPreset(),
                    current.categoryBlockSize(), current.categoryTableBudgetBytes(), candidates);
            long start = System.nanoTime();
            new JointModel(values, valueWeights, counts, countWeights, ETConfig.nullStateWeight(),
                    EmcSampler.SUPPORTED_EXPECTED_COUNTS, params);
            LOGGER.info("BENCHMARK model build alone, {} values, betaCandidatesPerSign = {}: {} ms",
                    values.length, candidates, ms((System.nanoTime() - start) / 1e6));
        }
        helper.succeed();
    }

    private static String ms(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
