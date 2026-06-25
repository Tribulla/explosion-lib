package com.example.explosionlib.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkForce {
    private ChunkForce() {}

    private static final Map<ServerLevel, Map<Long, Integer>> COUNTS = new HashMap<>();

    public static List<long[]> force(ServerLevel level, BlockPos origin, int half) {
        Map<Long, Integer> counts = COUNTS.computeIfAbsent(level, k -> new HashMap<>());
        int x0 = origin.getX() - half, x1 = origin.getX() + half;
        int z0 = origin.getZ() - half, z1 = origin.getZ() + half;
        List<long[]> held = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= (x1 >> 4); cx++)
            for (int cz = z0 >> 4; cz <= (z1 >> 4); cz++) {
                long key = ChunkPos.asLong(cx, cz);
                int c = counts.getOrDefault(key, 0);
                if (c == 0) level.setChunkForced(cx, cz, true);
                counts.put(key, c + 1);
                held.add(new long[]{cx, cz});
            }
        return held;
    }

    public static void release(ServerLevel level, List<long[]> held) {
        Map<Long, Integer> counts = COUNTS.get(level);
        if (counts == null) return;
        for (long[] c : held) {
            long key = ChunkPos.asLong((int) c[0], (int) c[1]);
            int n = counts.getOrDefault(key, 0) - 1;
            if (n <= 0) {
                counts.remove(key);
                level.setChunkForced((int) c[0], (int) c[1], false);
            } else {
                counts.put(key, n);
            }
        }
        if (counts.isEmpty()) COUNTS.remove(level);
    }

    public static void releaseAll() {
        for (Map.Entry<ServerLevel, Map<Long, Integer>> e : COUNTS.entrySet()) {
            ServerLevel level = e.getKey();
            for (long key : e.getValue().keySet()) {
                level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            }
        }
        COUNTS.clear();
    }
}
