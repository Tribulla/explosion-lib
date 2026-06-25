package com.example.explosionlib.client;

public final class ClientConfig {
    public static float yield = 8.0f;        // 0.5 .. 1024
    public static boolean shockwave = true;
    public static boolean gravity = true;
    public static boolean debris = true;
    public static boolean scorch = true;
    public static boolean entityDamage = true;

    private ClientConfig() {}

    public static int flags() {
        return (shockwave ? 1 : 0) | (gravity ? 2 : 0) | (debris ? 4 : 0)
            | (scorch ? 8 : 0) | (entityDamage ? 16 : 0);
    }
}
