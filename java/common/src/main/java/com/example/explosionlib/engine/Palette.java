package com.example.explosionlib.engine;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Palette {
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState RUBBLE = Blocks.GRAVEL.defaultBlockState();     // loose -> settles
    public static final BlockState SCORCHED = Blocks.BLACKSTONE.defaultBlockState();

    private Palette() {}
}
