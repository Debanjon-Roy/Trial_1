package portfolio;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.PerspectiveTransform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modern visual effects engine providing:
 * 1. Interactive 3D Perspective Tilt on cards and interactive elements.
 * 2. Floating ambient glowing ember particle field in the background.
 * 3. Glowing particle burst cursor trail.
 */
public final class UiEffects {

    private static final Random RNG = new Random();

    private UiEffects() {}

    /**
     * Applies a tilt-toward-the-cursor effect with a dynamic directional glow to any Node.
     *
     * The tilt is a 2D PerspectiveTransform effect, NOT real 3D Rotate transforms. That is
     * deliberate: as soon as a node inside a ScrollPane carries a 3D rotation, JavaFX stops
     * clipping the scrolled content, and hovered cards/sections then paint over the header
     * and nav bar. A 2D effect looks the same but keeps the scroll clipping intact.
     */
    public static void apply3DTilt(Node node) {
        DropShadow restingShadow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 2, 0, 0.40), 16, 0.15, 0, 6);
        node.setEffect(restingShadow);

        PerspectiveTransform tilt = new PerspectiveTransform();
        DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 140, 60, 0.48), 24, 0.28, 0, 4);
        glow.setInput(tilt);

        // Cursor position across the node, each from -1.0 to 1.0.
        DoubleProperty tiltX = new SimpleDoubleProperty(0);
        DoubleProperty tiltY = new SimpleDoubleProperty(0);

        Runnable render = () -> {
            Bounds b = node.getLayoutBounds();
            double x0 = b.getMinX();
            double y0 = b.getMinY();
            double w = b.getWidth();
            double h = b.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double nx = tiltX.get();
            double ny = tiltY.get();

            // The edge nearest the cursor recedes slightly, like a card being pressed.
            double insetX = Math.min(w * 0.010, 9);
            double insetY = Math.min(h * 0.010, 9);
            double top = Math.max(0, -ny);
            double bottom = Math.max(0, ny);
            double left = Math.max(0, -nx);
            double right = Math.max(0, nx);

            tilt.setUlx(x0 + insetX * top);
            tilt.setUly(y0 + insetY * left);
            tilt.setUrx(x0 + w - insetX * top);
            tilt.setUry(y0 + insetY * right);
            tilt.setLrx(x0 + w - insetX * bottom);
            tilt.setLry(y0 + h - insetY * right);
            tilt.setLlx(x0 + insetX * bottom);
            tilt.setLly(y0 + h - insetY * left);

            // Glow shifts away from the cursor, simulating a light source.
            glow.setOffsetX(-nx * 8.0);
            glow.setOffsetY(-ny * 8.0 + 4);
        };
        tiltX.addListener((o, a, b) -> render.run());
        tiltY.addListener((o, a, b) -> render.run());

        Timeline[] reset = new Timeline[1];

        node.setOnMouseMoved(e -> {
            if (reset[0] != null) {
                reset[0].stop();
            }
            double w = node.getLayoutBounds().getWidth();
            double h = node.getLayoutBounds().getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double nx = (e.getX() - w / 2.0) / (w / 2.0);
            double ny = (e.getY() - h / 2.0) / (h / 2.0);
            tiltX.set(Math.max(-1.0, Math.min(1.0, nx)));
            tiltY.set(Math.max(-1.0, Math.min(1.0, ny)));
            render.run();

            // Pop out slightly
            node.setScaleX(1.018);
            node.setScaleY(1.018);
            if (node.getEffect() != glow) {
                node.setEffect(glow);
            }
        });

        node.setOnMouseExited(e -> {
            if (reset[0] != null) {
                reset[0].stop();
            }
            reset[0] = new Timeline(
                    new KeyFrame(Duration.millis(320),
                            new KeyValue(tiltX, 0, Interpolator.EASE_OUT),
                            new KeyValue(tiltY, 0, Interpolator.EASE_OUT),
                            new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                    )
            );
            reset[0].setOnFinished(ev -> node.setEffect(restingShadow));
            reset[0].play();
        });
    }

    /**
     * Initializes a floating glowing embers / ambient stardust particle field
     * over the background pane without affecting UI interaction.
     */
    public static AnimationTimer startAmbientParticles(Pane container, int count) {
        container.setMouseTransparent(true);
        container.setPickOnBounds(false);

        List<EmberParticle> embers = new ArrayList<>();
        Color[] glowColors = new Color[]{
                Color.web("#FFD54F", 0.45),  // warm gold
                Color.web("#FF8A65", 0.40),  // ember coral
                Color.web("#FFCC80", 0.50),  // amber star
                Color.web("#FFFFFF", 0.35)   // stardust
        };

        for (int i = 0; i < count; i++) {
            double radius = 1.5 + RNG.nextDouble() * 3.5;
            Color c = glowColors[RNG.nextInt(glowColors.length)];
            Circle dot = new Circle(radius, c);
            dot.setMouseTransparent(true);
            dot.setEffect(new DropShadow(radius * 3, c));

            EmberParticle ember = new EmberParticle(dot, RNG);
            embers.add(ember);
            container.getChildren().add(dot);
        }

        AnimationTimer timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double dt = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;

                double containerW = container.getWidth() > 0 ? container.getWidth() : 1060;
                double containerH = container.getHeight() > 0 ? container.getHeight() : 780;

                for (EmberParticle ember : embers) {
                    ember.update(dt, containerW, containerH);
                }
            }
        };
        timer.start();
        return timer;
    }

    /**
     * Spawns an ethereal glowing particle spark when cursor moves.
     */
    public static void spawnCursorSpark(Pane cursorLayer, double x, double y) {
        double angle = RNG.nextDouble() * 2 * Math.PI;
        double speed = 15 + RNG.nextDouble() * 35;
        double dx = Math.cos(angle) * speed;
        double dy = Math.sin(angle) * speed;

        Color[] sparkPalette = new Color[]{
                Color.web("#FFF59D"), // Radiant bright yellow
                Color.web("#FFD54F"), // Warm gold
                Color.web("#FF7043"), // Neon coral
                Color.web("#FFFFFF")  // Pure white spark
        };
        Color sparkColor = sparkPalette[RNG.nextInt(sparkPalette.length)];

        double radius = 3.0 + RNG.nextDouble() * 3.0;
        Circle spark = new Circle(x, y, radius, sparkColor);
        spark.setOpacity(0.9);
        spark.setMouseTransparent(true);
        spark.setEffect(new DropShadow(10, sparkColor));

        cursorLayer.getChildren().add(spark);

        ScaleTransition scale = new ScaleTransition(Duration.millis(550), spark);
        scale.setToX(2.0);
        scale.setToY(2.0);

        TranslateTransition move = new TranslateTransition(Duration.millis(550), spark);
        move.setByX(dx);
        move.setByY(dy);

        FadeTransition fade = new FadeTransition(Duration.millis(550), spark);
        fade.setToValue(0);

        ParallelTransition pt = new ParallelTransition(spark, scale, move, fade);
        pt.setOnFinished(ev -> cursorLayer.getChildren().remove(spark));
        pt.play();

        if (cursorLayer.getChildren().size() > 50) {
            cursorLayer.getChildren().remove(0);
        }
    }

    private static class EmberParticle {
        private final Circle node;
        private double x;
        private double y;
        private double speedY;
        private double swaySpeed;
        private double swayAmplitude;
        private double time;
        private double baseX;

        public EmberParticle(Circle node, Random rng) {
            this.node = node;
            this.x = rng.nextDouble() * 1200;
            this.y = rng.nextDouble() * 900;
            this.baseX = x;
            this.speedY = 12 + rng.nextDouble() * 26; // float upwards
            this.swaySpeed = 1.0 + rng.nextDouble() * 2.0;
            this.swayAmplitude = 15 + rng.nextDouble() * 30;
            this.time = rng.nextDouble() * 10;

            node.setTranslateX(x);
            node.setTranslateY(y);
        }

        public void update(double dt, double width, double height) {
            time += dt;
            y -= speedY * dt;
            x = baseX + Math.sin(time * swaySpeed) * swayAmplitude;

            if (y < -20) {
                y = height + 20 + RNG.nextDouble() * 40;
                baseX = RNG.nextDouble() * width;
                x = baseX;
            }

            node.setTranslateX(x);
            node.setTranslateY(y);
            node.setOpacity(0.3 + 0.4 * Math.abs(Math.sin(time * 1.5)));
        }
    }
}