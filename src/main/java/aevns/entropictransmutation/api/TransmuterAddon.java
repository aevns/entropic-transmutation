package aevns.entropictransmutation.api;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.block.TransmuterAddonBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * An addon a transmuter can use, registered under {@link #REGISTRY_KEY}. The block attaches to a transmuter or monitor
 * and is what's counted; the description shown in the GUI uses the translation key {@code <block's key>.description}.
 *
 * @param block        the block that provides this addon
 * @param maxEffective the most attached copies that have an effect
 * @param effect       what the attached copies do to a transmutation
 * @param appearance   how the addon is shown in the transmuter's GUI
 */
public record TransmuterAddon(Supplier<? extends TransmuterAddonBlock> block, int maxEffective, Effect effect,
                              Appearance appearance) {
    public static final ResourceKey<Registry<TransmuterAddon>> REGISTRY_KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(EntropicTransmutation.MODID, "transmuter_addon"));

    @FunctionalInterface
    public interface Effect {
        /** Adjusts settings for count attached copies, from 1 to maxEffective. */
        void apply(int count, TransmutationSettings settings);
    }

    /**
     * @param texture      the texture holding the symbol, 256 x 256 in size
     * @param u            the left edge of the 7 x 8 symbol sprite
     * @param v            the top edge of the sprite used while attached; the inactive one is 8 px below it
     * @param color        the color of the attached count
     * @param shadowColor  the color of the attached count's shadow
     * @param tooltipColor the color of the heading and effects in the tooltip
     */
    public record Appearance(ResourceLocation texture, int u, int v, int color, int shadowColor,
                             ChatFormatting tooltipColor) {
    }

    /** Applies this addon's effect for the given number of attached copies, ignoring any beyond maxEffective. */
    public void applyTo(TransmutationSettings settings, int attached) {
        if (attached > 0) effect.apply(Math.min(attached, maxEffective), settings);
    }
}
