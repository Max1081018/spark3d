package dev.spark3d.game;

import java.util.Random;

import dev.spark3d.engine.SpatialHash3;
import dev.spark3d.engine.Touch;
import dev.spark3d.gfx.Camera;
import dev.spark3d.gfx.Terrain;

/**
 * The world: twelve thousand crystals, seven hundred spires, two hundred and
 * forty collectibles, and one player.
 *
 * <p>Storage is struct-of-arrays. There is no {@code Crystal} object and no
 * {@code ArrayList<Crystal>} anywhere, because twelve thousand objects would be
 * twelve thousand pointer chases per frame and a GC pause on every allocation.
 * The fields sit in {@code float[]} held for the life of the process.
 *
 * <p>Nothing here touches OpenGL. The renderer reads these arrays; the world has
 * no opinion about how it is drawn, which is what makes it steppable in a plain
 * unit test on the JVM.
 */
public final class World {

    public final Terrain terrain;
    public final int crystalCount;
    public final int spireCount;
    public final int shardCount;

    // --- crystals (SoA)
    public final float[] crX, crY, crZ;
    public final float[] crScale, crYaw, crPhase, crBob, crSpin;
    public final float[] crR, crG, crB, crE;

    // --- spires
    public final float[] spX, spY, spZ, spW, spH;
    public final float[] spR, spG, spB, spE;

    // --- shards
    public final float[] shX, shY, shZ, shPhase;
    public final boolean[] shAlive;

    // --- player
    public float px, pz, py;
    public float vx, vz;

    public int score;
    public int collected;
    public float time;

    /** Set for one frame when a shard is picked up; drives the HUD pulse. */
    public float pickupFlash;
    /** Distance to the nearest uncollected shard, for the HUD hint arrow. */
    public float nearestDistance = 999f;
    public float nearestX, nearestZ;

    private final SpatialHash3 spires;
    private final int[] query = new int[128];

    public World(long seed, Terrain terrain) {
        this.terrain = terrain;
        this.crystalCount = Tuning.CRYSTAL_CAPACITY;
        this.spireCount = Tuning.SPIRES;
        this.shardCount = Tuning.SHARDS;

        Random rng = new Random(seed);

        crX = new float[crystalCount];
        crY = new float[crystalCount];
        crZ = new float[crystalCount];
        crScale = new float[crystalCount];
        crYaw = new float[crystalCount];
        crPhase = new float[crystalCount];
        crBob = new float[crystalCount];
        crSpin = new float[crystalCount];
        crR = new float[crystalCount];
        crG = new float[crystalCount];
        crB = new float[crystalCount];
        crE = new float[crystalCount];

        for (int i = 0; i < crystalCount; i++) {
            float a = (float) (rng.nextDouble() * Math.PI * 2.0);
            // sqrt keeps the distribution uniform over the disc instead of
            // clumping everything in the middle.
            float r = Tuning.SPAWN_CLEAR
                    + (Tuning.FIELD_RADIUS - Tuning.SPAWN_CLEAR) * (float) Math.sqrt(rng.nextDouble());
            float x = (float) Math.cos(a) * r;
            float z = (float) Math.sin(a) * r;

            // A few outliers beyond the field give the horizon something to do.
            if (rng.nextFloat() < 0.06f) {
                float a2 = (float) (rng.nextDouble() * Math.PI * 2.0);
                float r2 = 300f + rng.nextFloat() * 35f;
                x = (float) Math.cos(a2) * r2;
                z = (float) Math.sin(a2) * r2;
            }

            crX[i] = x;
            crZ[i] = z;
            crY[i] = terrain.heightAt(x, z) - 0.15f;

            // Log-ish scale spread: mostly small, a few landmarks.
            float u = rng.nextFloat();
            crScale[i] = 0.40f + u * u * 2.9f;
            crYaw[i] = (float) (rng.nextDouble() * Math.PI * 2.0);
            crPhase[i] = (float) (rng.nextDouble() * Math.PI * 2.0);
            crBob[i] = 0.05f + rng.nextFloat() * 0.30f;
            crSpin[i] = rng.nextFloat() < 0.25f ? 0.10f + rng.nextFloat() * 0.5f : 0f;

            float[] c = Tuning.CRYSTAL_COLOURS[rng.nextInt(Tuning.CRYSTAL_COLOURS.length)];
            float tint = 0.82f + rng.nextFloat() * 0.30f;
            crR[i] = c[0] * tint;
            crG[i] = c[1] * tint;
            crB[i] = c[2] * tint;
            crE[i] = 0.06f + rng.nextFloat() * 0.44f;
        }

        spX = new float[spireCount];
        spY = new float[spireCount];
        spZ = new float[spireCount];
        spW = new float[spireCount];
        spH = new float[spireCount];
        spR = new float[spireCount];
        spG = new float[spireCount];
        spB = new float[spireCount];
        spE = new float[spireCount];

        spires = new SpatialHash3(14f, Tuning.WORLD_SIZE);

        for (int i = 0; i < spireCount; i++) {
            float a = (float) (rng.nextDouble() * Math.PI * 2.0);
            float r = 150f + (float) Math.sqrt(rng.nextDouble()) * 170f;
            float x = (float) Math.cos(a) * r;
            float z = (float) Math.sin(a) * r;
            float h = 5f + rng.nextFloat() * rng.nextFloat() * 26f;

            spX[i] = x;
            spZ[i] = z;
            spY[i] = terrain.heightAt(x, z) + h * 0.5f - 0.4f;
            spW[i] = 0.7f + rng.nextFloat() * 1.5f;
            spH[i] = h;

            boolean glow = rng.nextFloat() < 0.14f;
            if (glow) {
                spR[i] = 0.30f;
                spG[i] = 0.90f;
                spB[i] = 1.00f;
                spE[i] = 0.55f;
            } else {
                float v = 0.16f + rng.nextFloat() * 0.10f;
                spR[i] = v * 0.85f;
                spG[i] = v * 1.00f;
                spB[i] = v * 1.35f;
                spE[i] = 0.02f;
            }
            spires.insert(i, x, z);
        }

        shX = new float[shardCount];
        shY = new float[shardCount];
        shZ = new float[shardCount];
        shPhase = new float[shardCount];
        shAlive = new boolean[shardCount];

        px = 0f;
        pz = 0f;
        py = terrain.heightAt(0f, 0f);

        for (int i = 0; i < shardCount; i++) {
            placeShard(i, rng, px, pz);
        }
    }

    private void placeShard(int i, Random rng, float awayX, float awayZ) {
        float x = 0f;
        float z = 0f;
        for (int attempt = 0; attempt < 12; attempt++) {
            float a = (float) (rng.nextDouble() * Math.PI * 2.0);
            float r = 8f + (float) Math.sqrt(rng.nextDouble()) * (Tuning.FIELD_RADIUS - 20f);
            x = (float) Math.cos(a) * r;
            z = (float) Math.sin(a) * r;
            if ((x - awayX) * (x - awayX) + (z - awayZ) * (z - awayZ) > 90f * 90f) break;
        }
        shX[i] = x;
        shZ[i] = z;
        shY[i] = terrain.heightAt(x, z) + Tuning.SHARD_HEIGHT;
        shPhase[i] = (float) (rng.nextDouble() * Math.PI * 2.0);
        shAlive[i] = true;
    }

    public void snapCamera(Camera camera) {
        camera.groundY = terrain.heightAt(px, pz);
        camera.snap(px, py + 1.0f, pz);
    }

    /**
     * One frame of simulation.
     *
     * @param dt      clamped frame time in seconds
     * @param moveX   left stick x, -1..1
     * @param moveY   left stick y, -1..1 (screen space, down positive)
     * @param looking true while the right stick is held
     */
    public void update(float dt, float moveX, float moveY, float lookX, float lookY,
                       boolean looking, Camera camera) {
        time += dt;
        pickupFlash = Math.max(0f, pickupFlash - dt * 2.2f);

        // Movement is camera-relative: forward is always "into the screen".
        float fx = camera.rightX() * moveX + camera.forwardX() * (-moveY);
        float fz = camera.rightZ() * moveX + camera.forwardZ() * (-moveY);
        float len = (float) Math.sqrt(fx * fx + fz * fz);
        if (len > 1f) {
            fx /= len;
            fz /= len;
        }

        float k = 1f - (float) Math.exp(-Tuning.ACCEL_RATE * dt);
        vx += (fx * Tuning.PLAYER_SPEED - vx) * k;
        vz += (fz * Tuning.PLAYER_SPEED - vz) * k;

        px += vx * dt;
        pz += vz * dt;

        // Keep the player inside the populated area.
        float r = (float) Math.sqrt(px * px + pz * pz);
        float limit = Tuning.FIELD_RADIUS + 12f;
        if (r > limit) {
            float s = limit / r;
            px *= s;
            pz *= s;
            // Kill the outward component so the edge does not become a wall of
            // jitter.
            float nx = px / limit;
            float nz = pz / limit;
            float outward = vx * nx + vz * nz;
            if (outward > 0f) {
                vx -= nx * outward;
                vz -= nz * outward;
            }
        }

        collideSpires();

        py = terrain.heightAt(px, pz);

        // Shards: 240 of them, so a linear scan is genuinely the right choice —
        // it is a contiguous read of two arrays and costs less than the hash
        // lookups would.
        nearestDistance = 999f;
        float best = nearestDistance;
        for (int i = 0; i < shardCount; i++) {
            if (!shAlive[i]) continue;
            float dx = shX[i] - px;
            float dz = shZ[i] - pz;
            float d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                nearestDistance = (float) Math.sqrt(d2);
                nearestX = shX[i];
                nearestZ = shZ[i];
            }
            if (d2 < Tuning.COLLECT_RADIUS * Tuning.COLLECT_RADIUS) {
                collected++;
                score += 10;
                pickupFlash = 1f;
                camera.addShake(0.12f);
                // Respawn immediately, at least 90 units away. Deferring it to a
                // later frame would leave a hole in the field that the player can
                // walk back into; moving it now is both simpler and fairer.
                placeShard(i, respawnRandom, px, pz);
            }
        }

        if (looking) {
            camera.look(lookX, lookY, dt);
        }
        camera.groundY = terrain.heightAt(px, pz);
        camera.follow(px, py + 1.0f, pz, dt);
    }

    private final Random respawnRandom = new Random(0x5eed);

    /** Push the player out of any spire they walk into. */
    private void collideSpires() {
        int n = spires.query(px, pz, query);
        for (int i = 0; i < n; i++) {
            int idx = query[i];
            float dx = px - spX[idx];
            float dz = pz - spZ[idx];
            float rr = Tuning.PLAYER_RADIUS + spW[idx];
            float d2 = dx * dx + dz * dz;
            if (d2 >= rr * rr || d2 < 1e-6f) continue;
            float d = (float) Math.sqrt(d2);
            float push = (rr - d) / d;
            px += dx * push;
            pz += dz * push;
            // Remove the inward velocity so the player slides along instead of
            // sticking to the rock.
            float nx = dx / d;
            float nz = dz / d;
            float inward = vx * nx + vz * nz;
            if (inward < 0f) {
                vx -= nx * inward;
                vz -= nz * inward;
            }
        }
    }

    /** Deterministic respawn meant for tests and replays. */
    public void setSeedForRespawn(long seed) {
        respawnRandom.setSeed(seed);
    }

    /** World-space radius that the camera may not leave. */
    public float fieldRadius() {
        return Tuning.FIELD_RADIUS;
    }
}
