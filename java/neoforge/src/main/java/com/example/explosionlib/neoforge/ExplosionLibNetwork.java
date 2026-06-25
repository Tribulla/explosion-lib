package com.example.explosionlib.neoforge;

import com.example.explosionlib.Constants;
import com.example.explosionlib.network.ExplodePayload;
import com.example.explosionlib.network.ServerExplodeHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ExplosionLibNetwork {
    private ExplosionLibNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ExplodePayload.TYPE, ExplodePayload.CODEC, ExplosionLibNetwork::handle);
    }

    private static void handle(ExplodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ServerExplodeHandler.handle(payload, (ServerPlayer) context.player()));
    }
}
