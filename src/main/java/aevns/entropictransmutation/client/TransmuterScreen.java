package aevns.entropictransmutation.client;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.api.TransmutationSettings;
import aevns.entropictransmutation.api.TransmuterAddon;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import aevns.entropictransmutation.menu.TransmuterMenu;
import aevns.entropictransmutation.registry.ETAddons;
import aevns.entropictransmutation.samplers.maxent.MaxEntSampler;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

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
    private static final int ADDON_ROWS = 3;
    private static final int SYMBOL_SIZE = 7;
    /** How far below an addon's symbol sprite its inactive version is. */
    private static final int SYMBOL_INACTIVE_OFFSET = 8;

    // Sprites to the right of the background in the texture
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 0;
    private static final int DISABLED_SLOT_U = 176;
    private static final int DISABLED_SLOT_V = 16;

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
        for (int i = menu.getSettings().sampleCount(); i < TransmuterBlockEntity.OUTPUT_COUNT; i++) {
            Slot slot = menu.slots.get(TransmuterMenu.outputSlotIndex(i));
            if (!slot.hasItem()) {
                hatchSlot(graphics, slot);
            }
        }

        // Likewise the empty input while it's locked by pending outputs
        Slot input = menu.slots.get(TransmuterMenu.INPUT_SLOT);
        if (!input.hasItem() && menu.isInputLocked()) {
            hatchSlot(graphics, input);
        }

        List<TransmuterAddon> rows = addonRows();
        for (int row = 0; row < rows.size(); row++) {
            TransmuterAddon.Appearance appearance = rows.get(row).appearance();
            int v = appearance.v() + (menu.getAddonCount(rows.get(row)) > 0 ? 0 : SYMBOL_INACTIVE_OFFSET);
            graphics.blit(appearance.texture(), leftPos + ADDON_X, topPos + rowY(row),
                    appearance.u(), v, SYMBOL_SIZE, SYMBOL_SIZE + 1);
        }
    }

    /** The addons shown, from top to bottom: all of them if they fit, or else only the attached ones. */
    private List<TransmuterAddon> addonRows() {
        if (ETAddons.REGISTRY.size() <= ADDON_ROWS) return ETAddons.REGISTRY.stream().toList();
        return ETAddons.REGISTRY.stream().filter(addon -> menu.getAddonCount(addon) > 0).limit(ADDON_ROWS).toList();
    }

    private static int rowY(int row) {
        return ADDON_FIRST_Y + row * ADDON_SPACING;
    }

    private void hatchSlot(GuiGraphics graphics, Slot slot) {
        graphics.blit(TEXTURE, leftPos + slot.x, topPos + slot.y, DISABLED_SLOT_U, DISABLED_SLOT_V, 16, 16);
    }

    /** Shows the count of an overfilled stack in red, as a sign that it's not a normal stack. */
    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        boolean overfilled = countString == null && stack.getCount() > stack.getMaxStackSize();
        super.renderSlotContents(graphics, stack, slot, overfilled ? ChatFormatting.RED.toString() + stack.getCount() : countString);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        List<TransmuterAddon> rows = addonRows();
        for (int row = 0; row < rows.size(); row++) {
            int count = effectiveCount(rows.get(row));
            if (count == 0) continue;
            TransmuterAddon.Appearance appearance = rows.get(row).appearance();
            String text = Integer.toString(count);
            // Drawn twice rather than with the font's shadow, which is offset diagonally
            graphics.drawString(font, text, ADDON_DIGIT_X, rowY(row) + 1, 0xFF000000 | appearance.shadowColor(), false);
            graphics.drawString(font, text, ADDON_DIGIT_X, rowY(row), 0xFF000000 | appearance.color(), false);
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

    /** Tooltips for the parts of the GUI that aren't items: addon labels, progress, and disabled slots. */
    private List<Component> extraTooltip(int x, int y) {
        List<TransmuterAddon> rows = addonRows();
        for (int row = 0; row < rows.size(); row++) {
            if (menu.getAddonCount(rows.get(row)) > 0 && x >= ADDON_X && x < ADDON_DIGIT_X + 6
                    && y >= rowY(row) && y < rowY(row) + SYMBOL_SIZE + 1) {
                return addonTooltip(rows.get(row));
            }
        }

        int progress = menu.getProgressTicks();
        if (progress > 0 && x >= PROGRESS_X && x < PROGRESS_X + PROGRESS_WIDTH
                && y >= PROGRESS_Y && y < PROGRESS_Y + PROGRESS_HEIGHT) {
            return List.of(Component.translatable(KEY + "progress", progress, TransmuterBlockEntity.MAX_TRANSMUTATION_TIME));
        }

        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            if (hoveredSlot == menu.slots.get(TransmuterMenu.INPUT_SLOT) && menu.isInputLocked()) {
                return List.of(Component.translatable(KEY + "input_locked").withStyle(ChatFormatting.GRAY));
            }
            for (int i = menu.getSettings().sampleCount(); i < TransmuterBlockEntity.OUTPUT_COUNT; i++) {
                if (hoveredSlot == menu.slots.get(TransmuterMenu.outputSlotIndex(i))) {
                    return List.of(Component.translatable(KEY + "disabled_output").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return List.of();
    }

    private List<Component> addonTooltip(TransmuterAddon addon) {
        int count = effectiveCount(addon);
        TransmutationSettings base = new TransmutationSettings(), settings = new TransmutationSettings();
        addon.applyTo(settings, count);

        ChatFormatting color = addon.appearance().tooltipColor();
        String heading = KEY + (count >= addon.maxEffective() ? "addon_max" : "addon");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(heading, addon.block().get().getName(), count).withStyle(color));
        lines.add(Component.translatable(addon.block().get().getDescriptionId() + ".description")
                .withStyle(ChatFormatting.GRAY));
        // The addon's effect is described by what it changes
        if (settings.sampleCount() != base.sampleCount()) {
            lines.add(Component.translatable(KEY + "effect.outputs", base.sampleCount(), settings.sampleCount())
                    .withStyle(color));
        }
        if (settings.countIndex() != base.countIndex()) {
            lines.add(Component.translatable(KEY + "effect.stack_size",
                    expectedCount(base.countIndex()), expectedCount(settings.countIndex())).withStyle(color));
        }
        if (settings.voidMultiplier() != base.voidMultiplier()) {
            lines.add(Component.translatable(KEY + "effect.chance", settings.voidMultiplier()).withStyle(color));
        }
        lines.add(Component.translatable(KEY + "value_effect", NUMBER.format(valueMultiplier(settings))).withStyle(color));
        return lines;
    }

    /** How many times the mean value of each output item grows, as the input's value is split across fewer items. */
    private static double valueMultiplier(TransmutationSettings settings) {
        double[] counts = MaxEntSampler.SUPPORTED_EXPECTED_COUNTS;
        return (double) TransmuterBlockEntity.OUTPUT_COUNT / settings.sampleCount()
                * counts[TransmutationSettings.DEFAULT_COUNT_INDEX] / counts[settings.countIndex()]
                * settings.voidMultiplier();
    }

    private static String expectedCount(int countIndex) {
        return NUMBER.format(MaxEntSampler.SUPPORTED_EXPECTED_COUNTS[countIndex]);
    }

    /** The attached count, ignoring any beyond the most that have an effect. */
    private int effectiveCount(TransmuterAddon addon) {
        return Math.min(menu.getAddonCount(addon), addon.maxEffective());
    }
}
