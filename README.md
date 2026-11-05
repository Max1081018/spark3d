# Spark 3D

A native Android 3D engine and game. OpenGL ES 3.0, Java, one `GLSurfaceView`.
No browser, no WebView, no AndroidX, no game framework — the APK contains the
engine.

The point of the project is one claim: **twelve thousand independently scaled,
animated models at 60 fps on a phone**, drawn in a single call.

## What is on screen

A dark crystal field on a procedurally generated plain. You are the cyan shard in
the middle; the amber shards scattered around are collectibles (+10 each, they
respawn at least 90 units away). Rock spires block movement. The camera is a
third-person orbit that follows with exponential smoothing.

## Controls

| Input | Action |
| --- | --- |
| Left thumb (anywhere on the left half) | Move, camera-relative |
| Right thumb (anywhere on the right half) | Orbit the camera |
| Quick tap | Nothing yet — reserved |

The sticks float to wherever your thumb lands; there is no fixed on-screen pad to
cover the scene.

## How it is fast

1. **GPU instancing.** One mesh, one `glDrawElementsInstanced` call per archetype.
   Twelve thousand crystals = one call. Per-instance data is 18 floats: offset,
   quaternion, non-uniform scale, colour + emissive, and animation parameters.
2. **Zero CPU work for idle animation.** Bob and spin happen in the vertex shader
   from `uTime`. The instance buffer is uploaded once at load and never again.
3. **No per-instance branching on the CPU.** The world is struct-of-arrays in
   `float[]`; there is no `Crystal` object, no list, no iterator, no per-frame
   allocation in the render path.
4. **Adaptive quality.** If the smoothed frame time exceeds 16.9 ms the renderer
   lowers the *instance count* passed to the draw call (never re-uploading the
   buffer), with a cooldown so it does not oscillate. Floor is 4,000 instances.
5. **Seven draw calls per frame** including the HUD, which is one batched glyph
   atlas with a reserved white cell so text and solid quads share a texture.
6. **Culling, baked shade, derivative-based terrain grid, no post-processing.**

Every number is in `game/Tuning.java`.

## Layout

```
app/src/main/java/dev/spark3d/
  engine/   Mat4, Clock, Noise, Touch, SpatialHash3, SafeArea  (no Android imports)
  gfx/      Gl, Shader, Mesh, InstanceBatch, Camera, Font, UiBatch, Terrain, Sky
  game/     Tuning, World, Hud, GameRenderer
  GameView.java, MainActivity.java
app/src/main/res/raw/*.glsl      the eight shaders, loaded at runtime
verify/EngineCheck.java          JVM checks for the platform-independent maths
```

## Build

The APK is built by GitHub Actions (`.github/workflows/build-apk.yml`) on every
push to `main`; pushing a `v*` tag also attaches the APK to a release.

Locally, without an Android SDK, two checks run:

```sh
# eight GLSL shaders
for f in app/src/main/res/raw/*.glsl; do
  case "$f" in *_vert.glsl) glslangValidator -S vert "$f";; *) glslangValidator -S frag "$f";; esac
done

# engine maths, 68 assertions
javac -encoding UTF-8 -d out $(find app/src/main/java/dev/spark3d/engine -name '*.java') verify/EngineCheck.java
java -ea -cp out EngineCheck
```

To build an APK yourself you need JDK 17 + Android SDK 34:

```sh
gradle assembleDebug
```

Both `debug` and `release` are signed with the debug key, so they install
directly. A real signing config must replace this before publishing.

## Verified

- All eight shaders compile with `glslangValidator`.
- All Java sources compile against the real `android.jar` (platform 34), `-Xlint:all`.
- 68/68 engine checks pass (matrix maths, quaternions, noise determinism, touch
  sticks, multi-pointer handling, spatial hash, clock clamping).

## Not verified here

There is no Android device or GPU in this environment. On-device frame rate,
thermal behaviour, driver shader quirks and the actual look of the scene are
unmeasured. The HUD reports fps, frame ms, drawn model count, draw calls and
triangles so the first run on a phone answers those questions directly, and the
adaptive scaler keeps the frame rate up even if the device is slower than
expected.
