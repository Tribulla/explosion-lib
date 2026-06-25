package com.example.explosionlib.fabric;

import com.example.explosionlib.platform.NetworkPlatform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FabricNetworkPlatform implements NetworkPlatform {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
