package com.example.explosionlib.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface NetworkPlatform {
    NetworkPlatform INSTANCE = Services.load(NetworkPlatform.class);

    void sendToServer(CustomPacketPayload payload);
}
