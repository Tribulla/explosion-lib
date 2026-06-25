package com.example.explosionlib.neoforge.client;

import com.example.explosionlib.Constants;
import com.example.explosionlib.client.ExplodeRequests;
import com.example.explosionlib.neoforge.ExplosionLibNeoForge;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class NeoForgeClientInput {
    private NeoForgeClientInput() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return; // de-dupe holds
        if (!event.getItemStack().is(ExplosionLibNeoForge.EXPLODER.get())) return;
        ExplodeRequests.onLeftClick((LocalPlayer) event.getEntity());
        event.setCanceled(true); // cancels mining
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!event.getItemStack().is(ExplosionLibNeoForge.EXPLODER.get())) return;
        ExplodeRequests.onLeftClick((LocalPlayer) event.getEntity());
    }
}
