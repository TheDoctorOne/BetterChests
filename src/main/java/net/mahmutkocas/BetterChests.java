package net.mahmutkocas;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.crafting.BenchRecipeRegistry;
import com.hypixel.hytale.builtin.crafting.interaction.OpenBenchPageInteraction;
import com.hypixel.hytale.builtin.crafting.interaction.OpenProcessingBenchInteraction;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.mahmutkocas.betterchest.ChestCraftingManager;
import net.mahmutkocas.betterchest.ChestCraftingSystem;
import net.mahmutkocas.betterchest.OpenBetterChestInteraction;
import net.mahmutkocas.betterchest.UpgradeableItemContainerState;

import javax.annotation.Nonnull;
import java.util.Map;

public class BetterChests extends JavaPlugin {
    private static BetterChests instance;

    private ComponentType<EntityStore, ChestCraftingManager> chestCraftManagerType;
    private static final Map<String, BenchRecipeRegistry> registries = new Object2ObjectOpenHashMap<>();

    public BetterChests(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    private static void onRecipeLoad(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
            for (BenchRecipeRegistry registry : registries.values()) {
                registry.removeRecipe(recipe.getId());
            }

            if (recipe.getBenchRequirement() != null) {
                for (BenchRequirement benchRequirement : recipe.getBenchRequirement()) {
                    BenchRecipeRegistry benchRecipeRegistry = registries.computeIfAbsent(benchRequirement.id, BenchRecipeRegistry::new);
                    benchRecipeRegistry.addRecipe(benchRequirement, recipe);
                }
            }
        }

        computeBenchRecipeRegistries();
    }

    private static void computeBenchRecipeRegistries() {
        for (BenchRecipeRegistry registry : registries.values()) {
            registry.recompute();
        }
    }

    @Override
    protected void setup() {

        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, BetterChests::onRecipeLoad);
        Interaction.CODEC.register("OpenBetterChest", OpenBetterChestInteraction.class, OpenBetterChestInteraction.CODEC);
        chestCraftManagerType = this.getEntityStoreRegistry().registerComponent(ChestCraftingManager.class, ChestCraftingManager::new);
        getEntityStoreRegistry().registerSystem(new ChestCraftingSystem(this.chestCraftManagerType));
        this.getBlockStateRegistry().registerBlockState(
                UpgradeableItemContainerState.class,
                "DoctorOne_UpgradeableContainer",
                UpgradeableItemContainerState.CODEC,
                UpgradeableItemContainerState.UpgradeableContainerStateData.class,
                UpgradeableItemContainerState.UpgradeableContainerStateData.CODEC
        );
    }

    public ComponentType<EntityStore, ChestCraftingManager> getChestCraftManagerType() {
        return chestCraftManagerType;
    }

    public static BetterChests get() {
        return instance;
    }


    public static boolean isValidCraftingMaterialForBench(UpgradeableItemContainerState containerState, ItemStack itemStack) {
        BenchRecipeRegistry benchRecipeRegistry = registries.get(containerState.getItemContainer().getId());
        return benchRecipeRegistry == null ? false : benchRecipeRegistry.isValidCraftingMaterial(itemStack);
    }

    public static boolean isValidUpgradeMaterialForBench(UpgradeableItemContainerState containerState, ItemStack itemStack) {
        BenchUpgradeRequirement nextLevelUpgradeMaterials = containerState.getUpgradeRequirement();
        if (nextLevelUpgradeMaterials == null) {
            return false;
        } else {
            for (MaterialQuantity upgradeMaterial : nextLevelUpgradeMaterials.getInput()) {
                if (itemStack.getItemId().equals(upgradeMaterial.getItemId())) {
                    return true;
                }

                ItemResourceType[] resourceTypeId = itemStack.getItem().getResourceTypes();
                if (resourceTypeId != null) {
                    for (ItemResourceType resTypeId : resourceTypeId) {
                        if (resTypeId.id.equals(upgradeMaterial.getResourceTypeId())) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }
}