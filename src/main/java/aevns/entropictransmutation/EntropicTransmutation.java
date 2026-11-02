package aevns.entropictransmutation;

import aevns.entropictransmutation.block.TransmuterAddonBlock;
import aevns.entropictransmutation.block.TransmuterBlock;
import aevns.entropictransmutation.block.TransmuterMonitorBlock;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(EntropicTransmutation.MODID)
public class EntropicTransmutation {
    public static final String MODID = "entropictransmutation";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // TODO: Move this logic to a utility class or somesuch
    public static final DeferredBlock<TransmuterBlock> TRANSMUTER_BLOCK = BLOCKS.register(
            "transmuter",
            () -> new TransmuterBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .lightLevel(state -> 4)
                    .strength(2.0f)
                    .sound(SoundType.VAULT)
            )
    );
    public static final DeferredItem<BlockItem> TRANSMUTER_ITEM = ITEMS.registerSimpleBlockItem(
            "transmuter",
            TRANSMUTER_BLOCK
    );
    @SuppressWarnings("ConstantConditions")
    public static final Supplier<BlockEntityType<TransmuterBlockEntity>> TRANSMUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "transmuter",
            () -> BlockEntityType.Builder.of(TransmuterBlockEntity::new, TRANSMUTER_BLOCK.get()).build(null)
    );
    public static final DeferredBlock<TransmuterMonitorBlock> TRANSMUTER_MONITOR_BLOCK = BLOCKS.register(
            "transmuter_monitor",
            () -> new TransmuterMonitorBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .lightLevel(state -> 4)
                    .strength(2.0f)
                    .sound(SoundType.VAULT)
            )
    );
    public static final DeferredItem<BlockItem> TRANSMUTER_MONITOR_ITEM = ITEMS.registerSimpleBlockItem(
            "transmuter_monitor",
            TRANSMUTER_MONITOR_BLOCK
    );
    public static final DeferredBlock<TransmuterAddonBlock> COUNT_SUPPRESSOR_BLOCK = BLOCKS.register(
            "count_suppressor",
            () -> new TransmuterAddonBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 7)
                    .strength(2.0f)
                    .sound(SoundType.BAMBOO_WOOD_HANGING_SIGN)
            )
    );
    public static final DeferredItem<BlockItem> COUNT_SUPPRESSOR_ITEM = ITEMS.registerSimpleBlockItem(
            "count_suppressor",
            COUNT_SUPPRESSOR_BLOCK
    );
    public static final DeferredBlock<TransmuterAddonBlock> TYPE_SUPPRESSOR_BLOCK = BLOCKS.register(
            "type_suppressor",
            () -> new TransmuterAddonBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 7)
                    .strength(2.0f)
                    .sound(SoundType.BAMBOO_WOOD_HANGING_SIGN)
            )
    );
    public static final DeferredItem<BlockItem> TYPE_SUPPRESSOR_ITEM = ITEMS.registerSimpleBlockItem(
            "type_suppressor",
            TYPE_SUPPRESSOR_BLOCK
    );
    public static final DeferredBlock<TransmuterAddonBlock> VOID_SUPPRESSOR_BLOCK = BLOCKS.register(
            "void_suppressor",
            () -> new TransmuterAddonBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .lightLevel(state -> 7)
                    .strength(2.0f)
                    .sound(SoundType.BAMBOO_WOOD_HANGING_SIGN)
            )
    );
    public static final DeferredItem<BlockItem> VOID_SUPPRESSOR_ITEM = ITEMS.registerSimpleBlockItem(
            "void_suppressor",
            VOID_SUPPRESSOR_BLOCK
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRANSMUTER_TAB = CREATIVE_MODE_TABS.register("transmuter_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.entropictransmutation")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TRANSMUTER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TRANSMUTER_ITEM.get());// For your own tabs, this method is preferred over the event
                output.accept(TRANSMUTER_MONITOR_ITEM.get());
                output.accept(TYPE_SUPPRESSOR_ITEM.get());
                output.accept(COUNT_SUPPRESSOR_ITEM.get());
                output.accept(VOID_SUPPRESSOR_ITEM.get());

            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public EntropicTransmutation(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (EntropicTransmutation) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerCapabilities);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(TRANSMUTER_ITEM);
            event.accept(TRANSMUTER_MONITOR_ITEM);
            event.accept(TYPE_SUPPRESSOR_ITEM);
            event.accept(COUNT_SUPPRESSOR_ITEM);
            event.accept(VOID_SUPPRESSOR_ITEM);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TRANSMUTER_BLOCK_ENTITY.get(),
                (be, side) -> be.getAutomationHandler()
        );
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
