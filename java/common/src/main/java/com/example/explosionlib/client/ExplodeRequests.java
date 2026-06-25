package com.example.explosionlib.client;

import com.example.explosionlib.network.ExplodePayload;
import com.example.explosionlib.platform.NetworkPlatform;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ExplodeRequests {
    private ExplodeRequests() {}

    public static void atLookedAtBlock(LocalPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange() + 0.5, 1.0f, false);
        BlockPos pos = (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK)
            ? bhr.getBlockPos() : player.blockPosition();
        NetworkPlatform.INSTANCE.sendToServer(new ExplodePayload(0, pos, ClientConfig.yield, ClientConfig.flags()));
    }

    public static void atFeet(LocalPlayer player) {
        NetworkPlatform.INSTANCE.sendToServer(new ExplodePayload(1, player.blockPosition(), ClientConfig.yield, ClientConfig.flags()));
    }

    public static void onLeftClick(LocalPlayer player) {
        if (player.isShiftKeyDown()) atFeet(player);
        else atLookedAtBlock(player);
    }
}
