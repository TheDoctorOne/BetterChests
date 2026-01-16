package net.mahmutkocas.betterchest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.window.SortItemsAction;
import com.hypixel.hytale.protocol.packets.window.TierUpgradeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SortType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class BetterChestContainerWindow extends InteractiveCustomUIPage<BetterChestContainerWindow.ChestEventData> implements ItemContainerWindow, MaterialContainerWindow {

    private static final String GRID_PLAYER = "PLAYER";
    private static final String GRID_CHEST  = "CHEST";
    private ItemContainer playerInv;

    private MaterialExtraResourcesSection extraResourcesSection = new MaterialExtraResourcesSection();
    private UpgradeableItemContainerState containerState;


    // Server-side “cursor”
    private ItemStack carried = null;

    // If you want “return to origin if drop fails”
    private String dragFromGrid = null;
    private int dragFromSlot = -1;

    public BetterChestContainerWindow(@Nonnull PlayerRef playerRef,
                                      @Nonnull ItemContainer playerInv,
                                      UpgradeableItemContainerState state) {
        super(playerRef, CustomPageLifetime.CanDismiss, ChestEventData.CODEC);
        this.playerInv = playerInv;
        this.containerState = state;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        commands.append("Pages/CustomChestPage.ui");

        // Bind slot events (slot-specific types are supported).
        events.addEventBinding(CustomUIEventBindingType.SlotClicking, "#PlayerGrid",
                EventData.of("Type", "SlotClick").append("Grid", GRID_PLAYER), false);

        events.addEventBinding(CustomUIEventBindingType.SlotClicking, "#ChestGrid",
                EventData.of("Type", "SlotClick").append("Grid", GRID_CHEST), false);

        // Drag lifecycle
        events.addEventBinding(CustomUIEventBindingType.SlotClickPressWhileDragging, "#PlayerGrid",
                EventData.of("Type", "DragPress").append("Grid", GRID_PLAYER), false);

        events.addEventBinding(CustomUIEventBindingType.SlotClickPressWhileDragging, "#ChestGrid",
                EventData.of("Type", "DragPress").append("Grid", GRID_CHEST), false);

        events.addEventBinding(CustomUIEventBindingType.Dropped, "#PlayerGrid",
                EventData.of("Type", "Dropped").append("Grid", GRID_PLAYER), false);

        events.addEventBinding(CustomUIEventBindingType.Dropped, "#ChestGrid",
                EventData.of("Type", "Dropped").append("Grid", GRID_CHEST), false);

        events.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, "#PlayerGrid",
                EventData.of("Type", "DragDone").append("Grid", GRID_PLAYER), false);

        events.addEventBinding(CustomUIEventBindingType.SlotMouseDragCompleted, "#ChestGrid",
                EventData.of("Type", "DragDone").append("Grid", GRID_CHEST), false);

        // Initial paint
        refreshBoth(commands);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ChestEventData data) {


        HytaleLogger.getLogger()
                .at(Level.WARNING).log("LOG -> " + String.valueOf(data));
        if (data.type == null) return;

        switch (data.type) {
            case "SlotClick" -> handleSlotClick(ref, store, data);
            case "DragPress" -> handleDragPress(ref, store, data);
            case "Dropped"   -> handleDropped(ref, store, data);
            case "DragDone"  -> handleDragDone(ref, store, data);
        }
    }

    private void handleSlotClick(Ref<EntityStore> ref, Store<EntityStore> store, ChestEventData d) {
        if (d.grid == null || d.slot == null) return;

        boolean isShift = Boolean.TRUE.equals(d.shiftDown);
        boolean isLeft  = d.mouseButton == null || "LEFT".equalsIgnoreCase(d.mouseButton);

        ItemContainer from = getContainerForGrid(d.grid);
        ItemContainer to   = getOtherContainer(d.grid);
        short slot = (short) (int) d.slot;

        if (isShift && isLeft) {
            // Shift+Left = move WHOLE stack to the other container
            ItemStack s = from.getItemStack(slot);
            if (!ItemStack.isEmpty(s)) {
                from.moveItemStackFromSlot(slot, to, /*allOrNothing*/ false, /*filter*/ true);
                UICommandBuilder u = new UICommandBuilder();
                refreshBoth(u);
                sendUpdate(u, null, false);
            }
            return;
        }

        // Normal click behavior: pick up / place / swap with carried stack
        if (carried == null || ItemStack.isEmpty(carried)) {
            // pick up stack from slot
            ItemStack picked = from.getItemStack(slot);
            if (!ItemStack.isEmpty(picked)) {
                from.removeItemStackFromSlot(slot, /*filter*/ true);
                carried = picked;
            }
        } else {
            // place into slot (merge if possible, otherwise swap)
            carried = placeCarriedIntoSlot(from, slot, carried);
        }

        UICommandBuilder u = new UICommandBuilder();
        refreshBoth(u);
        sendUpdate(u, null, false);
    }

    private void handleDragPress(Ref<EntityStore> ref, Store<EntityStore> store, ChestEventData d) {
        // Treat drag-press as “start dragging from this slot if cursor empty”
        if (d.grid == null || d.slot == null) return;

        ItemContainer from = getContainerForGrid(d.grid);
        short slot = (short) (int) d.slot;

        if (carried == null || ItemStack.isEmpty(carried)) {
            ItemStack picked = from.getItemStack(slot);
            if (!ItemStack.isEmpty(picked)) {
                from.removeItemStackFromSlot(slot, true);
                carried = picked;

                dragFromGrid = d.grid;
                dragFromSlot = d.slot;
            }
        }
    }

    private void handleDropped(Ref<EntityStore> ref, Store<EntityStore> store, ChestEventData d) {
        // Drop onto a slot (preferred)
        if (carried == null || ItemStack.isEmpty(carried)) return;
        if (d.grid == null || d.slot == null) return;

        ItemContainer to = getContainerForGrid(d.grid);
        short slotTo = (short) (int) d.slot;

        carried = placeCarriedIntoSlot(to, slotTo, carried);

        UICommandBuilder u = new UICommandBuilder();
        refreshBoth(u);
        sendUpdate(u, null, false);
    }

    private void handleDragDone(Ref<EntityStore> ref, Store<EntityStore> store, ChestEventData d) {
        // If drag completed without a valid drop target, return carried to origin if possible
        if (carried == null || ItemStack.isEmpty(carried)) return;

        if (dragFromGrid != null && dragFromSlot >= 0) {
            ItemContainer origin = getContainerForGrid(dragFromGrid);
            short originSlot = (short) dragFromSlot;

            // Try to put it back in the origin slot first, otherwise “add” anywhere
            ItemStack remainder = placeCarriedIntoSlot(origin, originSlot, carried);
            if (!ItemStack.isEmpty(remainder)) {
                ItemStackTransaction addTx = origin.addItemStack(remainder, false, false, true);
                carried = addTx.getRemainder();
            } else {
                carried = null;
            }
        }

        dragFromGrid = null;
        dragFromSlot = -1;

        UICommandBuilder u = new UICommandBuilder();
        refreshBoth(u);
        sendUpdate(u, null, false);
    }

    /**
     * Places a carried stack into a specific slot:
     * - If slot empty => set slot = carried, return remainder empty
     * - If stackable => fill up to max stack, return remainder
     * - Else swap => slot becomes carried, return old slot stack
     */
    private ItemStack placeCarriedIntoSlot(ItemContainer container, short slot, ItemStack inHand) {
        ItemStack existing = container.getItemStack(slot);

        if (ItemStack.isEmpty(existing)) {
            container.setItemStackForSlot(slot, inHand, true);
            return ItemStack.EMPTY;
        }

        if (inHand.isStackableWith(existing)) {
            int max = existing.getItem().getMaxStack();
            int total = existing.getQuantity() + inHand.getQuantity();
            int placed = Math.min(max, total);
            int remainder = total - placed;

            container.setItemStackForSlot(slot, existing.withQuantity(placed), true);
            return (remainder > 0) ? inHand.withQuantity(remainder) : ItemStack.EMPTY;
        }

        // swap
        container.setItemStackForSlot(slot, inHand, true);
        return existing;
    }

    private ItemContainer getContainerForGrid(String grid) {
        return GRID_CHEST.equalsIgnoreCase(grid) ? getItemContainer() : playerInv;
    }

    private ItemContainer getOtherContainer(String grid) {
        return GRID_CHEST.equalsIgnoreCase(grid) ? playerInv : getItemContainer();
    }

    private void refreshBoth(UICommandBuilder commands) {
        commands.set("#PlayerGrid.Slots", buildSlots(playerInv));
        commands.set("#ChestGrid.Slots", buildSlots(getItemContainer()));
    }

    private ItemGridSlot[] buildSlots(ItemContainer c) {
        int cap = c.getCapacity();
        ItemGridSlot[] slots = new ItemGridSlot[cap];
        for (short i = 0; i < cap; i++) {
            ItemStack s = c.getItemStack(i);
            slots[i] = ItemStack.isEmpty(s) ? new ItemGridSlot() : new ItemGridSlot(s);
        }
        return slots;
    }

    @Nonnull
    @Override
    public MaterialExtraResourcesSection getExtraResourcesSection() {
        if (!this.extraResourcesSection.isValid()) {
            ChestCraftingManager.feedExtraResourcesSection(this.containerState, this.extraResourcesSection);
        }

        return this.extraResourcesSection;
    }

    @Override
    public void invalidateExtraResources() {
        this.extraResourcesSection.setValid(false);
        invalidate();
    }

    @Override
    public boolean isValid() {
        return this.extraResourcesSection.isValid();
    }

    public void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WindowAction action) {
        if (action instanceof SortItemsAction sortAction) {
            SortType sortType = SortType.fromPacket(sortAction.sortType);
            Player playerComponent = store.getComponent(ref, Player.getComponentType());

            assert playerComponent != null;

            playerComponent.getInventory().setSortType(sortType);
            this.getItemContainer().sortItems(sortType);
            this.invalidate();
        }
        if (action instanceof TierUpgradeAction tierUpgradeAction) {
            ChestCraftingManager craftingManager = store.getComponent(ref, ChestCraftingManager.getComponentType());
            if (craftingManager == null) {
                return;
            }

            craftingManager.startTierUpgrade(ref, store, this);
        }
    }

    private void invalidate() {
        rebuild();
    }

    private void updateGrids(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commands) {
        Player player = store.getComponent(ref, Player.getComponentType());
        Inventory inv = player.getInventory();

        // Good “single grid” view for the left side:
        // pick one that matches your UX (storage first vs hotbar first)
        CombinedItemContainer playerView = inv.getCombinedStorageFirst();

        commands.set("#PlayerInventoryGrid.Slots", toGridSlots(playerView));
        commands.set("#ChestInventoryGrid.Slots", toGridSlots(getItemContainer()));
    }

    private ItemGridSlot[] toGridSlots(ItemContainer container) {
        int size = container.getCapacity(); // capacity/size method name may differ in your mappings
        ItemGridSlot[] slots = new ItemGridSlot[size];

        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItemStack((short) i); // typical API shape (may differ)
            slots[i] = (stack == null || stack.isEmpty()) ? new ItemGridSlot() : new ItemGridSlot(stack);
        }
        return slots;
    }

    @Nonnull
    @Override
    public ItemContainer getItemContainer() {
        return containerState.getItemContainer();
    }

    public static class ChestEventData {
        public static final BuilderCodec<ChestEventData> CODEC =
                BuilderCodec.builder(ChestEventData.class, ChestEventData::new)

                        // Our static keys from EventData.of(...).append(...)
                        .append(new KeyedCodec<>("Type", Codec.STRING), (d, v) -> d.type = v, d -> d.type).add()
                        .append(new KeyedCodec<>("Grid", Codec.STRING), (d, v) -> d.grid = v, d -> d.grid).add()

                        // These keys depend on what the slot events provide.
                        // You’ll commonly get some combination of: Slot index, mouse button, shift state.
                        // Keep them nullable so your page doesn’t break if a key is missing.
                        .append(new KeyedCodec<>("Slot", Codec.INTEGER), (d, v) -> d.slot = v, d -> d.slot).add()
                        .append(new KeyedCodec<>("MouseButton", Codec.STRING), (d, v) -> d.mouseButton = v, d -> d.mouseButton).add()
                        .append(new KeyedCodec<>("ShiftDown", Codec.BOOLEAN), (d, v) -> d.shiftDown = v, d -> d.shiftDown).add()

                        .build();

        public String type;
        public String grid;

        public Integer slot;          // clicked/dropped slot index
        public String mouseButton;    // "LEFT"/"RIGHT" (example)
        public Boolean shiftDown;     // shift pressed

        @Override
        public String toString() {
            return "ChestEventData{" +
                    "type='" + type + '\'' +
                    ", grid='" + grid + '\'' +
                    ", slot=" + slot +
                    ", mouseButton='" + mouseButton + '\'' +
                    ", shiftDown=" + shiftDown +
                    '}';
        }
    }
}
