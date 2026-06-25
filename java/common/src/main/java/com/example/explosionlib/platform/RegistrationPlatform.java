package com.example.explosionlib.platform;

import net.minecraft.world.item.Item;

public interface RegistrationPlatform {
    RegistrationPlatform INSTANCE = Services.load(RegistrationPlatform.class);

    Item exploderItem();
}
