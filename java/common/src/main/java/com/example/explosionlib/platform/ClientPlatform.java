package com.example.explosionlib.platform;

public interface ClientPlatform {
    ClientPlatform INSTANCE = Services.load(ClientPlatform.class);

    void openExploderConfigScreen();
}
