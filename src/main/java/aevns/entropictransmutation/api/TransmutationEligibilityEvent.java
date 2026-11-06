package aevns.entropictransmutation.api;

import moze_intel.projecte.api.ItemInfo;
import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge event bus for every item with an EMC value whenever the transmutation sampler is rebuilt, which
 * happens each time ProjectE remaps EMC values. Starts with the result of the config's filter, and listeners can
 * change it to add or remove the item from transmutation outputs. Fired on the server thread.
 */
public class TransmutationEligibilityEvent extends Event {
    private final ItemInfo item;
    private boolean eligible;

    public TransmutationEligibilityEvent(ItemInfo item, boolean eligible) {
        this.item = item;
        this.eligible = eligible;
    }

    public ItemInfo getItem() {
        return item;
    }

    /** Whether the item can currently appear in transmutation outputs. */
    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }
}
