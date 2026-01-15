package net.mahmutkocas;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import net.mahmutkocas.container.UpgradeableItemContainerState;

import javax.annotation.Nonnull;

public class BetterChests extends JavaPlugin {

    public BetterChests(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getBlockStateRegistry().registerBlockState(UpgradeableItemContainerState.class,
                "DoctorOne_UpgradeableContainer", UpgradeableItemContainerState.CODEC,
                UpgradeableItemContainerState.ItemContainerStateData.class,
                UpgradeableItemContainerState.ItemContainerStateData.CODEC);
    }
}