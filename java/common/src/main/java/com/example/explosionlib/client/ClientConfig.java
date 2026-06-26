package com.example.explosionlib.client;

public final class ClientConfig {
    public static float yield = 8.0f;
    public static boolean shockwave = true;
    public static boolean scorch = true;
    public static boolean entityDamage = true;

    private ClientConfig() {}

    public static int flags() {   // bits 2 (gravity) and 4 (debris) retired; left unused for protocol stability
        return (shockwave ? 1 : 0) | (scorch ? 8 : 0) | (entityDamage ? 16 : 0);
    }
}
