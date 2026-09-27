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
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
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
     * Applies a true 3D tilt effect and dynamic specular lighting to any JavaFX Node.
     * As the mouse hovers and moves across the node, the node tilts in 3D perspective
     * towards the cursor and casts a dynamic directional glow shadow.
     */
    public static void apply3DTilt(Node node) {
        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        node.getTransforms().addAll(rotateX, rotateY);

        DropShadow restingShadow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 2, 0, 0.40), 16, 0.15, 0, 6);
        node.setEffect(restingShadow);

        node.setOnMouseMoved(e -> {
            double w = node.getBoundsInLocal().getWidth();
            double h = node.getBoundsInLocal().getHeight();
            if (w <= 0 || h <= 0) return;

            rotateX.setPivotX(w / 2.0);
            rotateX.setPivotY(h / 2.0);
            rotateY.setPivotX(w / 2.0);
            rotateY.setPivotY(h / 2.0);

            // Normalized position (-1.0 to 1.0)
            double nx = (e.getX() - w / 2.0) / (w / 2.0);
            double ny = (e.getY() - h / 2.0) / (h / 2.0);
            nx = Math.max(-1.0, Math.min(1.0, nx));
            ny = Math.max(-1.0, Math.min(1.0, ny));

            // Perspective angles
            double maxAngle = 6.5;
            rotateX.setAngle(-ny * maxAngle);
            rotateY.setAngle(nx * maxAngle);

            // Pop out slightly in 3D space
            node.setScaleX(1.018);
            node.setScaleY(1.018);

            // Dynamic directional glow shadow simulating light source
            DropShadow dynamicGlow = new DropShadow(
                    BlurType.GAUSSIAN,
                    Color.rgb(255, 140, 60, 0.48),
                    24,
                    0.28,
                    -nx * 8.0,
                    -ny * 8.0 + 4
            );
            node.setEffect(dynamicGlow);
        });

        node.setOnMouseExited(e -> {
            Timeline reset = new Timeline(
                    new KeyFrame(Duration.millis(320),
                            new KeyValue(rotateX.angleProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(rotateY.angleProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                    )
            );
            reset.setOnFinished(ev -> node.setEffect(restingShadow));
            reset.play();
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
