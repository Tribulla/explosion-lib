package com.example.explosionlib.fabric;

import com.example.explosionlib.CommonClass;
import com.example.explosionlib.Constants;
import com.example.explosionlib.engine.ChunkForce;
import com.example.explosionlib.engine.Engine;
import com.example.explosionlib.engine.ExplosionScheduler;
import com.example.explosionlib.item.ExploderItem;
import com.example.explosionlib.network.ExplodePayload;
import com.example.explosionlib.network.ServerExplodeHandler;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ExplosionLibFabric implements ModInitializer {
    public static Item EXPLODER;

    @Override
    public void onInitialize() {
        CommonClass.init();

        EXPLODER = Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "exploder"),
            new ExploderItem(new Item.Properties().stacksTo(1)));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(EXPLODER));

        PayloadTypeRegistry.playC2S().register(ExplodePayload.TYPE, ExplodePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ExplodePayload.TYPE,
            (payload, context) -> ServerExplodeHandler.handle(payload, context.player()));

        ServerTickEvents.END_WORLD_TICK.register(ExplosionScheduler::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Engine.onServerStopping();
            ChunkForce.releaseAll();
            ExplosionScheduler.reset();
        });
    }
}
