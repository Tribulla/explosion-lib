package com.example.explosionlib.neoforge;

import com.example.explosionlib.platform.RegistrationPlatform;

import net.minecraft.world.item.Item;

public class NeoForgeRegistrationPlatform implements RegistrationPlatform {
    @Override
    public Item exploderItem() {
        return ExplosionLibNeoForge.EXPLODER.get();
    }
}
