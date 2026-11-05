# Model & Animation Credits

Spark 3D ships real 3D models with real skeletal animation. The assets below are
from open-content sample libraries and are included (GLB binary, embedded
textures) in `app/src/main/assets/models/`.

| Model | Source | License |
| --- | --- | --- |
| Fox | [glTF-Sample-Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) — `Models/Fox` by tomkranis, Asobo Studio, scurest | CC-BY 4.0 |
| CesiumMan | [glTF-Sample-Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) — `Models/CesiumMan` by Cesium | CC-BY 4.0 |
| RiggedFigure | [glTF-Sample-Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) — `Models/RiggedFigure` by Cesium | CC-BY 4.0 |
| Duck | [glTF-Sample-Assets](https://github.com/KhronosGroup/glTF-Sample-Assets) — `Models/Duck` by Sony Computer Entertainment | SCEA Shared Source License 1.0 |
| RobotExpressive | [three.js](https://github.com/mrdoob/three.js) — `examples/models/gltf/RobotExpressive` by Tomás Laulhé (Quaternius), modified by Don McCurdy | CC0 |

## What each model contributes to the game

- **Fox** (`Fox.glb`, 159 KB) — 24-joint skeleton, three clips (Survey / Walk /
  Run). The player-adjacent animal.
- **CesiumMan** (`CesiumMan.glb`, 428 KB) — 19-joint skeleton, one clip (Take
  off / land). The big atmospheric character.
- **RiggedFigure** (`RiggedFigure.glb`, 49 KB) — 19-joint skeleton, one clip
  (dance-ish). Small articulated figure.
- **Duck** (`Duck.glb`, 118 KB) — static prop, 2 399 vertices, PNG texture. The
  flock: ~900 instances of one real model.
- **RobotExpressive** (`RobotExpressive.glb`, 453 KB) — 43-joint skeleton, 14
  clips (Idle / Walking / Dancing / Punch / …). The toy robot with the biggest
  animation set.

## License obligations

- CC-BY models require attribution; the table above and this file are that
  attribution, and the attribution is also shipped in the APK as the
  `assets/models/` credits are not in-game (kept in the repository).
- The SCEA Shared Source License permits use but requires the source and
  derivative notices to be included; we ship the unmodified GLB binary.
- CC0 requires nothing beyond the original notice.

If any of these models are ever swapped for ones with stricter terms, the
credits and the loader scope must be updated together.

## Loader scope (why we do not ship everything)

The loader (`dev.spark3d.gfx.Gltf`) accepts binary GLB, triangle primitives,
skins with inverse bind matrices, and translation/rotation/scale samplers with
STEP or LINEAR interpolation. Draco compression, sparse accessors, morph
targets and CUBICSPLINE animation curves are not supported; those assets are
rejected at load time rather than silently mis-rendered.

## Attribution text for the release notes

```
Fox (c) 2014 tomkranis, (c) 2017 Asobo Studio and scurest, CC-BY 4.0
CesiumMan (c) 2017 Cesium, CC-BY 4.0
RiggedFigure (c) 2017 Cesium, CC-BY 4.0
Duck (c) 2006 Sony Computer Entertainment America, SCEA Shared Source License 1.0
RobotExpressive by Tomás Laulhé (Quaternius), modified by Don McCurdy, CC0
```
