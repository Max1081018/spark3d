package dev.spark3d.game;

/**
 * Every tunable number in one place. Nothing below this file should contain a
 * bare magic constant.
 */
public final class Tuning {

    private Tuning() {
    }

    public static final float WORLD_SIZE = 680f;
    public static final int TERRAIN_RES = 112;

    /** Instances allocated. The adaptive scaler draws between MIN and MAX. */
    public static final int CRYSTAL_CAPACITY = 12000;
    public static final int CRYSTAL_MIN_DRAW = 4000;
    public static final int CRYSTAL_START_DRAW = 9000;
    public static final int SPIRES = 700;
    public static final int SHARDS = 240;

    public static final float FIELD_RADIUS = 300f;
    public static final float SPAWN_CLEAR = 12f;

    public static final float PLAYER_RADIUS = 0.8f;
    public static final float PLAYER_SPEED = 18.0f;
    public static final float ACCEL_RATE = 10.0f;
    public static final float COLLECT_RADIUS = 3.4f;
    public static final float SHARD_HEIGHT = 1.5f;

    /** Camera. */
    public static final float CAM_DISTANCE = 12.0f;
    public static final float CAM_PITCH = 0.40f;
    public static final float FOV_DEGREES = 58f;

    /** Fog starts and ends, in world units. Must match the sky bottom colour feel. */
    public static final float FOG_START = 90f;
    public static final float FOG_END = 330f;

    /** Adaptive quality targets, in milliseconds per frame. */
    public static final float FRAME_BUDGET_HI = 16.9f;  // above this, drop instances
    public static final float FRAME_BUDGET_LO = 13.2f;  // below this, restore them

    // --- palette (linear-ish 0..1, phone screens are bright so keep it punchy)
    public static final float[][] CRYSTAL_COLOURS = {
            {0.28f, 0.86f, 1.00f},
            {0.24f, 0.98f, 0.76f},
            {0.62f, 0.56f, 1.00f},
            {0.32f, 0.62f, 0.95f},
            {1.00f, 0.74f, 0.40f},
    };

    public static final float SKY_TOP_R = 0.030f, SKY_TOP_G = 0.045f, SKY_TOP_B = 0.085f;
    public static final float SKY_BOT_R = 0.150f, SKY_BOT_G = 0.215f, SKY_BOT_B = 0.300f;

    public static final float SUN_X = 0.42f, SUN_Y = 0.55f, SUN_Z = 0.72f;

    public static final float PLAYER_R = 0.32f, PLAYER_G = 0.92f, PLAYER_B = 1.00f;
    public static final float SHARD_R = 1.00f, SHARD_G = 0.86f, SHARD_B = 0.35f;

    /** HUD. */
    public static final float ACCENT_R = 0.28f, ACCENT_G = 0.88f, ACCENT_B = 1.00f;
    public static final float INK_R = 0.90f, INK_G = 0.94f, INK_B = 1.00f;
    public static final float DIM_R = 0.45f, DIM_G = 0.53f, DIM_B = 0.66f;
}
