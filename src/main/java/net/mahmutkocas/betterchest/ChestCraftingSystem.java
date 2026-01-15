package net.mahmutkocas.betterchest;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ChestCraftingSystem  extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, ChestCraftingManager> craftingManagerComponentType;

    public ChestCraftingSystem(ComponentType<EntityStore, ChestCraftingManager> craftingManagerComponentType) {
        this.craftingManagerComponentType = craftingManagerComponentType;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return this.craftingManagerComponentType;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        ChestCraftingManager craftingManagerComponent = archetypeChunk.getComponent(index, this.craftingManagerComponentType);
        if(craftingManagerComponent == null) {
            return;
        }
        craftingManagerComponent.tick(ref, commandBuffer, dt);
    }
}
