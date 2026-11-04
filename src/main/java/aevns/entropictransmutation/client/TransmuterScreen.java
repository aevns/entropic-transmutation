package aevns.entropictransmutation.client;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.block.TransmuterAddonBlock;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import aevns.entropictransmutation.menu.TransmuterMenu;
import aevns.entropictransmutation.registry.ModBlocks;
import aevns.entropictransmutation.samplers.maxent.MaxEntSampler;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.registries.DeferredBlock;

import javax.annotation.ParametersAreNonnullByDefault;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterScreen extends AbstractContainerScreen<TransmuterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EntropicTransmutation.MODID, "textures/gui/container/transmuter.png");
    private static final String KEY = "gui." + EntropicTransmutation.MODID + ".transmuter.";
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");

    // Positions within the GUI
    private static final int PROGRESS_X = 68;
    private static final int PROGRESS_Y = 35;
    private static final int PROGRESS_WIDTH = 24;
    private static final int PROGRESS_HEIGHT = 16;
    private static final int ADDON_X = 140;
    private static final int ADDON_FIRST_Y = 28;
    private static final int ADDON_SPACING = 11;
    private static final int ADDON_DIGIT_X = ADDON_X + 9;
    private static final int SYMBOL_SIZE = 7;

    // Sprites to the right of the background in the texture
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 0;
    private static final int DISABLED_SLOT_U = 176;
    private static final int DISABLED_SLOT_V = 16;
    private static final int SYMBOL_U = 176;
    private static final int SYMBOL_ACTIVE_V = 32;
    private static final int SYMBOL_INACTIVE_V = 40;

    /** Addon labels from top to bottom; each symbol sprite is 7 x 8 with its shadow, 8 px apart in the texture. */
    private enum Addon {
        TYPE(ModBlocks.TYPE_SUPPRESSOR, 0xB07CF0, 0x5A2C96, ChatFormatting.LIGHT_PURPLE,
                TransmuterBlockEntity.MAX_TYPE_SUPPRESSORS),
        COUNT(ModBlocks.COUNT_SUPPRESSOR, 0x2FCAD8, 0x0B6670, ChatFormatting.AQUA,
                TransmuterBlockEntity.MAX_COUNT_SUPPRESSORS),
        VOID(ModBlocks.VOID_AMPLIFIER, 0xFF962E, 0x9C4300, ChatFormatting.GOLD,
                TransmuterBlockEntity.MAX_VOID_AMPLIFIERS);

        final DeferredBlock<TransmuterAddonBlock> block;
        final int color;
        final int shadowColor;
        final ChatFormatting tooltipColor;
        final int maxEffective;

        Addon(DeferredBlock<TransmuterAddonBlock> block, int color, int shadowColor, ChatFormatting tooltipColor,
              int maxEffective) {
            this.block = block;
            this.color = color;
            this.shadowColor = shadowColor;
            this.tooltipColor = tooltipColor;
            this.maxEffective = maxEffective;
        }

        int y() {
            return ADDON_FIRST_Y + ordinal() * ADDON_SPACING;
        }

        String key() {
            return KEY + name().toLowerCase(Locale.ROOT) + ".";
        }
    }

    public TransmuterScreen(TransmuterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int filled = menu.getProgressTicks() * PROGRESS_WIDTH / TransmuterBlockEntity.MAX_TRANSMUTATION_TIME;
        graphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_U, PROGRESS_V, filled, PROGRESS_HEIGHT);

        // Hatch the empty output slots that type suppressors stop from being filled
        int samples = TransmuterBlockEntity.sampleCount(menu.getTypeSuppressors());
        for (int i = samples; i < TransmuterBlockEntity.OUTPUT_COUNT; i++) {
            Slot slot = menu.slots.get(TransmuterMenu.outputSlotIndex(i));
            if (!slot.hasItem()) {
                graphics.blit(TEXTURE, leftPos + slot.x, topPos + slot.y, DISABLED_SLOT_U, DISABLED_SLOT_V, 16, 16);
            }
        }

        for (Addon addon : Addon.values()) {
            int v = count(addon) > 0 ? SYMBOL_ACTIVE_V : SYMBOL_INACTIVE_V;
            graphics.blit(TEXTURE, leftPos + ADDON_X, topPos + addon.y(),
                    SYMBOL_U + addon.ordinal() * 8, v, SYMBOL_SIZE, SYMBOL_SIZE + 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        for (Addon addon : Addon.values()) {
            int count = effectiveCount(addon);
            if (count == 0) continue;
            String text = Integer.toString(count);
            // Drawn twice rather than with the font's shadow, which is offset diagonally
            graphics.drawString(font, text, ADDON_DIGIT_X, addon.y() + 1, 0xFF000000 | addon.shadowColor, false);
            graphics.drawString(font, text, ADDON_DIGIT_X, addon.y(), 0xFF000000 | addon.color, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        List<Component> lines = extraTooltip(mouseX - leftPos, mouseY - topPos);
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Tooltips for the parts of the GUI that aren't items: addon labels, progress, and disabled outputs. */
    private List<Component> extraTooltip(int x, int y) {
        for (Addon addon : Addon.values()) {
            if (count(addon) > 0 && x >= ADDON_X && x < ADDON_DIGIT_X + 6
                    && y >= addon.y() && y < addon.y() + SYMBOL_SIZE + 1) {
                return addonTooltip(addon);
            }
        }

        int progress = menu.getProgressTicks();
        if (progress > 0 && x >= PROGRESS_X && x < PROGRESS_X + PROGRESS_WIDTH
                && y >= PROGRESS_Y && y < PROGRESS_Y + PROGRESS_HEIGHT) {
            return List.of(Component.translatable(KEY + "progress", progress, TransmuterBlockEntity.MAX_TRANSMUTATION_TIME));
        }

        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            int samples = TransmuterBlockEntity.sampleCount(menu.getTypeSuppressors());
            for (int i = samples; i < TransmuterBlockEntity.OUTPUT_COUNT; i++) {
                if (hoveredSlot == menu.slots.get(TransmuterMenu.outputSlotIndex(i))) {
                    return List.of(Component.translatable(KEY + "disabled_output").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return List.of();
    }

    private List<Component> addonTooltip(Addon addon) {
        int count = effectiveCount(addon);
        Component effect = switch (addon) {
            case TYPE -> Component.translatable(addon.key() + "effect",
                    TransmuterBlockEntity.OUTPUT_COUNT, TransmuterBlockEntity.sampleCount(count));
            case COUNT -> Component.translatable(addon.key() + "effect",
                    expectedCount(TransmuterBlockEntity.DEFAULT_COUNT_INDEX),
                    expectedCount(TransmuterBlockEntity.countIndex(count)));
            case VOID -> Component.translatable(addon.key() + "effect", TransmuterBlockEntity.voidMultiplier(count));
        };
        String heading = KEY + (count >= addon.maxEffective ? "addon_max" : "addon");
        return List.of(
                Component.translatable(heading, addon.block.get().getName(), count).withStyle(addon.tooltipColor),
                Component.translatable(addon.key() + "description").withStyle(ChatFormatting.GRAY),
                effect.copy().withStyle(addon.tooltipColor)
        );
    }

    private static String expectedCount(int countIndex) {
        return NUMBER.format(MaxEntSampler.SUPPORTED_EXPECTED_COUNTS[countIndex]);
    }

    /** The attached count, ignoring any beyond the most that have an effect. */
    private int effectiveCount(Addon addon) {
        return Math.min(count(addon), addon.maxEffective);
    }

    private int count(Addon addon) {
        return switch (addon) {
            case TYPE -> menu.getTypeSuppressors();
            case COUNT -> menu.getCountSuppressors();
            case VOID -> menu.getVoidAmplifiers();
        };
    }
}
