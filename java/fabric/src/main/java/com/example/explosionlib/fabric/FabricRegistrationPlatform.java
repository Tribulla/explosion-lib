package com.example.explosionlib.fabric;

import com.example.explosionlib.platform.RegistrationPlatform;

import net.minecraft.world.item.Item;

public class FabricRegistrationPlatform implements RegistrationPlatform {
    @Override
    public Item exploderItem() {
        return ExplosionLibFabric.EXPLODER;
    }
}
