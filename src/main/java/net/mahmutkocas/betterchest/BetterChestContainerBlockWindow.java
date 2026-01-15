package net.mahmutkocas.betterchest;

import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.packets.window.SortItemsAction;
import com.hypixel.hytale.protocol.packets.window.TierUpgradeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.BlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SortType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class BetterChestContainerBlockWindow extends BlockWindow implements ItemContainerWindow, MaterialContainerWindow {
    private float lastUpdatePercent;
    private long lastUpdateTimeMs;

    protected final JsonObject windowData = new JsonObject();
    @Nonnull
    private final UpgradeableItemContainer itemContainer;

    protected final UpgradeableItemContainerState containerState;

    @Nonnull
    private MaterialExtraResourcesSection extraResourcesSection = new MaterialExtraResourcesSection();

    public BetterChestContainerBlockWindow(@Nonnull WindowType windowType, @Nonnull UpgradeableItemContainerState containerState) {
        super(windowType, containerState.getBlockX(), containerState.getBlockY(), containerState.getBlockZ(), containerState.getRotationIndex(), containerState.getBlockType());
        this.itemContainer = containerState.getItemContainer();
        this.containerState = containerState;
        Item item = this.blockType.getItem();
        this.windowData.addProperty("id", this.itemContainer.getId());
        this.windowData.addProperty("name", item.getTranslationKey());
        this.windowData.addProperty("blockItemId", item.getId());
        this.windowData.addProperty("tierLevel", this.getTierLevel());
    }

    public void updateUpgradeJob(float percent) {
        this.windowData.addProperty("tierUpgradeProgress", percent);
        this.checkProgressInvalidate(percent);
    }

    private void checkProgressInvalidate(float percent) {
        if (this.lastUpdatePercent != percent) {
            long time = System.currentTimeMillis();
            if (percent >= 1.0F
                    || percent < this.lastUpdatePercent
                    || percent - this.lastUpdatePercent > 0.05F
                    || time - this.lastUpdateTimeMs > 500L
                    || this.lastUpdateTimeMs == 0L) {
                this.lastUpdatePercent = percent;
                this.lastUpdateTimeMs = time;
                this.invalidate();
            }
        }
    }

    protected int getTierLevel() {
        return this.containerState != null ? this.containerState.getTierLevel() : 1;
    }

    @Nonnull
    @Override
    public ItemContainer getItemContainer() {
        return itemContainer;
    }

    @Nonnull
    @Override
    public JsonObject getData() {
        return windowData;
    }

    @Override
    protected boolean onOpen0() {
        return true;
    }

    @Override
    protected void onClose0() {

    }

    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WindowAction action) {
        if (action instanceof SortItemsAction sortAction) {
            SortType sortType = SortType.fromPacket(sortAction.sortType);
            Player playerComponent = store.getComponent(ref, Player.getComponentType());

            assert playerComponent != null;

            playerComponent.getInventory().setSortType(sortType);
            this.itemContainer.sortItems(sortType);
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
        this.invalidate();
    }

    @Override
    public boolean isValid() {
        return this.extraResourcesSection.isValid();
    }

    public void updateTierLevel(int newValue) {
        this.windowData.addProperty("tierLevel", newValue);
        this.updateUpgradeJob(0.0F);
        this.setNeedRebuild();
        this.invalidate();
    }

    public static class UpgradeChestAction {

    }
}
