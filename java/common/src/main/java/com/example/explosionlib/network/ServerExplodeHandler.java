package com.example.explosionlib.network;

import com.example.explosionlib.engine.Engine;
import com.example.explosionlib.engine.ExplosionConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ServerExplodeHandler {
    private ServerExplodeHandler() {}

    public static void handle(ExplodePayload p, ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();

        BlockPos origin;
        if (p.mode() == 1) {
            origin = player.blockPosition();
        } else {
            Vec3 eye = player.getEyePosition(1.0f);
            double reach = player.blockInteractionRange() + 1.0;
            Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
            BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            origin = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : player.blockPosition();
        }

        ExplosionConfig cfg = new ExplosionConfig();
        cfg.yield = Mth.clamp(p.yield(), 0.5f, 1024.0f);   // validate untrusted input
        cfg.seed = level.getRandom().nextLong();           // fresh each blast -> always a new crater
        int f = p.flags();
        cfg.shockwave = (f & 1) != 0;
        cfg.gravity = (f & 2) != 0;
        cfg.debris = (f & 4) != 0;
        cfg.scorch = (f & 8) != 0;
        cfg.entityDamage = (f & 16) != 0;

        Engine.explode(level, origin, cfg, player);
    }
}
