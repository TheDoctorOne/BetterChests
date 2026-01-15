package net.mahmutkocas.betterchest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.DestroyableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.meta.state.MarkerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class UpgradeableItemContainerState extends BlockState implements ItemContainerBlockState, DestroyableBlockState, MarkerBlockState {

    public static final Codec<UpgradeableItemContainerState> CODEC = BuilderCodec.builder(UpgradeableItemContainerState.class, UpgradeableItemContainerState::new, BlockState.BASE_CODEC)
            .append(new KeyedCodec<>("Custom", Codec.BOOLEAN), (state, o) -> state.custom = o, state -> state.custom)
            .add()
            .append(new KeyedCodec<>("AllowViewing", Codec.BOOLEAN), (state, o) -> state.allowViewing = o, state -> state.allowViewing)
            .add()
            .append(new KeyedCodec<>("Marker", WorldMapManager.MarkerReference.CODEC), (state, o) -> state.marker = o, state -> state.marker)
            .add()
            .append(new KeyedCodec<>("ItemContainer", UpgradeableItemContainer.CODEC), (state, o) -> state.itemContainer = o, state -> state.itemContainer)
            .add()
            .append(new KeyedCodec<>("UpgradeRequirement", new ArrayCodec<>(BenchUpgradeRequirement.CODEC, BenchUpgradeRequirement[]::new)), (tier, d) -> tier.upgradeRequirement = d, tier -> tier.upgradeRequirement)
            .add()
            .appendInherited(
                    new KeyedCodec<>("TierLevel", Codec.INTEGER),
                    (state, o) -> state.tierLevel = o,
                    state -> state.tierLevel,
                    (state, parent) -> state.tierLevel = parent.tierLevel
            )
            .add()
            .appendInherited(
                    new KeyedCodec<>("UpgradeItems", new ArrayCodec<>(ItemStack.CODEC, ItemStack[]::new)),
                    (state, o) -> state.upgradeItems = o,
                    state -> state.upgradeItems,
                    (state, parent) -> state.upgradeItems = parent.upgradeItems
            )
            .add()
            .build();

    private final Map<UUID, BetterChestContainerBlockWindow> windows = new ConcurrentHashMap<>();
    protected boolean custom;
    protected boolean allowViewing = true;
    protected UpgradeableItemContainer itemContainer;
    protected WorldMapManager.MarkerReference marker;
    protected ItemStack[] upgradeItems = ItemStack.EMPTY_ARRAY;

    protected BenchUpgradeRequirement[] upgradeRequirement;
    protected int tierLevel = 1;

    @Override
    public boolean initialize(@Nonnull BlockType blockType) {

        if (blockType.getState() instanceof UpgradeableContainerStateData itemContainerStateData) {
            tierLevel = itemContainerStateData.getTierLevel();
            upgradeRequirement = itemContainerStateData.getUpgradeRequirement();
            HytaleLogger.getLogger()
                    .at(Level.WARNING).log(itemContainerStateData.toString());
        } else {
            HytaleLogger.getLogger()
                    .at(Level.WARNING).log("WHY IS THIS LIKE THIS???" + String.valueOf(blockType.getState()));
        }

        List<ItemStack> remainder = new ObjectArrayList<>();
        this.itemContainer = ItemContainer.ensureContainerCapacity(this.itemContainer, getCapacityFromTier(), UpgradeableItemContainer::new, remainder);
        this.itemContainer.registerChangeEvent(EventPriority.LAST, this::onItemChange);
        if (!remainder.isEmpty()) {
            WorldChunk chunk = this.getChunk();
            World world = chunk.getWorld();
            Store<EntityStore> store = world.getEntityStore().getStore();
            HytaleLogger.getLogger()
                    .at(Level.WARNING)
                    .withCause(new Throwable())
                    .log(
                            "Dropping %d excess items from item container: %s at world: %s, chunk: %s, block: %s",
                            remainder.size(),
                            blockType.getId(),
                            chunk.getWorld().getName(),
                            chunk,
                            this.getPosition()
                    );
            Vector3i blockPosition = this.getBlockPosition();
            Holder<EntityStore>[] itemEntityHolders = ItemComponent.generateItemDrops(store, remainder, blockPosition.toVector3d(), Vector3f.ZERO);
            store.addEntities(itemEntityHolders, AddReason.SPAWN);
        }

        return true;
    }


    public void addUpgradeItems(List<ItemStack> consumed) {
        consumed.addAll(Arrays.asList(this.upgradeItems));
        this.upgradeItems = consumed.toArray(ItemStack[]::new);
        this.markNeedsSave();
    }

    public BenchUpgradeRequirement getUpgradeRequirement() {
        return upgradeRequirement.length <= tierLevel || tierLevel < 1 ? null : upgradeRequirement[tierLevel];
    }

    public short getCapacityFromTier() {
        return (short) (18 + (tierLevel - 1) * 9);
    }

    public int getTierLevel() {
        return tierLevel;
    }

    public void onItemChange(ItemContainer.ItemContainerChangeEvent event) {
        this.markNeedsSave();
    }

    private void dropUpgradeItems() {
        if (this.upgradeItems.length != 0) {
            World world = this.getChunk().getWorld();
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d dropPosition = this.getBlockPosition().toVector3d().add(0.5, 0.0, 0.5);
            Holder<EntityStore>[] itemEntityHolders = ItemComponent.generateItemDrops(entityStore, List.of(this.upgradeItems), dropPosition, Vector3f.ZERO);
            if (itemEntityHolders.length > 0) {
                world.execute(() -> entityStore.addEntities(itemEntityHolders, AddReason.SPAWN));
            }

            this.upgradeItems = ItemStack.EMPTY_ARRAY;
        }
    }


    @Override
    public void onDestroy() {
        WindowManager.closeAndRemoveAll(this.windows);
        WorldChunk chunk = this.getChunk();
        World world = chunk.getWorld();
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<ItemStack> allItemStacks = this.itemContainer.dropAllItemStacks();
        Vector3d dropPosition = this.getBlockPosition().toVector3d().add(0.5, 0.0, 0.5);
        Holder<EntityStore>[] itemEntityHolders = ItemComponent.generateItemDrops(store, allItemStacks, dropPosition, Vector3f.ZERO);
        if (itemEntityHolders.length > 0) {
            world.execute(() -> store.addEntities(itemEntityHolders, AddReason.SPAWN));
        }

        if (this.marker != null) {
            this.marker.remove();
        }

        this.dropUpgradeItems();

    }

    @Override
    public UpgradeableItemContainer getItemContainer() {
        return itemContainer;
    }

    @Nonnull
    public Map<UUID, BetterChestContainerBlockWindow> getWindows() {
        return this.windows;
    }

    @Override
    public void setMarker(WorldMapManager.MarkerReference marker) {
        this.marker = marker;
        this.markNeedsSave();
    }

    protected void onTierLevelChange() {
        this.getChunk().setBlockInteractionState(this.getBlockPosition(), this.getBaseBlockType(), this.getTierStateName());
    }

    public String getTierStateName() {
        return this.tierLevel > 1 ? "Tier" + this.tierLevel : "default";
    }

    public BlockType getBaseBlockType() {
        BlockType currentBlockType = this.getBlockType();
        String baseBlockKey = currentBlockType.getDefaultStateKey();
        BlockType baseBlockType = BlockType.getAssetMap().getAsset(baseBlockKey);
        if (baseBlockType == null) {
            baseBlockType = currentBlockType;
        }

        return baseBlockType;
    }

    public void setTierLevel(int newTierLevel) {
        if (this.tierLevel != newTierLevel) {
            this.tierLevel = newTierLevel;
            this.onTierLevelChange();
            this.markNeedsSave();
        }
    }

    public boolean isAllowViewing() {
        return true;
    }

    public boolean canOpen(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        return true;
    }

    public void onOpen(Ref<EntityStore> ref, World world, Store<EntityStore> store) {

    }

    public static class UpgradeableContainerStateData extends StateData {
        public static final BuilderCodec<UpgradeableContainerStateData> CODEC = BuilderCodec.builder(
                        UpgradeableContainerStateData.class, UpgradeableContainerStateData::new, StateData.DEFAULT_CODEC
                )
                .appendInherited(
                        new KeyedCodec<>("UpgradeRequirement", new ArrayCodec<>(BenchUpgradeRequirement.CODEC, BenchUpgradeRequirement[]::new)),
                        (state, o) -> state.upgradeRequirement = o,
                        state -> state.upgradeRequirement,
                        (state, parent) -> state.upgradeRequirement = parent.upgradeRequirement
                )
                .add()
                .appendInherited(
                        new KeyedCodec<>("TierLevel", Codec.INTEGER),
                        (state, o) -> state.tierLevel = o,
                        state -> state.tierLevel,
                        (state, parent) -> state.tierLevel = parent.tierLevel
                )
                .add()
                .build();
        protected BenchUpgradeRequirement[] upgradeRequirement;
        protected int tierLevel = 1;

        protected UpgradeableContainerStateData() {
        }

        public BenchUpgradeRequirement[] getUpgradeRequirement() {
            return upgradeRequirement;
        }

        public int getTierLevel() {
            return tierLevel;
        }

        @Override
        public String toString() {
            return "UpgradeableContainerStateData{" +
                    "upgradeRequirement=" + Arrays.toString(upgradeRequirement) +
                    ", tierLevel=" + tierLevel +
                    '}';
        }
    }
}
