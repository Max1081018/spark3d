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

On top of the field: a **flock of real duck models** (~900 instances of one
glTF duck with a real PNG texture, instanced), and **four real animated
characters** — Fox (3 clips), RobotExpressive (14 clips), CesiumMan (1 clip) and
RiggedFigure (1 clip) — skinned through joint palettes and playing real
glTF animation clips. The engine parses glTF/GLB binary, bakes static models
into instanced vertex data, and plays skeletal animation on the GPU.

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

## Models and animation

The "twelve thousand models" claim became real with actual 3D content:

- **Real static models** — `app/src/main/assets/models/Duck.glb` baked into an
  instanced mesh (one draw call for the flock).
- **Real skinned characters** — Fox, RobotExpressive, CesiumMan, RiggedFigure,
  each with glTF animations (translation/rotation/scale samplers, LINEAR or
  STEP interpolation, quaternion slerp).

The renderer draws the characters through a joint palette: per frame the
skeleton is advanced and a flat array of joint world matrices is uploaded as a
uniform array (`uJoints[64]`) that the vertex shader uses to skin each vertex
through its four joints.

Every number is in `game/Tuning.java`.

## Layout

```
app/src/main/java/dev/spark3d/
  engine/   Mat4, Clock, Noise, Touch, SpatialHash3, SafeArea  (no Android imports)
  gfx/      Gl, Shader, Mesh, InstanceBatch, Camera, Font, UiBatch, Terrain, Sky,
            Gltf, GltfLoader, GltfMesh, ModelBatch, Skeletal, Sky
  game/     Tuning, World, Hud, GameRenderer, Characters
  GameView.java, MainActivity.java
app/src/main/res/raw/*.glsl      the twelve shaders, loaded at runtime
verify/EngineCheck.java          JVM checks for the platform-independent maths
verify/GltfCheck.java            JVM checks for the glTF/GLB parser + animation
```

## Build

The APK is built by GitHub Actions (`.github/workflows/build-apk.yml`) on every
push to `main`; pushing a `v*` tag also attaches the APK to a release.

Locally, without an Android SDK, two checks run:

```sh
# twelve GLSL shaders
for f in app/src/main/res/raw/*.glsl; do
  case "$f" in *_vert.glsl) glslangValidator -S vert "$f";; *_frag.glsl) glslangValidator -S frag "$f";; esac
done

# engine maths, 68 assertions
javac -encoding UTF-8 -d out $(find app/src/main/java/dev/spark3d/engine -name '*.java') verify/EngineCheck.java
java -ea -cp out EngineCheck

# glTF parser + animation maths, 17872 assertions
javac -encoding UTF-8 -d out $(find app/src/main/java/dev/spark3d/engine -name '*.java') \
  app/src/main/java/dev/spark3d/gfx/Gltf.java verify/EngineCheck.java verify/GltfCheck.java
java -ea -cp out GltfCheck app/src/main/assets/models
```

To build an APK yourself you need JDK 17 + Android SDK 34:

```sh
gradle assembleDebug
```

Both `debug` and `release` are signed with the debug key, so they install
directly. A real signing config must replace this before publishing.

## Verified

- All twelve shaders compile with `glslangValidator`.
- All Java sources compile against the real `android.jar` (platform 34), `-Xlint:all`.
- 68/68 engine checks pass (matrix maths, quaternions, noise determinism, touch
  sticks, multi-pointer handling, spatial hash, clock clamping).
- 17,872 checks pass over the five real shipped models: glTF chunk/accessor
  parsing, skin inverse-bind matrices, animation sampling (LINEAR + STEP, slerp),
  bakeStatic normals and bounds.

## Models

Real content ships in `app/src/main/assets/models/`:

- `Duck.glb` — static prop, the flock.
- `Fox.glb` — 24-joint skeleton, Survey / Walk / Run.
- `RobotExpressive.glb` — 43-joint skeleton, 14 clips.
- `CesiumMan.glb` — 19-joint skeleton, Take off / land.
- `RiggedFigure.glb` — 19-joint skeleton, dance clip.

Attribution and license obligations live in [CREDITS.md](CREDITS.md). The
loader only accepts binary GLB with triangle primitives and LINEAR/STEP
animation; anything else fails loudly at load.

## Not verified here

There is no Android device or GPU in this environment. On-device frame rate,
thermal behaviour, driver shader quirks and the actual look of the scene are
unmeasured. The HUD reports fps, frame ms, drawn model count, draw calls and
triangles so the first run on a phone answers those questions directly, and the
adaptive scaler keeps the frame rate up even if the device is slower than
expected.
