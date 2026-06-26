package com.example.explosionlib.config;

import com.example.explosionlib.Constants;
import com.example.explosionlib.engine.ExplosionConfig;
import com.example.explosionlib.engine.Palette;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ServerConfig {
    private ServerConfig() {}

    public static final String FILE_NAME = "explosionlib.properties";

    private static String rubbleBlockId = "minecraft:gravel";
    private static String scorchBlockId = "minecraft:blackstone";

    public static void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        try {
            Map<String, String> values = Files.exists(file) ? parse(file) : Map.of();
            apply(values);
            Files.createDirectories(configDir);
            Files.writeString(file, render());
            Constants.LOG.info("[{}] config loaded from {}", Constants.MOD_ID, file);
        } catch (Exception e) {
            Constants.LOG.error("[{}] failed to read config {}; using defaults", Constants.MOD_ID, file, e);
        }
    }

    private static Map<String, String> parse(Path file) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return map;
    }

    private static void apply(Map<String, String> v) {
        ExplosionConfig.K_CRATER = clampDouble(v, "craterScale", ExplosionConfig.K_CRATER, 0.1, 256.0);
        ExplosionConfig.SHOCKWAVE_RADIUS = clampDouble(v, "shockwaveRadius", ExplosionConfig.SHOCKWAVE_RADIUS, 0.0, 64.0);
        ExplosionConfig.SHOCKWAVE_CRACK_RATE = clampDouble(v, "shockwaveCrackRate", ExplosionConfig.SHOCKWAVE_CRACK_RATE, 0.0, 1.0);
        ExplosionConfig.SCORCH_PROB = clampDouble(v, "scorchChance", ExplosionConfig.SCORCH_PROB, 0.0, 1.0);
        ExplosionConfig.ENTITY_RADIUS = clampDouble(v, "entityEffectRadius", ExplosionConfig.ENTITY_RADIUS, 0.0, 64.0);
        ExplosionConfig.ENTITY_KNOCKBACK = clampDouble(v, "entityKnockback", ExplosionConfig.ENTITY_KNOCKBACK, 0.0, 256.0);
        ExplosionConfig.EXPANSION_SPEED = clampInt(v, "expansionSpeedRingsPerTick", ExplosionConfig.EXPANSION_SPEED, 1, 4096);

        ExplosionConfig.ALLOW_SHOCKWAVE = bool(v, "allowShockwave", ExplosionConfig.ALLOW_SHOCKWAVE);
        ExplosionConfig.ALLOW_SCORCH = bool(v, "allowScorch", ExplosionConfig.ALLOW_SCORCH);
        ExplosionConfig.ALLOW_ENTITY_DAMAGE = bool(v, "allowEntityDamage", ExplosionConfig.ALLOW_ENTITY_DAMAGE);

        rubbleBlockId = blockId(v, "rubbleBlock", "minecraft:gravel");
        scorchBlockId = blockId(v, "scorchBlock", "minecraft:blackstone");
    }

    public static void resolveBlocks() {
        Palette.RUBBLE = resolve(rubbleBlockId, Palette.RUBBLE);
        Palette.SCORCHED = resolve(scorchBlockId, Palette.SCORCHED);
    }

    private static BlockState resolve(String id, BlockState fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Optional<Block> found = BuiltInRegistries.BLOCK.getOptional(rl);
            if (found.isPresent()) return found.get().defaultBlockState();
        }
        Constants.LOG.warn("[{}] config block '{}' not found; keeping {}",
            Constants.MOD_ID, id, BuiltInRegistries.BLOCK.getKey(fallback.getBlock()));
        return fallback;
    }

    private static int clampInt(Map<String, String> v, String key, int def, int lo, int hi) {
        String s = v.get(key);
        if (s == null) return def;
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            Constants.LOG.warn("[{}] config '{}': '{}' is not an integer; using {}", Constants.MOD_ID, key, s, def);
            return def;
        }
    }

    private static double clampDouble(Map<String, String> v, String key, double def, double lo, double hi) {
        String s = v.get(key);
        if (s == null) return def;
        try {
            return Math.max(lo, Math.min(hi, Double.parseDouble(s.trim())));
        } catch (NumberFormatException e) {
            Constants.LOG.warn("[{}] config '{}': '{}' is not a number; using {}", Constants.MOD_ID, key, s, def);
            return def;
        }
    }

    private static boolean bool(Map<String, String> v, String key, boolean def) {
        String s = v.get(key);
        if (s == null) return def;
        s = s.trim();
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        Constants.LOG.warn("[{}] config '{}': '{}' is not true/false; using {}", Constants.MOD_ID, key, s, def);
        return def;
    }

    private static String blockId(Map<String, String> v, String key, String def) {
        String id = v.getOrDefault(key, def).trim();
        if (ResourceLocation.tryParse(id) == null) {
            Constants.LOG.warn("[{}] config '{}': '{}' is not a valid block id; using {}",
                Constants.MOD_ID, key, id, def);
            return def;
        }
        return id;
    }

    private static String render() {
        StringBuilder b = new StringBuilder(2560);
        b.append("# ExplosionLib configuration. Edit a value and restart to apply.\n");

        b.append("craterScale=").append(ExplosionConfig.K_CRATER).append('\n');
        b.append("shockwaveRadius=").append(ExplosionConfig.SHOCKWAVE_RADIUS).append('\n');
        b.append("shockwaveCrackRate=").append(ExplosionConfig.SHOCKWAVE_CRACK_RATE).append('\n');
        b.append("scorchChance=").append(ExplosionConfig.SCORCH_PROB).append('\n');
        b.append("entityEffectRadius=").append(ExplosionConfig.ENTITY_RADIUS).append('\n');
        b.append("entityKnockback=").append(ExplosionConfig.ENTITY_KNOCKBACK).append('\n');
        b.append("expansionSpeedRingsPerTick=").append(ExplosionConfig.EXPANSION_SPEED).append("\n\n");

        b.append("rubbleBlock=").append(rubbleBlockId).append('\n');
        b.append("scorchBlock=").append(scorchBlockId).append("\n\n");

        b.append("allowShockwave=").append(ExplosionConfig.ALLOW_SHOCKWAVE).append('\n');
        b.append("allowScorch=").append(ExplosionConfig.ALLOW_SCORCH).append('\n');
        b.append("allowEntityDamage=").append(ExplosionConfig.ALLOW_ENTITY_DAMAGE).append('\n');
        return b.toString();
    }
}
