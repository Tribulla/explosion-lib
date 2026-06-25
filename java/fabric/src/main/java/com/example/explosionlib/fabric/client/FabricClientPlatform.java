package com.example.explosionlib.fabric.client;

import com.example.explosionlib.client.ExploderConfigScreen;
import com.example.explosionlib.platform.ClientPlatform;

import net.minecraft.client.Minecraft;

public class FabricClientPlatform implements ClientPlatform {
    @Override
    public void openExploderConfigScreen() {
        Minecraft.getInstance().setScreen(new ExploderConfigScreen(null));
    }
}
