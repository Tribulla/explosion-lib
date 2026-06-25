package com.example.explosionlib.neoforge;

import com.example.explosionlib.Constants;
import com.example.explosionlib.engine.ChunkForce;
import com.example.explosionlib.engine.ExplosionScheduler;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class NeoForgeServerEvents {
    private NeoForgeServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ExplosionScheduler.tick(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ChunkForce.releaseAll();
    }
}
