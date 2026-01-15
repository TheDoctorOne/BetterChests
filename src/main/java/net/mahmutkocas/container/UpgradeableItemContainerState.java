package net.mahmutkocas.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

public class UpgradeableItemContainerState extends ItemContainerState {

    public static final Codec<UpgradeableItemContainerState> CODEC = BuilderCodec.builder(UpgradeableItemContainerState.class, UpgradeableItemContainerState::new, BlockState.BASE_CODEC)
            .append(new KeyedCodec<>("Custom", Codec.BOOLEAN), (state, o) -> state.custom = o, state -> state.custom)
            .add()
            .append(new KeyedCodec<>("AllowViewing", Codec.BOOLEAN), (state, o) -> state.allowViewing = o, state -> state.allowViewing)
            .add()
            .append(new KeyedCodec<>("Droplist", Codec.STRING), (state, o) -> state.droplist = o, state -> state.droplist)
            .add()
            .append(new KeyedCodec<>("Marker", WorldMapManager.MarkerReference.CODEC), (state, o) -> state.marker = o, state -> state.marker)
            .add()
            .append(new KeyedCodec<>("ItemContainer", SimpleItemContainer.CODEC), (state, o) -> state.itemContainer = o, state -> state.itemContainer)
            .add()
            .append(new KeyedCodec<>("Capacity", Codec.SHORT), (state, o) -> state.capacity = o, state -> state.capacity)
            .add()
            .build();

    short capacity;

    @Override
    public boolean initialize(@Nonnull BlockType blockType) {
        if (blockType.getState() instanceof ItemContainerStateData itemContainerStateData) {
            capacity = itemContainerStateData.getCapacity();
        }

        List<ItemStack> remainder = new ObjectArrayList<>();
        this.itemContainer = ItemContainer.ensureContainerCapacity(this.itemContainer, capacity, SimpleItemContainer::new, remainder);
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
}
