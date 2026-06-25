package com.example.explosionlib.fabric.client;

import com.example.explosionlib.client.ExplodeRequests;
import com.example.explosionlib.fabric.ExplosionLibFabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;

public class ExplosionLibFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount == 0) return false;
            if (!player.getMainHandItem().is(ExplosionLibFabric.EXPLODER)) return false;
            ExplodeRequests.onLeftClick(player);
            return true; // cancel the vanilla attack (and thus the block-attack path below)
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!world.isClientSide) return InteractionResult.PASS;
            if (!player.getMainHandItem().is(ExplosionLibFabric.EXPLODER)) return InteractionResult.PASS;
            ExplodeRequests.onLeftClick((LocalPlayer) player);
            return InteractionResult.SUCCESS; // cancels mining
        });
    }
}
