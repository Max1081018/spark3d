package dev.spark3d.game;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import dev.spark3d.R;
import dev.spark3d.engine.Clock;
import dev.spark3d.engine.Mat4;
import dev.spark3d.engine.Touch;
import dev.spark3d.gfx.Camera;
import dev.spark3d.gfx.Font;
import dev.spark3d.gfx.Gl;
import dev.spark3d.gfx.Gltf;
import dev.spark3d.gfx.GltfLoader;
import dev.spark3d.gfx.GltfMesh;
import dev.spark3d.gfx.InstanceBatch;
import dev.spark3d.gfx.Mesh;
import dev.spark3d.gfx.ModelBatch;
import dev.spark3d.gfx.Shader;
import dev.spark3d.gfx.Skeletal;
import dev.spark3d.gfx.Sky;
import dev.spark3d.gfx.Terrain;
import dev.spark3d.gfx.UiBatch;

/**
 * The renderer, and the only class that knows the frame's draw order.
 *
 * <p>Seven draw calls per frame:
 * <ol>
 *   <li>sky — one full-screen triangle, no buffers</li>
 *   <li>terrain — one static mesh</li>
 *   <li>crystals — up to 12,000 instances, one call</li>
 *   <li>spires — 700 instances, one call</li>
 *   <li>shards — 240 instances, one call</li>
 *   <li>player marker — one instance</li>
 *   <li>HUD — one batch containing every glyph and quad</li>
 * </ol>
 *
 * <p>Two things make this hold 60 fps on a phone that is not a flagship:
 * the static instance buffers are uploaded once and animated in the vertex
 * shader, and the adaptive scaler changes only the instance count passed to
 * {@code glDrawElementsInstanced} — never a buffer upload. Dropping from twelve
 * thousand crystals to four thousand costs one integer.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {

    private static final long SEED = 0x5EEDL;

    private final Context context;
    private final Touch touch;
    private final Hud hud = new Hud();
    private final Clock clock = new Clock();

    private Shader sceneShader;
    private Shader terrainShader;
    private Shader uiShader;
    private Font font;
    private UiBatch ui;

    private Terrain terrain;
    private World world;
    private Camera camera;
    private Sky sky;

    private InstanceBatch crystals;
    private InstanceBatch spires;
    private InstanceBatch shards;
    private InstanceBatch player;

    private Shader modelShader;
    private Shader skinShader;
    private Gltf foxGltf;
    private Gltf robotGltf;
    private Gltf cesiumGltf;
    private Gltf riggedGltf;
    private Gltf duckGltf;
    private final java.util.List<ModelBatch> duckBatches = new java.util.ArrayList<>();
    private Characters characters;

    /** Real animated characters currently in the world. */
    private int characterCount;

    private int width = 1;
    private int height = 1;
    private float aspect = 1f;

    /** Instances of the crystal batch actually drawn. The adaptive knob. */
    private int crystalDraw = Tuning.CRYSTAL_START_DRAW;
    private float qualityCooldown;

    private int drawCalls;
    private int triangles;
    private int modelInstancesDrawn;

    private final float[] sunDir = new float[3];

    public GameRenderer(Context context, Touch touch) {
        this.context = context;
        this.touch = touch;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        clock.reset();

        GLES30.glClearColor(Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glFrontFace(GLES30.GL_CCW);
        GLES30.glDisable(GLES30.GL_BLEND);

        buildResources();
        Gl.check("surface created");
    }

    /**
     * Everything here is rebuilt from scratch on context loss. On a phone the
     * GL context can be destroyed while the activity survives (app backgrounded,
     * another app took the GPU), and reusing handles across that boundary gives
     * a black screen with no error.
     */
    private void buildResources() {
        sceneShader = new Shader("scene",
                Gl.readRaw(context, R.raw.scene_vert),
                Gl.readRaw(context, R.raw.scene_frag));
        terrainShader = new Shader("terrain",
                Gl.readRaw(context, R.raw.terrain_vert),
                Gl.readRaw(context, R.raw.terrain_frag));
        uiShader = new Shader("ui",
                Gl.readRaw(context, R.raw.ui_vert),
                Gl.readRaw(context, R.raw.ui_frag));
        modelShader = new Shader("model",
                Gl.readRaw(context, R.raw.model_vert),
                Gl.readRaw(context, R.raw.model_frag));
        skinShader = new Shader("skin",
                Gl.readRaw(context, R.raw.skin_vert),
                Gl.readRaw(context, R.raw.skin_frag));

        sky = new Sky(context);
        sky.topR = Tuning.SKY_TOP_R;
        sky.topG = Tuning.SKY_TOP_G;
        sky.topB = Tuning.SKY_TOP_B;
        sky.botR = Tuning.SKY_BOT_R;
        sky.botG = Tuning.SKY_BOT_G;
        sky.botB = Tuning.SKY_BOT_B;

        terrain = new Terrain(SEED, Tuning.WORLD_SIZE, Tuning.TERRAIN_RES);
        world = new World(SEED ^ 0x9e3779b9L, terrain);

        camera = new Camera();
        camera.distance = Tuning.CAM_DISTANCE;
        camera.pitch = Tuning.CAM_PITCH;
        camera.fovY = (float) Math.toRadians(Tuning.FOV_DEGREES);
        camera.near = 0.4f;
        camera.far = 520f;
        world.snapCamera(camera);

        font = new Font();
        ui = new UiBatch(720, uiShader);

        // --- static crystal field: filled once, uploaded once
        crystals = new InstanceBatch(Mesh.crystal(), Tuning.CRYSTAL_CAPACITY);
        for (int i = 0; i < world.crystalCount; i++) {
            crystals.addYaw(
                    world.crX[i], world.crY[i], world.crZ[i],
                    world.crYaw[i],
                    world.crScale[i], world.crScale[i] * (1f + world.crBob[i] * 0.35f),
                    world.crScale[i],
                    world.crR[i], world.crG[i], world.crB[i], world.crE[i],
                    world.crPhase[i], 0.55f + i % 7 * 0.11f, world.crBob[i], world.crSpin[i]);
        }
        crystals.flush();

        // --- spires: static too, but only a handful actually move
        spires = new InstanceBatch(Mesh.prism(6), Tuning.SPIRES);
        for (int i = 0; i < world.spireCount; i++) {
            spires.addYaw(
                    world.spX[i], world.spY[i], world.spZ[i], i * 0.7f,
                    world.spW[i], world.spH[i], world.spW[i],
                    world.spR[i], world.spG[i], world.spB[i], world.spE[i],
                    i * 0.31f, 0.20f, 0.02f, 0f);
        }
        spires.flush();

        // --- dynamic batches: refilled every frame, tiny
        shards = new InstanceBatch(Mesh.sphere(1), Tuning.SHARDS);
        player = new InstanceBatch(Mesh.crystal(), 2);

        crystalDraw = Tuning.CRYSTAL_START_DRAW;
        qualityCooldown = 0f;

        // --- real models: animated characters and the static duck flock
        foxGltf = GltfLoader.load(context, "models/Fox.glb");
        robotGltf = GltfLoader.load(context, "models/RobotExpressive.glb");
        cesiumGltf = GltfLoader.load(context, "models/CesiumMan.glb");
        riggedGltf = GltfLoader.load(context, "models/RiggedFigure.glb");
        duckGltf = GltfLoader.load(context, "models/Duck.glb");

        for (Gltf.Primitive prim : duckGltf.bakeStatic()) {
            int tex = -1;
            if (duckGltf.materials.get(prim.material) != null
                    && duckGltf.materials.get(prim.material).baseColorImage >= 0
                    && duckGltf.materials.get(prim.material).baseColorImage < duckGltf.images.size()) {
                tex = GltfMesh.texture(duckGltf.images.get(duckGltf.materials.get(prim.material).baseColorImage));
            }
            ModelBatch batch = new ModelBatch(
                    GltfMesh.staticMesh(duckGltf, prim, prim.material, tex),
                    Tuning.MODEL_CAPACITY);
            for (int i = 0; i < Tuning.DUCKS; i++) {
                float a = (float) (i * 2.399f);
                float r = 40f + (float) Math.cos(a) * 90f;
                float z = 40f + (float) Math.sin(a) * 90f;
                batch.add(
                        r, -1.5f, z,
                        0f, 0f, 0f, 1f,
                        0.5f, 0.5f, 0.5f,
                        Tuning.DUCK_R, Tuning.DUCK_G, Tuning.DUCK_B, 0f,
                        i * 0.31f, 0f, 0f, (float) (i % 5) * 0.25f);
            }
            batch.flush();
            duckBatches.add(batch);
        }

        characters = new Characters(foxGltf, 0, robotGltf, 0, cesiumGltf, 0, riggedGltf, 0);
        characterCount = characters.count();
        sunDir[0] = Tuning.SUN_X;
        sunDir[1] = Tuning.SUN_Y;
        sunDir[2] = Tuning.SUN_Z;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        aspect = Camera.aspect(width, height);
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        float dt = clock.tick();
        if (dt == 0f) dt = 1f / 60f;

        boolean looking = touch.looking;
        world.update(dt,
                touch.moveX, touch.moveY,
                touch.lookX, touch.lookY,
                looking,
                camera);

        // Look is applied inside World.update so its dt matches the movement.
        camera.update(aspect, dt);

        adaptQuality(dt);

        updateCharacters(dt);

        drawCalls = 0;
        triangles = 0;

        GLES30.glClearColor(Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        sky.draw(camera, aspect, sunDir[0], sunDir[1], sunDir[2]);
        drawCalls++;

        drawTerrain();
        drawScene();
        drawHud();
    }

    private void drawTerrain() {
        terrainShader.use();
        terrainShader.setMat4("uViewProj", camera.viewProj);
        terrainShader.set3f("uCamPos", camera.ex, camera.ey, camera.ez);
        terrainShader.set3f("uSunDir", sunDir[0], sunDir[1], sunDir[2]);
        terrainShader.set3f("uFogColor", Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B);
        terrainShader.set1f("uFogStart", Tuning.FOG_START);
        terrainShader.set1f("uFogEnd", Tuning.FOG_END);
        terrainShader.set1f("uGridScale", 0.55f);
        terrainShader.set3f("uAccent", Tuning.ACCENT_R, Tuning.ACCENT_G, Tuning.ACCENT_B);
        terrain.draw(terrainShader);
        drawCalls++;
        triangles += terrain.triangleCount();
    }

    private void drawScene() {
        sceneShader.use();
        sceneShader.setMat4("uViewProj", camera.viewProj);
        sceneShader.set3f("uCamPos", camera.ex, camera.ey, camera.ez);
        sceneShader.set3f("uFogColor", Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B);
        sceneShader.set1f("uFogStart", Tuning.FOG_START);
        sceneShader.set1f("uFogEnd", Tuning.FOG_END);
        sceneShader.set1f("uTime", clock.time);

        // 1. The field. One call, adaptive count.
        crystals.draw(crystalDraw);
        drawCalls++;
        triangles += crystals.indexCount() / 3 * crystalDraw;

        // 2. Rocks.
        spires.draw();
        drawCalls++;
        triangles += spires.indexCount() / 3 * world.spireCount;

        // 3. Collectibles: 240 instances rebuilt on the CPU each frame.
        shards.clear();
        for (int i = 0; i < world.shardCount; i++) {
            if (!world.shAlive[i]) continue;
            shards.addYaw(
                    world.shX[i], world.shY[i], world.shZ[i],
                    world.shPhase[i] * 1.3f,
                    0.55f, 0.55f, 0.55f,
                    Tuning.SHARD_R, Tuning.SHARD_G, Tuning.SHARD_B, 0.85f,
                    world.shPhase[i], 1.6f, 0.30f, 1.9f);
        }
        shards.flush();
        shards.draw();
        drawCalls++;
        triangles += shards.indexCount() / 3 * shards.count;

        // 4. The player: a bright marker rather than a character model. At this
        // camera distance a silhouette would be eight pixels tall.
        player.clear();
        float groundY = terrain.heightAt(world.px, world.pz);
        player.addYaw(
                world.px, groundY + 1.05f, world.pz,
                clock.time * 1.4f,
                0.80f, 1.00f, 0.80f,
                Tuning.PLAYER_R, Tuning.PLAYER_G, Tuning.PLAYER_B, 0.70f,
                0f, 1.1f, 0.10f, 1.2f);
        player.flush();
        player.draw();
        drawCalls++;
        triangles += player.indexCount() / 3 * player.count;

        // 5. Real models: the duck flock, then the animated characters.
        drawModels();
    }

    /**
     * The real-model content: a static flock of baked ducks in one instanced
     * batch, plus a handful of skinned characters that play real clips.
     */
    private void drawModels() {
        float[] base = {1f, 1f, 1f, 1f};
        modelInstancesDrawn = 0;
        for (ModelBatch b : duckBatches) {
            if (b.count <= 0) continue;
            modelInstancesDrawn += b.count;
            b.draw(modelShader, camera.viewProj, camera.ex, camera.ey, camera.ez,
                    sunDir[0], sunDir[1], sunDir[2],
                    Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B,
                    Tuning.FOG_START, Tuning.FOG_END, clock.time,
                    base[0], base[1], base[2], base[3]);
            triangles += b.indexCount() / 3 * b.count;
        }

        for (Characters.Character c : characters.list) {
            float gy = terrain.heightAt(c.x, c.z) + 0.85f;
            float[] mm = Mat4.create();
            Mat4.fromTRS(mm, c.x, gy, c.z, 0f, 0f, 0f, 1f, 1f, 1f, 1f);
            float[] q = new float[4];
            Mat4.quatAxisAngle(q, 0f, 1f, 0f, c.yaw);
            Mat4.fromTRS(mm, c.x, gy, c.z, q[0], q[1], q[2], q[3], 1f, 1f, 1f);
            c.skeletal.draw(skinShader, camera, mm, clock.time,
                    sunDir[0], sunDir[1], sunDir[2],
                    Tuning.SKY_BOT_R, Tuning.SKY_BOT_G, Tuning.SKY_BOT_B,
                    Tuning.FOG_START, Tuning.FOG_END,
                    0.85f, 0.85f, 0.85f, 1f);
        }
    }

    private void updateCharacters(float dt) {
        characters.update(dt);
    }

    private void drawHud() {
        hud.setViewport(width, height, Touch.density());
        hud.draw(ui, font, clock, world, camera, touch, crystalDraw, modelInstancesDrawn,
                characters.count(), drawCalls, triangles);
        drawCalls++;
    }

    /**
     * Adaptive quality with hysteresis and a cooldown.
     *
     * <p>Without the cooldown this oscillates: drop instances, frame gets cheap,
     * restore them, frame gets expensive, drop again — a visible pulse. The
     * cooldown means a change is judged for half a second before the next one.
     */
    private void adaptQuality(float dt) {
        if (qualityCooldown > 0f) {
            qualityCooldown -= dt;
            return;
        }
        float ms = clock.smoothDelta * 1000f;

        if (ms > Tuning.FRAME_BUDGET_HI && crystalDraw > Tuning.CRYSTAL_MIN_DRAW) {
            crystalDraw = Math.max(Tuning.CRYSTAL_MIN_DRAW, crystalDraw - 800);
            qualityCooldown = 0.7f;
        } else if (ms < Tuning.FRAME_BUDGET_LO && crystalDraw < Tuning.CRYSTAL_CAPACITY) {
            crystalDraw = Math.min(Tuning.CRYSTAL_CAPACITY, crystalDraw + 400);
            qualityCooldown = 1.1f;
        }
    }

    /** Called from the view when the surface goes away; safe to call twice. */
    public void dispose() {
        if (crystals != null) crystals.dispose();
        if (spires != null) spires.dispose();
        if (shards != null) shards.dispose();
        if (player != null) player.dispose();
        if (terrain != null) terrain.dispose();
        if (sky != null) sky.dispose();
        if (font != null) font.dispose();
        if (ui != null) ui.dispose();
        if (sceneShader != null) sceneShader.dispose();
        if (terrainShader != null) terrainShader.dispose();
        if (uiShader != null) uiShader.dispose();
        if (modelShader != null) modelShader.dispose();
        if (skinShader != null) skinShader.dispose();
        for (ModelBatch b : duckBatches) b.dispose();
        duckBatches.clear();
        foxGltf = null;
        robotGltf = null;
        cesiumGltf = null;
        riggedGltf = null;
        duckGltf = null;
        characters = null;
        crystals = null;
        spires = null;
        shards = null;
        player = null;
        terrain = null;
        sky = null;
        font = null;
        ui = null;
        sceneShader = null;
        terrainShader = null;
        uiShader = null;
    }

    public Clock clock() {
        return clock;
    }

    public World world() {
        return world;
    }
}
