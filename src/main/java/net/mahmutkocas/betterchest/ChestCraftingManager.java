package net.mahmutkocas.betterchest;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ItemQuantity;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.mahmutkocas.BetterChests;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ChestCraftingManager implements Component<EntityStore> {
    @Nullable
    private UpgradeJob upgradingJob;
    private int x;
    private int y;
    private int z;
    @Nullable
    private BlockType blockType;

    public ChestCraftingManager() {
    }

    private ChestCraftingManager(@Nonnull ChestCraftingManager other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.blockType = other.blockType;
        this.upgradingJob = other.upgradingJob;
    }

    public static ComponentType<EntityStore, ChestCraftingManager> getComponentType() {
        return BetterChests.get().getChestCraftManagerType();
    }

    public static void feedExtraResourcesSection(UpgradeableItemContainerState containerState, MaterialExtraResourcesSection extraResourcesSection) {
        List<ItemContainer> chests = getContainersAroundBench(containerState);
        ItemContainer itemContainer = EmptyItemContainer.INSTANCE;
        if (!chests.isEmpty()) {
            itemContainer = new CombinedItemContainer((ItemContainer[]) chests.stream().map((container) -> {
                DelegateItemContainer<ItemContainer> delegate = new DelegateItemContainer(container);
                delegate.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
                return delegate;
            }).toArray((x$0) -> {
                return new ItemContainer[x$0];
            }));
        }

        Map<String, ItemQuantity> materials = new Object2ObjectOpenHashMap();
        Iterator var5 = chests.iterator();

        while (var5.hasNext()) {
            ItemContainer chest = (ItemContainer) var5.next();
            chest.forEach((i, itemStack) -> {
                if (BetterChests.isValidUpgradeMaterialForBench(containerState, itemStack) || BetterChests.isValidCraftingMaterialForBench(containerState, itemStack)) {
                    ItemQuantity var10000 = (ItemQuantity) materials.computeIfAbsent(itemStack.getItemId(), (k) -> {
                        return new ItemQuantity(itemStack.getItemId(), 0);
                    });
                    var10000.quantity += itemStack.getQuantity();
                }
            });
        }

        extraResourcesSection.setItemContainer((ItemContainer) itemContainer);
        extraResourcesSection.setExtraMaterials((ItemQuantity[]) materials.values().toArray(new ItemQuantity[0]));
        extraResourcesSection.setValid(true);
    }

    protected static List<ItemContainer> getContainersAroundBench(@Nonnull UpgradeableItemContainerState containerState) {
        List<ItemContainer> containers = new ObjectArrayList<>();
        World world = containerState.getChunk().getWorld();
        Store<ChunkStore> store = world.getChunkStore().getStore();
        int limit = world.getGameplayConfig().getCraftingConfig().getBenchMaterialChestLimit();
        double horizontalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialHorizontalChestSearchRadius();
        double verticalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialVerticalChestSearchRadius();
        Vector3d blockPos = containerState.getBlockPosition().toVector3d();
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(containerState.getBlockType().getHitboxTypeIndex());
        BlockBoundingBoxes.RotatedVariantBoxes rotatedHitbox = hitboxAsset.get(containerState.getRotationIndex());
        Box boundingBox = rotatedHitbox.getBoundingBox();
        double benchWidth = boundingBox.width();
        double benchHeight = boundingBox.height();
        double benchDepth = boundingBox.depth();
        double extraSearchRadius = Math.max(benchWidth, Math.max(benchDepth, benchHeight)) - 1.0;
        SpatialResource<Ref<ChunkStore>, ChunkStore> blockStateSpatialStructure = store.getResource(BlockStateModule.get().getItemContainerSpatialResourceType());
        ObjectList<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
        blockStateSpatialStructure.getSpatialStructure()
                .ordered3DAxis(blockPos, horizontalRadius + extraSearchRadius, verticalRadius + extraSearchRadius, horizontalRadius + extraSearchRadius, results);
        if (!results.isEmpty()) {
            double minX = blockPos.x + boundingBox.min.x - horizontalRadius;
            double minY = blockPos.y + boundingBox.min.y - verticalRadius;
            double minZ = blockPos.z + boundingBox.min.z - horizontalRadius;
            double maxX = blockPos.x + boundingBox.max.x + horizontalRadius;
            double maxY = blockPos.y + boundingBox.max.y + verticalRadius;
            double maxZ = blockPos.z + boundingBox.max.z + horizontalRadius;

            for (Ref<ChunkStore> ref : results) {
                if (BlockState.getBlockState(ref, ref.getStore()) instanceof ItemContainerState chest) {
                    Vector3d chestPos = chest.getCenteredBlockPosition();
                    if (chestPos.x >= minX && chestPos.x <= maxX && chestPos.y >= minY && chestPos.y <= maxY && chestPos.z >= minZ && chestPos.z <= maxZ) {
                        containers.add(chest.getItemContainer());
                        if (containers.size() >= limit) {
                            break;
                        }
                    }
                }
            }
        }

        return containers;
    }

    private static List<MaterialQuantity> getInputMaterials(@Nonnull MaterialQuantity[] input) {
        return getInputMaterials(input, 1);
    }

    private static List<MaterialQuantity> getInputMaterials(@Nonnull MaterialQuantity[] input, int quantity) {
        ObjectList<MaterialQuantity> materials = new ObjectArrayList<>();

        for (MaterialQuantity craftingMaterial : input) {
            String itemId = craftingMaterial.getItemId();
            String resourceTypeId = craftingMaterial.getResourceTypeId();
            int materialQuantity = craftingMaterial.getQuantity();
            BsonDocument metadata = craftingMaterial.getMetadata();
            materials.add(new MaterialQuantity(itemId, resourceTypeId, null, materialQuantity * quantity, metadata));
        }

        return materials;
    }

    public static void setChest(int x, int y, int z, BlockType blockType) {
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return null;
    }

    public boolean startTierUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, BetterChestContainerWindow window) {
        if (this.upgradingJob != null) {
            return false;
        } else {
            BenchUpgradeRequirement requirements = this.getRequirement(store);
            if (requirements == null) {
                return false;
            } else {
                List<MaterialQuantity> input = getInputMaterials(requirements.getInput());
                if (input.isEmpty()) {
                    return false;
                } else {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player.getGameMode() != GameMode.Creative) {
                        CombinedItemContainer combined = new CombinedItemContainer(
                                player.getInventory().getCombinedBackpackStorageHotbar(), window.getExtraResourcesSection().getItemContainer()
                        );
                        if (!combined.canRemoveMaterials(input)) {
                            return false;
                        }
                    }

                    this.upgradingJob = new ChestCraftingManager.UpgradeJob(window, requirements.getTimeSeconds());
                    return true;
                }
            }
        }
    }

    private int finishTierUpgrade(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        if (this.upgradingJob == null) {
            return 0;
        } else {
            BlockState state = store.getExternalData().getWorld().getState(this.x, this.y, this.z, true);
            UpgradeableItemContainerState containerState = state instanceof UpgradeableItemContainerState ? (UpgradeableItemContainerState) state : null;
            if (containerState != null && containerState.getTierLevel() != 0) {
                BenchUpgradeRequirement requirements = containerState.getUpgradeRequirement();
                if (requirements == null) {
                    return containerState.getTierLevel();
                } else {
                    List<MaterialQuantity> input = getInputMaterials(requirements.getInput());
                    if (input.isEmpty()) {
                        return containerState.getTierLevel();
                    } else {
                        Player player = store.getComponent(ref, Player.getComponentType());
                        boolean canUpgrade = player.getGameMode() == GameMode.Creative;
                        if (!canUpgrade) {
                            CombinedItemContainer combined = new CombinedItemContainer(
                                    player.getInventory().getCombinedBackpackStorageHotbar(), this.upgradingJob.window.getExtraResourcesSection().getItemContainer()
                            );
                            combined = new CombinedItemContainer(combined, this.upgradingJob.window.getExtraResourcesSection().getItemContainer());
                            ListTransaction<MaterialTransaction> materialTransactions = combined.removeMaterials(input);
                            if (materialTransactions.succeeded()) {
                                List<ItemStack> consumed = new ObjectArrayList<>();

                                for (MaterialTransaction transaction : materialTransactions.getList()) {
                                    for (MaterialSlotTransaction matSlot : transaction.getList()) {
                                        consumed.add(matSlot.getOutput());
                                    }
                                }

                                containerState.addUpgradeItems(consumed);
                                canUpgrade = true;
                            }
                        }

                        if (canUpgrade) {
                            containerState.setTierLevel(containerState.getTierLevel() + 1);
                        }

                        return containerState.getTierLevel();
                    }
                }
            } else {
                return 0;
            }
        }
    }

    public void tick(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> store, float dt) {
        if (this.upgradingJob == null) {
            return;
        }

        if (dt > 0.0F) {
            this.upgradingJob.timeSecondsCompleted += dt;
        }

//        this.upgradingJob.window.updateUpgradeJob(this.upgradingJob.computeLoadingPercent());
        if (this.upgradingJob.timeSecondsCompleted >= this.upgradingJob.timeSeconds) {
//            this.upgradingJob.window.updateTierLevel(this.finishTierUpgrade(ref, store));
            this.upgradingJob = null;
        }

    }

    private BenchUpgradeRequirement getRequirement(Store<EntityStore> store) {
        BlockState state = store.getExternalData().getWorld().getState(this.x, this.y, this.z, true);
        return state instanceof UpgradeableItemContainerState ? ((UpgradeableItemContainerState) state).getUpgradeRequirement() : null;
    }

    public static class UpgradeJob {
        @Nonnull
        private final BetterChestContainerWindow window;
        private final float timeSeconds;
        private float timeSecondsCompleted;

        private UpgradeJob(@Nonnull BetterChestContainerWindow window, float timeSeconds) {
            this.window = window;
            this.timeSeconds = timeSeconds;
        }

        public float computeLoadingPercent() {
            return this.timeSeconds <= 0.0F ? 1.0F : Math.min(this.timeSecondsCompleted / this.timeSeconds, 1.0F);
        }
    }
}
