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
 * Dynamic animated background for detail views (Projects, Research Papers, Achievements).
 * Switches theme, animation, and colors based on Item.Type:
 * - ACHIEVEMENT: Golden Stardust, shimmering light beams, and celestial constellations.
 * - RESEARCH: High-tech Sapphire neural network graph with connecting data vectors and quantum pulses.
 * - PROJECT: Cyber Matrix Emerald digital sparks, upward data streams, and glowing glyphs.
 */
public class DetailBackground extends Pane {

    private static final Random RNG = new Random();
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L / 30;

    private final Canvas canvas = new Canvas();
    private AnimationTimer timer;
    private Item.Type currentType = Item.Type.PROJECT;
    private double time = 0;

    // Achievement Particles
    private final List<StarParticle> stars = new ArrayList<>();
    // Research Neural Nodes
    private final List<NeuralNode> nodes = new ArrayList<>();
    // Project Cyber Sparks
    private final List<MatrixSpark> cyberSparks = new ArrayList<>();

    public DetailBackground() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        getChildren().add(canvas);

        widthProperty().addListener((obs, oldW, newW) -> canvas.setWidth(newW.doubleValue()));
        heightProperty().addListener((obs, oldH, newH) -> canvas.setHeight(newH.doubleValue()));

        visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible) {
                if (timer == null) {
                    startAnimation();
                }
            } else {
                stopAnimation();
            }
        });
    }

    public void setMode(Item.Type type) {
        this.currentType = type;
        double w = getWidth() > 0 ? getWidth() : 1060;
        double h = getHeight() > 0 ? getHeight() : 780;

        stars.clear();
        nodes.clear();
        cyberSparks.clear();

        if (type == Item.Type.ACHIEVEMENT) {
            for (int i = 0; i < 85; i++) {
                StarParticle s = new StarParticle();
                s.reset(w, h, true);
                stars.add(s);
            }
        } else if (type == Item.Type.RESEARCH) {
            for (int i = 0; i < 48; i++) {
                NeuralNode n = new NeuralNode();
                n.reset(w, h);
                nodes.add(n);
            }
        } else { // PROJECT
            for (int i = 0; i < 75; i++) {
                MatrixSpark m = new MatrixSpark();
                m.reset(w, h, true);
                cyberSparks.add(m);
            }
        }
    }

    public void startAnimation() {
        if (timer != null) {
            timer.stop();
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();

        timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                // Cap the frame rate: without this it redraws at the monitor's full
                // refresh rate, which starves the UI thread and makes clicks feel frozen.
                if (now - lastNano < FRAME_INTERVAL_NANOS) {
                    return;
                }
                double dt = Math.min((now - lastNano) / 1_000_000_000.0, 0.1);
                lastNano = now;
                time += dt;

                double w = getWidth() > 0 ? getWidth() : canvas.getWidth();
                double h = getHeight() > 0 ? getHeight() : canvas.getHeight();
                if (w <= 0 || h <= 0) return;

                gc.setGlobalBlendMode(BlendMode.SRC_OVER);
                gc.clearRect(0, 0, w, h);

                if (currentType == Item.Type.ACHIEVEMENT) {
                    renderAchievement(gc, dt, w, h);
                } else if (currentType == Item.Type.RESEARCH) {
                    renderResearch(gc, dt, w, h);
                } else {
                    renderProject(gc, dt, w, h);
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

    // =========================================================================
    // 1. ACHIEVEMENT THEME: Golden Stardust & Constellations
    // =========================================================================
    private void renderAchievement(GraphicsContext gc, double dt, double w, double h) {
        // Soft golden radial aura at top-center
        double glowPulse = 0.20 + 0.06 * Math.sin(time * 2.0);
        RadialGradient aura = new RadialGradient(
                0, 0,
                w / 2.0, h * 0.25,
                Math.max(w * 0.5, 400),
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 215, 64, glowPulse)),
                new Stop(0.5, Color.rgb(180, 110, 20, glowPulse * 0.4)),
                new Stop(1.0, Color.TRANSPARENT)
        );
        gc.setFill(aura);
        gc.fillRect(0, 0, w, h);

        gc.setGlobalBlendMode(BlendMode.ADD);

        // Constellation lines between nearby stars
        int starCount = stars.size();
        for (int i = 0; i < starCount; i++) {
            StarParticle s1 = stars.get(i);
            for (int j = i + 1; j < starCount; j++) {
                StarParticle s2 = stars.get(j);
                double dx = s1.x - s2.x;
                double dy = s1.y - s2.y;
                double distSq = dx * dx + dy * dy;
                if (distSq < 10000) { // dist < 100
                    double dist = Math.sqrt(distSq);
                    double alpha = (1.0 - dist / 100.0) * 0.25;
                    gc.setStroke(Color.rgb(255, 220, 120, alpha));
                    gc.setLineWidth(1.0);
                    gc.strokeLine(s1.x, s1.y, s2.x, s2.y);
                }
            }
        }

        // Render and update individual stars
        for (StarParticle s : stars) {
            s.update(dt, w, h);
            s.render(gc);
        }
    }

    private static class StarParticle {
        double x, y;
        double baseX;
        double vy;
        double radius;
        double twinkleSpeed;
        double phase;
        double swayAmp;
        Color color;

        void reset(double w, double h, boolean spreadY) {
            baseX = RNG.nextDouble() * w;
            x = baseX;
            y = spreadY ? RNG.nextDouble() * h : h + 10.0 + RNG.nextDouble() * 30.0;
            vy = -(20.0 + RNG.nextDouble() * 45.0); // slow celestial rise
            radius = 1.2 + RNG.nextDouble() * 2.8;
            twinkleSpeed = 1.8 + RNG.nextDouble() * 3.5;
            phase = RNG.nextDouble() * 10.0;
            swayAmp = 10.0 + RNG.nextDouble() * 20.0;

            int pick = RNG.nextInt(4);
            if (pick == 0) color = Color.rgb(255, 250, 205); // light goldenrod
            else if (pick == 1) color = Color.rgb(255, 215, 0); // radiant gold
            else if (pick == 2) color = Color.rgb(255, 179, 0); // amber
            else color = Color.rgb(255, 255, 255); // brilliant diamond white
        }

        void update(double dt, double w, double h) {
            y += vy * dt;
            phase += dt * twinkleSpeed;
            x = baseX + Math.sin(phase) * swayAmp;
            if (y < -15.0) {
                reset(w, h, false);
            }
        }

        void render(GraphicsContext gc) {
            double alpha = 0.4 + 0.55 * Math.abs(Math.sin(phase));
            Color c = Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
            gc.setFill(c);
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

            // Shimmer cross / halo on larger stars
            if (radius > 2.2) {
                double haloR = radius * 2.5;
                gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha * 0.3));
                gc.fillOval(x - haloR, y - haloR, haloR * 2, haloR * 2);
            }
        }
    }

    // =========================================================================
    // 2. RESEARCH THEME: Quantum Sapphire Neural Network Graph
    // =========================================================================
    private void renderResearch(GraphicsContext gc, double dt, double w, double h) {
        // Deep blue ambient pulse
        double pulse = 0.18 + 0.05 * Math.sin(time * 1.8);
        RadialGradient aura = new RadialGradient(
                0, 0,
                w * 0.5, h * 0.5,
                Math.max(w * 0.6, 500),
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 180, 255, pulse)),
                new Stop(0.6, Color.rgb(10, 80, 160, pulse * 0.4)),
                new Stop(1.0, Color.TRANSPARENT)
        );
        gc.setFill(aura);
        gc.fillRect(0, 0, w, h);

        gc.setGlobalBlendMode(BlendMode.ADD);

        // Update nodes first
        for (NeuralNode n : nodes) {
            n.update(dt, w, h);
        }

        // Interconnecting data vectors
        int count = nodes.size();
        for (int i = 0; i < count; i++) {
            NeuralNode n1 = nodes.get(i);
            for (int j = i + 1; j < count; j++) {
                NeuralNode n2 = nodes.get(j);
                double dx = n1.x - n2.x;
                double dy = n1.y - n2.y;
                double distSq = dx * dx + dy * dy;
                if (distSq < 16900) { // dist < 130
                    double dist = Math.sqrt(distSq);
                    double alpha = (1.0 - dist / 130.0) * 0.42;
                    gc.setStroke(Color.rgb(64, 196, 255, alpha));
                    gc.setLineWidth(1.1);
                    gc.strokeLine(n1.x, n1.y, n2.x, n2.y);
                }
            }
        }

        // Render nodes
        for (NeuralNode n : nodes) {
            n.render(gc, time);
        }
    }

    private static class NeuralNode {
        double x, y;
        double vx, vy;
        double radius;
        Color color;

        void reset(double w, double h) {
            x = RNG.nextDouble() * w;
            y = RNG.nextDouble() * h;
            double angle = RNG.nextDouble() * 2 * Math.PI;
            double speed = 12.0 + RNG.nextDouble() * 22.0;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;
            radius = 2.5 + RNG.nextDouble() * 3.5;

            int pick = RNG.nextInt(3);
            if (pick == 0) color = Color.rgb(0, 229, 255);   // electric cyan
            else if (pick == 1) color = Color.rgb(68, 138, 255); // sapphire blue
            else color = Color.rgb(187, 222, 251); // quantum pale blue
        }

        void update(double dt, double w, double h) {
            x += vx * dt;
            y += vy * dt;

            // Soft bounce off walls
            if (x < 10) { x = 10; vx = Math.abs(vx); }
            if (x > w - 10) { x = w - 10; vx = -Math.abs(vx); }
            if (y < 10) { y = 10; vy = Math.abs(vy); }
            if (y > h - 10) { y = h - 10; vy = -Math.abs(vy); }
        }

        void render(GraphicsContext gc, double time) {
            double pulse = 0.7 + 0.3 * Math.sin(time * 3.0 + x * 0.05);
            // Core
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.85 * pulse));
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

            // Halo
            double haloR = radius * 2.2;
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.28 * pulse));
            gc.fillOval(x - haloR, y - haloR, haloR * 2, haloR * 2);
        }
    }

    // =========================================================================
    // 3. PROJECT THEME: Cyber Matrix Emerald Sparks
    // =========================================================================
    private void renderProject(GraphicsContext gc, double dt, double w, double h) {
        // Emerald matrix ambient glow
        double pulse = 0.18 + 0.06 * Math.sin(time * 2.2);
        RadialGradient aura = new RadialGradient(
                0, 0,
                w * 0.5, h,
                Math.max(w * 0.55, 450),
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 230, 118, pulse)),
                new Stop(0.55, Color.rgb(4, 90, 60, pulse * 0.45)),
                new Stop(1.0, Color.TRANSPARENT)
        );
        gc.setFill(aura);
        gc.fillRect(0, 0, w, h);

        gc.setGlobalBlendMode(BlendMode.ADD);

        for (MatrixSpark m : cyberSparks) {
            m.update(dt, w, h);
            m.render(gc);
        }
    }

    private static class MatrixSpark {
        double x, y;
        double vy;
        double length;
        double width;
        double life;
        double maxLife;
        Color color;

        void reset(double w, double h, boolean spreadY) {
            x = RNG.nextDouble() * w;
            y = spreadY ? RNG.nextDouble() * h : h + 15.0 + RNG.nextDouble() * 30.0;
            vy = -(60.0 + RNG.nextDouble() * 140.0); // fast upward digital stream
            length = 8.0 + RNG.nextDouble() * 22.0;
            width = 1.6 + RNG.nextDouble() * 2.4;
            maxLife = 1.2 + RNG.nextDouble() * 1.8;
            life = spreadY ? RNG.nextDouble() * maxLife : 0;

            int pick = RNG.nextInt(4);
            if (pick == 0) color = Color.rgb(105, 240, 174); // neon mint
            else if (pick == 1) color = Color.rgb(0, 230, 118);  // electric emerald
            else if (pick == 2) color = Color.rgb(0, 200, 83);   // cyber green
            else color = Color.rgb(241, 248, 233); // digital code white
        }

        void update(double dt, double w, double h) {
            life += dt;
            y += vy * dt;
            if (y < -30.0 || life >= maxLife) {
                reset(w, h, false);
            }
        }

        void render(GraphicsContext gc) {
            double alpha = Math.min(1.0, (1.0 - (life / maxLife))) * 0.75;
            gc.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            gc.setLineWidth(width);
            gc.strokeLine(x, y, x, y + length);

            // Leading bright spark point
            gc.setFill(Color.color(1.0, 1.0, 1.0, alpha));
            gc.fillOval(x - width, y - width, width * 2, width * 2);
        }
    }
}