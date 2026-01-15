package net.mahmutkocas.betterchest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.Short2ObjectMapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

public class UpgradeableItemContainer extends SimpleItemContainer {
    public static final BuilderCodec<UpgradeableItemContainer> CODEC = BuilderCodec.builder(UpgradeableItemContainer.class, UpgradeableItemContainer::new)
            .addField(new KeyedCodec<>("Id", Codec.STRING), (container, s) -> container.id = s, bench -> bench.id)
            .append(new KeyedCodec<>("Capacity", Codec.SHORT), (o, i) -> o.capacity = i, o -> o.capacity)
            .addValidator(Validators.greaterThanOrEqual((short)0))
            .add()
            .append(new KeyedCodec<>("Items", new Short2ObjectMapCodec<>(ItemStack.CODEC, Short2ObjectOpenHashMap::new, false)), (o, i) -> o.items = i, o -> o.items)
            .add()
            .afterDecode(i -> {
                if (i.items == null) {
                    i.items = new Short2ObjectOpenHashMap<>(i.capacity);
                }

                i.items.short2ObjectEntrySet().removeIf(e -> e.getShortKey() < 0 || e.getShortKey() >= i.capacity || ItemStack.isEmpty(e.getValue()));
            })
            .build();

    protected String id;

    public UpgradeableItemContainer() {
        super();
    }

    public UpgradeableItemContainer(short capacity) {
        super(capacity);
    }

    public void setCapacity(short capacity) {
        this.capacity = capacity;
    }

    public String getId() {
        return this.id;
    }
}
