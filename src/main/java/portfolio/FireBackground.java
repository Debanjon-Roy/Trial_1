package portfolio;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animated fiery canvas background featuring:
 * 1. An incandescent additive flame layer at the bottom with flickering fire tongues.
 * 2. Rising glowing embers and fire sparks drifting upward through the entire scene.
 * 3. A breathing warm thermal glow that pulses realistically.
 * 4. Configurable/reduced FPS rendering (default 26-28 FPS) for cinematic pacing and lower resource usage.
 */
public class FireBackground extends Pane {

    private static final Random RNG = new Random();
    private static final int FLAME_PARTICLE_COUNT = 50;
    private static final int EMBER_PARTICLE_COUNT = 25;

    private int targetFps = 100; // Decreased frame rate for softer, cinematic fire
    private long frameIntervalNanos = 1_000_000_000L / targetFps;

    private final Canvas canvas = new Canvas();
    private final List<FlameParticle> flames = new ArrayList<>();
    private final List<EmberSpark> embers = new ArrayList<>();
    private AnimationTimer timer;
    private double time = 0;
    private long lastFrameNano = 0;

    public FireBackground() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        getChildren().add(canvas);

        widthProperty().addListener((obs, oldW, newW) -> canvas.setWidth(newW.doubleValue()));
        heightProperty().addListener((obs, oldH, newH) -> canvas.setHeight(newH.doubleValue()));

        for (int i = 0; i < FLAME_PARTICLE_COUNT; i++) {
            FlameParticle p = new FlameParticle();
            p.reset(800, 600, true);
            flames.add(p);
        }

        for (int i = 0; i < EMBER_PARTICLE_COUNT; i++) {
            EmberSpark e = new EmberSpark();
            e.reset(800, 600, true);
            embers.add(e);
        }

        startAnimation();
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(12, Math.min(60, fps));
        this.frameIntervalNanos = 1_000_000_000L / this.targetFps;
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void startAnimation() {
        if (timer != null) {
            timer.stop();
        }
        lastFrameNano = 0;

        GraphicsContext gc = canvas.getGraphicsContext2D();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Throttle frame rate to targetFps
                if (now - lastFrameNano < frameIntervalNanos) {
                    return;
                }

                double dt = lastFrameNano == 0 ? (1.0 / targetFps) : (now - lastFrameNano) / 1_000_000_000.0;
                // Guard against sudden large delta if paused or backgrounded
                if (dt > 0.1) {
                    dt = 1.0 / targetFps;
                }
                lastFrameNano = now;
                time += dt;

                double w = getWidth() > 0 ? getWidth() : canvas.getWidth();
                double h = getHeight() > 0 ? getHeight() : canvas.getHeight();
                if (w <= 0 || h <= 0) return;

                // Clear canvas with standard blend mode
                gc.setGlobalBlendMode(BlendMode.SRC_OVER);
                gc.clearRect(0, 0, w, h);

                // --- 1. Breathing bottom thermal glow ---
                double pulse = 0.22 + 0.08 * Math.sin(time * 2.4);
                RadialGradient thermalGlow = new RadialGradient(
                        0, 0,
                        w / 2.0, h,
                        Math.max(w * 0.55, 380),
                        false,
                        CycleMethod.NO_CYCLE,
                        new Stop(0.0, Color.rgb(255, 120, 20, pulse)),
                        new Stop(0.45, Color.rgb(220, 40, 0, pulse * 0.5)),
                        new Stop(1.0, Color.TRANSPARENT)
                );
                gc.setFill(thermalGlow);
                gc.fillRect(0, h - Math.min(300, h * 0.5), w, Math.min(300, h * 0.5));

                // --- 2. Additive Fire Particle Rendering ---
                gc.setGlobalBlendMode(BlendMode.ADD);

                // Draw and update bottom flames
                for (FlameParticle p : flames) {
                    p.update(dt, w, h);
                    if (p.isDead()) {
                        p.reset(w, h, false);
                    }
                    p.render(gc);
                }

                // Draw and update rising fiery embers and sparks
                for (EmberSpark e : embers) {
                    e.update(dt, w, h);
                    if (e.isDead(h)) {
                        e.reset(w, h, false);
                    }
                    e.render(gc);
                }
            }
        };
        timer.start();
    }

    public void stopAnimation() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Additive flame particle producing realistic white-hot to deep fiery orange plumes.
     */
    private static class FlameParticle {
        double x, y;
        double vx, vy;
        double initialRadius;
        double radius;
        double life;
        double maxLife;
        double swayFreq;
        double swayAmp;
        double timeOffset;

        void reset(double w, double h, boolean randomLife) {
            x = RNG.nextDouble() * w;
            y = h - RNG.nextDouble() * 25.0 + 10.0;
            vx = (RNG.nextDouble() - 0.5) * 28.0;
            vy = -(65.0 + RNG.nextDouble() * 110.0);
            initialRadius = 14.0 + RNG.nextDouble() * 22.0;
            radius = initialRadius;
            maxLife = 1.0 + RNG.nextDouble() * 1.5;
            life = randomLife ? RNG.nextDouble() * maxLife : 0;
            swayFreq = 2.0 + RNG.nextDouble() * 3.5;
            swayAmp = 18.0 + RNG.nextDouble() * 26.0;
            timeOffset = RNG.nextDouble() * 10.0;
        }

        void update(double dt, double w, double h) {
            life += dt;
            double progress = Math.min(1.0, life / maxLife);

            y += vy * dt;
            x += (vx + Math.sin((life + timeOffset) * swayFreq) * swayAmp) * dt;

            // Flame particles shrink as they ascend and burn out
            radius = initialRadius * (1.0 - progress * 0.75);
        }

        boolean isDead() {
            return life >= maxLife;
        }

        void render(GraphicsContext gc) {
            double progress = Math.min(1.0, life / maxLife);

            // Realistic incandescent combustion color transition:
            // 0% -> White-hot yellow
            // 35% -> Blazing gold/orange
            // 70% -> Fiery crimson
            // 100% -> Smoky dark red fade out
            double r, g, b, alpha;
            if (progress < 0.28) {
                double t = progress / 0.28;
                r = 1.0;
                g = 0.92 - t * 0.22;
                b = 0.65 - t * 0.55;
                alpha = 0.55 * (1.0 - progress);
            } else if (progress < 0.65) {
                double t = (progress - 0.28) / 0.37;
                r = 1.0;
                g = 0.70 - t * 0.45;
                b = 0.10 - t * 0.08;
                alpha = 0.42 * (1.0 - progress);
            } else {
                double t = (progress - 0.65) / 0.35;
                r = 0.95 - t * 0.35;
                g = 0.25 - t * 0.22;
                b = 0.02;
                alpha = 0.28 * (1.0 - progress);
            }

            gc.setFill(new Color(Math.max(0, Math.min(1, r)),
                    Math.max(0, Math.min(1, g)),
                    Math.max(0, Math.min(1, b)),
                    Math.max(0, Math.min(1, alpha))));

            gc.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        }
    }

    /**
     * Glowing ember sparks floating upward through the entire screen.
     */
    private static class EmberSpark {
        double x, y;
        double baseX;
        double vy;
        double radius;
        double time;
        double swaySpeed;
        double swayAmp;
        Color baseColor;

        void reset(double w, double h, boolean spreadY) {
            baseX = RNG.nextDouble() * w;
            x = baseX;
            y = spreadY ? RNG.nextDouble() * h : h + 10.0 + RNG.nextDouble() * 30.0;
            vy = -(35.0 + RNG.nextDouble() * 75.0);
            radius = 1.5 + RNG.nextDouble() * 3.2;
            time = RNG.nextDouble() * 10.0;
            swaySpeed = 1.2 + RNG.nextDouble() * 2.2;
            swayAmp = 15.0 + RNG.nextDouble() * 30.0;

            int pick = RNG.nextInt(4);
            if (pick == 0) {
                baseColor = Color.rgb(255, 245, 160); // white-gold spark
            } else if (pick == 1) {
                baseColor = Color.rgb(255, 200, 60);  // hot gold
            } else if (pick == 2) {
                baseColor = Color.rgb(255, 110, 40);  // ember orange
            } else {
                baseColor = Color.rgb(255, 60, 40);   // neon flame
            }
        }

        void update(double dt, double w, double h) {
            time += dt;
            y += vy * dt;
            x = baseX + Math.sin(time * swaySpeed) * swayAmp;
        }

        boolean isDead(double h) {
            return y < -20.0;
        }

        void render(GraphicsContext gc) {
            double flicker = 0.55 + 0.45 * Math.sin(time * 6.5);
            Color c = Color.color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.max(0.0, Math.min(1.0, 0.75 * flicker)));

            gc.setFill(c);
            gc.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);

            if (radius > 2.2) {
                Color halo = Color.color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                        0.25 * flicker);
                gc.setFill(halo);
                double haloR = radius * 2.4;
                gc.fillOval(x - haloR, y - haloR, haloR * 2.0, haloR * 2.0);
            }
        }
    }
}
