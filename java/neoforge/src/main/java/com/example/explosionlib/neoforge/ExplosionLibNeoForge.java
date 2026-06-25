package com.example.explosionlib.neoforge;

import com.example.explosionlib.CommonClass;
import com.example.explosionlib.Constants;
import com.example.explosionlib.item.ExploderItem;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Constants.MOD_ID)
public class ExplosionLibNeoForge {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    public static final DeferredItem<ExploderItem> EXPLODER =
        ITEMS.registerItem("exploder", ExploderItem::new, new Item.Properties().stacksTo(1));

    public ExplosionLibNeoForge(IEventBus modBus) {
        CommonClass.init();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(EXPLODER.get());
        }
    }
}
