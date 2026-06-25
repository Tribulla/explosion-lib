package com.example.explosionlib.neoforge.client;

import com.example.explosionlib.client.ExploderConfigScreen;
import com.example.explosionlib.platform.ClientPlatform;

import net.minecraft.client.Minecraft;

public class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public void openExploderConfigScreen() {
        Minecraft.getInstance().setScreen(new ExploderConfigScreen(null));
    }
}
