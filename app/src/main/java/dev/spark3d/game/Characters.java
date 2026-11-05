package dev.spark3d.game;

import java.util.ArrayList;
import java.util.List;

import dev.spark3d.gfx.Gltf;
import dev.spark3d.gfx.Skeletal;

/**
 * The cast: a handful of real animated characters wandering the field.
 *
 * <p>Everything here is CPU state. The render side turns a {@link Character}
 * into a skinned draw; the simulation decides which clip it is in, where it
 * stands, and how fast the clip runs.
 *
 * <p>Keeping the character count small (five) is a deliberate product choice:
 * the engine's claim is thousands of models, and the animation budget goes to
 * the field. A flock of thousands would not animate.
 */
public final class Characters {

    public static final class Character {
        public final String model;
        public final Gltf gltf;
        public final Skeletal skeletal;

        public float x, y, z;
        public float yaw;
        public String clip;
        public float time;
        public float speed = 0.6f;

        public Character(String model, Gltf gltf, Skeletal skeletal) {
            this.model = model;
            this.gltf = gltf;
            this.skeletal = skeletal;
            this.clip = gltf.animations.isEmpty() ? null : gltf.animations.get(0).name;
        }

        public Gltf.Animation current() {
            return gltf.animation(clip);
        }
    }

    public final List<Character> list = new ArrayList<>();

    public Characters(Gltf fox, int foxSkin, Gltf robot, int robotSkin,
                      Gltf cesium, int cesiumSkin, Gltf rigged, int riggedSkin) {
        spawn(fox, foxSkin, "Fox", 40f, -2f, 60f);
        spawn(robot, robotSkin, "RobotExpressive", -55f, -2f, -45f);
        spawn(cesium, cesiumSkin, "CesiumMan", -20f, -2f, -95f);
        spawn(rigged, riggedSkin, "RiggedFigure", 75f, -2f, -110f);
    }

    public Character spawn(Gltf gltf, int skinIndex, String model, float x, float y, float z) {
        Skeletal skeletal = new Skeletal(gltf, skinIndex);
        Character c = new Character(model, gltf, skeletal);
        c.x = x;
        c.y = y;
        c.z = z;
        list.add(c);
        return c;
    }

    public int count() {
        return list.size();
    }

    public void update(float dt) {
        for (Character c : list) {
            if (c.clip == null) continue;
            c.gltf.play(c.current(), c.time);
            c.gltf.advance(dt, c.speed);
            c.time = c.gltf.activeTime();
            c.skeletal.pose();
        }
    }
}
