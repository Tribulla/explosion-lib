package com.example.explosionlib.engine;

public final class Material {
    public static final int AIR = 0;
    public static final int LOOSE = 1;        // sand/gravel-like: granular, flows
    public static final int STRUCT = 2;       // rigid solid: stone/wood/metal/...
    public static final int BRITTLE = 3;      // glass/leaves: shatters under the shockwave
    public static final int UNBREAKABLE = 4;  // bedrock-tier: never destroyed, anchors collapse
    public static final int RUBBLE = 5;       // result tag: cracked struct -> behaves loose
    public static final int SCORCHED = 6;     // result tag: charred struct -> behaves struct
    public static final int FLUID = 7;        // water/lava: blasts clear it, but it isn't cracked/scorched/flung

    private Material() {}

    public static boolean isSolid(int id)       { return id != AIR; }
    public static boolean isLoose(int id)        { return id == LOOSE || id == RUBBLE; }
    public static boolean isBrittle(int id)      { return id == BRITTLE; }
    public static boolean isUnbreakable(int id)  { return id == UNBREAKABLE; }
    public static boolean isFluid(int id)        { return id == FLUID; }
    public static boolean isStruct(int id)       { return isSolid(id) && !isLoose(id) && id != FLUID; }
    public static boolean isDestructible(int id) { return isSolid(id) && id != UNBREAKABLE; }
}
