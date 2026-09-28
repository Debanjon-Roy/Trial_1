package portfolio;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Personal portfolio desktop app.
 *
 * Sections: About, Projects, Research, Achievements, Donate, Contact, Comments,
 * reachable both by scrolling and via the nav bar under the header. Clicking a
 * project/research/achievement card opens a detail view with room for a full
 * description, an attached image or PDF, and its own comment thread.
 *
 * Concurrency: every database read/write and every file copy runs on a
 * background thread from Concurrency's fixed thread pool via runBackground(...)
 * below, so the window never freezes while it talks to disk. See the comment
 * on Concurrency.java for the full list of where this is used.
 */
public class PortfolioApp extends Application {

    // ----------------------------------------------------- personal details
    // Your name, bio, links and password live in data/profile.properties, not
    // here — see Profile.java. Edit that file; this class only reads it.

    private static final Path ATTACHMENTS_DIR = Paths.get("data", "attachments");

    // ------------------------------------------------------------- state
    private final Profile profile = new Profile();
    private final Store store = new Store();
    private final BkashService bkash = new BkashService();
    private GeminiService geminiService;
    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<Comment> comments = FXCollections.observableArrayList();
    private final BooleanProperty ownerMode = new SimpleBooleanProperty(false);
    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final Map<String, Node> sectionAnchors = new LinkedHashMap<>();

    private final List<GeminiService.ChatMessage> chatHistory = new ArrayList<>();
    private VBox chatMessagesBox;
    private ScrollPane chatScroller;
    private TextField chatInputField;
    private Button sendChatButton;
    private HBox thinkingIndicator;
    private VBox apiSetupCard;
    private Label apiStatusBadge;

    private Stage stage;
    private VBox mainContent;
    private ScrollPane mainScroller;
    private ScrollPane detailScroller;
    private StackPane loadingOverlay;
    private Pane cursorLayer;
    private BorderPane rootPane;

    private VBox projectsBox;
    private VBox researchBox;
    private VBox achievementsBox;
    private VBox commentsBox;
    private Item currentDetailItem;
    private long lastParticleTime = 0;
    private FireBackground fireBackground;
    private DetailBackground detailBackground;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        profile.load();
        geminiService = new GeminiService(profile);

        projectsBox = new VBox(14);
        researchBox = new VBox(14);
        achievementsBox = new VBox(14);
        commentsBox = new VBox(12);

        Node about = buildAbout();
        Node projectsSection = buildItemSection("Projects", projectsBox);
        VBox researchSection = buildItemSection("Research Work", researchBox);
        researchSection.getChildren().add(1, buildStatusLegend());
        Node achievementsSection = buildItemSection("Achievements", achievementsBox);
        Node donateSection = buildDonate();
        Node chatSection = buildChatSection();
        Node contactSection = buildContact();
        Node commentsSection = buildCommentsSection("Comments", null, commentsBox);

        sectionAnchors.put("About", about);
        sectionAnchors.put("Projects", projectsSection);
        sectionAnchors.put("Research", researchSection);
        sectionAnchors.put("Achievements", achievementsSection);
        sectionAnchors.put("Donate", donateSection);
        sectionAnchors.put("Ask AI", chatSection);
        sectionAnchors.put("Contact", contactSection);
        sectionAnchors.put("Comments", commentsSection);

        mainContent = new VBox(30, about, projectsSection, researchSection, achievementsSection,
                donateSection, chatSection, contactSection, commentsSection);
        mainContent.getStyleClass().add("content");

        mainScroller = new ScrollPane(mainContent);
        mainScroller.setFitToWidth(true);
        mainScroller.getStyleClass().add("scroller");

        detailScroller = new ScrollPane();
        detailScroller.setFitToWidth(true);
        detailScroller.getStyleClass().add("scroller");
        detailScroller.setVisible(false);
        detailScroller.setManaged(false);

        fireBackground = new FireBackground();
        detailBackground = new DetailBackground();
        detailBackground.setVisible(false);
        detailBackground.setManaged(false);

        StackPane centerStack = new StackPane(fireBackground, detailBackground, mainScroller, detailScroller);
        fireBackground.prefWidthProperty().bind(centerStack.widthProperty());
        fireBackground.prefHeightProperty().bind(centerStack.heightProperty());
        detailBackground.prefWidthProperty().bind(centerStack.widthProperty());
        detailBackground.prefHeightProperty().bind(centerStack.heightProperty());

        rootPane = new BorderPane();
        rootPane.getStyleClass().add("root-pane");
        rootPane.setTop(new VBox(buildHeader(), buildNavBar()));
        rootPane.setCenter(centerStack);

        cursorLayer = new Pane();
        cursorLayer.setMouseTransparent(true);
        cursorLayer.setPickOnBounds(false);

        loadingOverlay = buildLoadingOverlay();

        StackPane sceneRoot = new StackPane(rootPane, cursorLayer, loadingOverlay);
        cursorLayer.prefWidthProperty().bind(sceneRoot.widthProperty());
        cursorLayer.prefHeightProperty().bind(sceneRoot.heightProperty());

        searchQuery.addListener((obs, oldValue, newValue) -> refreshLists());
        ownerMode.addListener((obs, oldValue, newValue) -> {
            refreshLists();
            if (currentDetailItem != null) {
                openDetail(currentDetailItem);
            }
        });

        Scene scene = new Scene(sceneRoot, 1060, 780);
        scene.setOnMouseMoved(this::onCursorMoved);
        URL css = getClass().getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle(profile.getName() + " — Portfolio");
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        loadDataInBackground();
    }

    @Override
    public void stop() {
        if (fireBackground != null) {
            fireBackground.stopAnimation();
        }
        if (detailBackground != null) {
            detailBackground.stopAnimation();
        }
        Concurrency.shutdown();
    }

    // -------------------------------------------------------- data loading

    private void loadDataInBackground() {
        runBackground(store::load, result -> {
            items.setAll(result.items);
            comments.setAll(result.comments);
            refreshLists();
            hideLoadingOverlay();
        });
    }

    private StackPane buildLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(52, 52);
        Label label = new Label("Loading your portfolio…");
        label.getStyleClass().add("loading-label");
        VBox box = new VBox(14, spinner, label);
        box.setAlignment(Pos.CENTER);
        StackPane overlay = new StackPane(box);
        overlay.getStyleClass().add("loading-overlay");
        return overlay;
    }

    private void hideLoadingOverlay() {
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);
    }

    // ------------------------------------------------------------- concurrency
    //
    // Wraps blocking work (SQL, file copies) in a javafx.concurrent.Task and
    // hands it to Concurrency's thread pool instead of running it on the
    // JavaFX Application Thread. Task guarantees onSucceeded/onFailed always
    // fire back on the FX thread, which is why touching the UI and the
    // ObservableLists inside onSuccess below is safe.

    private <T> void runBackground(Callable<T> work, Consumer<T> onSuccess) {
        runBackground(work, onSuccess, null);
    }

    private <T> void runBackground(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) {
                ex.printStackTrace(); // full chain, including "Caused by:", visible in the Run console
            }
            if (onError != null) {
                onError.accept(ex);
            } else {
                info("Something went wrong", ex == null ? "Unknown error." : describeError(ex));
            }
        });
        Concurrency.pool().submit(task);
    }

    /** Walks to the deepest cause so the dialog shows the real reason, not just a generic wrapper message. */
    private String describeError(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String top = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String root = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return top.equals(root) ? top : top + "\n\nDetails: " + root;
    }

    private void runBackground(Runnable work, Runnable onSuccess) {
        this.<Void>runBackground(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run());
    }

    // ------------------------------------------------------------- header

    private Node buildHeader() {
        Label brand = new Label(profile.getName());
        brand.getStyleClass().add("brand");

        TextField search = new TextField();
        search.setPromptText("Search projects, research, achievements, comments…");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(340);
        searchQuery.bind(search.textProperty());

        Button clear = new Button("✕");
        clear.getStyleClass().add("clear-button");
        clear.setTooltip(new Tooltip("Clear search"));
        clear.setOnAction(e -> search.clear());
        clear.visibleProperty().bind(search.textProperty().isEmpty().not());
        clear.managedProperty().bind(clear.visibleProperty());

        Button add = new Button("+");
        add.getStyleClass().add("add-button");
        add.setTooltip(new Tooltip("Add a project, research paper or achievement (owner only)"));
        add.setOnAction(e -> onAddClicked());

        Button lock = new Button();
        lock.getStyleClass().add("ghost-button");
        lock.textProperty().bind(Bindings.when(ownerMode).then("Log out").otherwise("Owner login"));
        lock.setOnAction(e -> {
            if (ownerMode.get()) {
                ownerMode.set(false);
            } else {
                requestLogin();
            }
        });

        Label badge = new Label("owner mode");
        badge.getStyleClass().add("owner-badge");
        badge.visibleProperty().bind(ownerMode);
        badge.managedProperty().bind(ownerMode);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, brand, badge, spacer, search, clear, add, lock);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildNavBar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("nav-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        for (String label : List.of("About", "Projects", "Research", "Achievements",
                "Donate", "Ask AI", "Contact", "Comments")) {
            Button button = new Button(label);
            button.getStyleClass().add("nav-button");
            button.setOnAction(e -> goToSection(label));
            bar.getChildren().add(button);
        }
        return bar;
    }

    private void goToSection(String key) {
        showMainView();
        Node target = sectionAnchors.get(key);
        if (target == null) {
            return;
        }
        mainContent.applyCss();
        mainContent.layout();
        double targetY = Math.max(0, target.getBoundsInParent().getMinY() - 10);
        double contentHeight = mainContent.getHeight();
        double viewportHeight = mainScroller.getViewportBounds().getHeight();
        double maxScroll = Math.max(1, contentHeight - viewportHeight);
        double value = Math.max(0, Math.min(1, targetY / maxScroll));
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(420),
                new KeyValue(mainScroller.vvalueProperty(), value, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    // -------------------------------------------------------------- about

    private Node buildAbout() {
        Node photo = circularPhoto("/images/profile", 130);

        Label name = new Label(profile.getName());
        name.getStyleClass().add("hero-name");

        Label tagline = new Label(profile.getTagline());
        tagline.getStyleClass().add("hero-tagline");
        tagline.setWrapText(true);

        Label about = new Label(profile.getAbout());
        about.getStyleClass().add("hero-about");
        about.setWrapText(true);

        VBox text = new VBox(8, name, tagline, about);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox hero = new HBox(26, photo, text);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().addAll("section", "hero");
        UiEffects.apply3DTilt(hero);
        return hero;
    }

    // ------------------------------------------------------ item sections

    private VBox buildItemSection(String title, VBox listBox) {
        VBox section = new VBox(14);
        section.getStyleClass().add("section");
        section.getChildren().addAll(sectionTitle(title), listBox);
        return section;
    }

    private Node buildStatusLegend() {
        FlowPane legend = new FlowPane(10, 8);
        legend.getChildren().addAll(
                statusPill(Item.Status.COMPLETED),
                statusPill(Item.Status.ONGOING),
                statusPill(Item.Status.NOT_STARTED));
        return legend;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label statusPill(Item.Status status) {
        Label pill = new Label(status.label());
        pill.getStyleClass().addAll("pill", status.styleClass());
        return pill;
    }

    private Label metaPill(String text) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("pill", "pill-neutral");
        return pill;
    }

    /** Card shown in a list. Clicking the title/description area opens the detail page. */
    private Node itemCard(Item item) {
        Label title = new Label(item.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (!item.getYear().isBlank()) {
            meta.getChildren().add(metaPill(item.getYear()));
        }
        if (item.getType() == Item.Type.RESEARCH && item.getStatus() != null) {
            meta.getChildren().add(statusPill(item.getStatus()));
        }

        Label description = new Label(item.getDescription());
        description.getStyleClass().add("card-text");
        description.setWrapText(true);

        VBox cardBody = new VBox(8, title);
        if (!meta.getChildren().isEmpty()) {
            cardBody.getChildren().add(meta);
        }
        cardBody.getChildren().add(description);
        cardBody.getStyleClass().add("card-body-clickable");
        cardBody.setCursor(Cursor.HAND);
        cardBody.setOnMouseClicked(e -> openDetail(item));

        VBox card = new VBox(10, cardBody);
        card.getStyleClass().add("card");

        if (!item.getLink().isBlank()) {
            Hyperlink link = new Hyperlink(item.getLink());
            link.getStyleClass().add("card-link");
            link.setOnAction(e -> openLink(item.getLink()));
            card.getChildren().add(link);
        }

        if (ownerMode.get()) {
            Button delete = new Button("Remove");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(e -> {
                if (confirm("Remove \"" + item.getTitle() + "\"?")) {
                    delete.setDisable(true);
                    runBackground(() -> store.deleteItem(item), () -> {
                        items.remove(item);
                        comments.removeIf(c -> c.getItemId() != null && c.getItemId() == item.getId());
                        refreshLists();
                    });
                }
            });
            HBox actions = new HBox(delete);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        }

        UiEffects.apply3DTilt(card);
        return card;
    }

    // ---------------------------------------------------------- detail view

    private void openDetail(Item item) {
        currentDetailItem = item;
        // Configure and start the per-type animated background
        detailBackground.setMode(item.getType());
        detailBackground.startAnimation();
        detailScroller.setContent(buildDetailContent(item));
        showDetailView(item.getType());
    }

    private void showDetailView(Item.Type type) {
        // Swap CSS class on the root pane to shift the base gradient
        stage.getScene().getRoot().getStyleClass().removeAll(
                "detail-view-project", "detail-view-research", "detail-view-achievement");
        stage.getScene().getRoot().getStyleClass().add(switch (type) {
            case PROJECT -> "detail-view-project";
            case RESEARCH -> "detail-view-research";
            case ACHIEVEMENT -> "detail-view-achievement";
        });

        // Hide fire, show detail background
        fireBackground.setVisible(false);
        detailBackground.setVisible(true);
        detailBackground.setManaged(true);

        mainScroller.setVisible(false);
        mainScroller.setManaged(false);
        detailScroller.setVisible(true);
        detailScroller.setManaged(true);
    }

    private void showMainView() {
        currentDetailItem = null;

        // Stop detail animation and hide it
        if (detailBackground != null) {
            detailBackground.stopAnimation();
            detailBackground.setVisible(false);
            detailBackground.setManaged(false);
        }

        // Restore fire background
        if (fireBackground != null) {
            fireBackground.setVisible(true);
        }

        // Remove detail-view CSS overrides
        stage.getScene().getRoot().getStyleClass().removeAll(
                "detail-view-project", "detail-view-research", "detail-view-achievement");

        detailScroller.setVisible(false);
        detailScroller.setManaged(false);
        mainScroller.setVisible(true);
        mainScroller.setManaged(true);
    }

    private VBox buildDetailContent(Item item) {
        Button back = new Button("← Back to portfolio");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> showMainView());

        Label title = new Label(item.getTitle());
        title.getStyleClass().add("detail-title");
        title.setWrapText(true);

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getChildren().add(metaPill(item.getType().label()));
        if (!item.getYear().isBlank()) {
            meta.getChildren().add(metaPill(item.getYear()));
        }
        if (item.getType() == Item.Type.RESEARCH && item.getStatus() != null) {
            meta.getChildren().add(statusPill(item.getStatus()));
        }

        Label description = new Label(item.getDescription().isBlank()
                ? "No description yet." : item.getDescription());
        description.getStyleClass().add("card-text");
        description.setWrapText(true);
        description.setMaxWidth(680);

        VBox detailCard = new VBox(14, title, meta, description);
        detailCard.getStyleClass().add("section");
        UiEffects.apply3DTilt(detailCard);

        Node attachments = buildAttachments(item);
        if (attachments != null) {
            detailCard.getChildren().add(attachments);
        }

        if (!item.getLink().isBlank()) {
            Hyperlink link = new Hyperlink(item.getLink());
            link.getStyleClass().add("card-link");
            link.setOnAction(e -> openLink(item.getLink()));
            detailCard.getChildren().add(link);
        }

        if (ownerMode.get()) {
            Button attach = new Button(item.getAttachments().isEmpty()
                    ? "Attach files (images or PDFs)" : "Add more files");
            attach.getStyleClass().add("ghost-button");
            attach.setOnAction(e -> chooseAndAttach(item));
            detailCard.getChildren().add(attach);
        }

        VBox itemCommentsBox = new VBox(12);
        Node commentsSection = buildCommentsSection(
                "Comments on this " + item.getType().label().toLowerCase(), item.getId(), itemCommentsBox);
        fillItemComments(item.getId(), itemCommentsBox);

        VBox page = new VBox(20, back, detailCard, commentsSection);
        page.getStyleClass().add("content");
        return page;
    }

    /**
     * Shows every attachment of an item: images as a clickable thumbnail grid,
     * everything else (PDFs etc.) as a list of rows. In owner mode each one also
     * gets a Remove button. Returns null when there is nothing to show.
     */
    private Node buildAttachments(Item item) {
        FlowPane images = new FlowPane(14, 14);
        VBox files = new VBox(8);

        for (String name : item.getAttachments()) {
            Path file = ATTACHMENTS_DIR.resolve(name);
            if (!Files.exists(file)) {
                continue;
            }
            Button remove = null;
            if (ownerMode.get()) {
                remove = new Button("Remove");
                remove.getStyleClass().add("danger-button");
                remove.setOnAction(e -> removeAttachment(item, name));
            }

            if (isImageFile(name)) {
                // Downscaled and loaded in the background so many large photos stay light.
                ImageView iv = new ImageView(new Image(file.toUri().toString(), 640, 0, true, true, true));
                iv.setPreserveRatio(true);
                iv.setFitWidth(300);
                iv.setCursor(Cursor.HAND);
                iv.setOnMouseClicked(e -> openLink(file.toUri().toString()));
                StackPane frame = new StackPane(iv);
                frame.getStyleClass().add("attachment-frame");
                VBox cell = new VBox(6, frame);
                cell.setAlignment(Pos.CENTER);
                if (remove != null) {
                    cell.getChildren().add(remove);
                }
                images.getChildren().add(cell);
            } else {
                Label label = new Label(displayName(name));
                label.getStyleClass().add("card-text");
                Button open = new Button("Open");
                open.getStyleClass().add("ghost-button");
                open.setOnAction(e -> openLink(file.toUri().toString()));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(10, label, spacer, open);
                row.setAlignment(Pos.CENTER_LEFT);
                if (remove != null) {
                    row.getChildren().add(remove);
                }
                files.getChildren().add(row);
            }
        }

        if (images.getChildren().isEmpty() && files.getChildren().isEmpty()) {
            return null;
        }
        VBox box = new VBox(14);
        if (!images.getChildren().isEmpty()) {
            box.getChildren().add(images);
        }
        if (!files.getChildren().isEmpty()) {
            box.getChildren().add(files);
        }
        return box;
    }

    /** "1790529219821_thesis.pdf" -> "thesis.pdf" (drops the uniqueness prefix). */
    private String displayName(String storedName) {
        return storedName.replaceFirst("^\\d+(_\\d+)?_", "");
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private FileChooser attachmentChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images and PDF", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.pdf"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser;
    }

    /** Lets the owner pick any number of files at once and adds them to the item. */
    private void chooseAndAttach(Item item) {
        List<File> chosen = attachmentChooser("Choose images or PDFs (select as many as you like)")
                .showOpenMultipleDialog(stage);
        if (chosen == null || chosen.isEmpty()) {
            return;
        }
        List<File> picked = new ArrayList<>(chosen);
        runBackground(() -> {
            for (File f : picked) {
                item.addAttachment(storeAttachmentBlocking(f));
            }
            store.updateItem(item);
        }, () -> openDetail(item));
    }

    private void removeAttachment(Item item, String name) {
        if (!confirm("Remove \"" + displayName(name) + "\" from this entry?")) {
            return;
        }
        runBackground(() -> {
            item.removeAttachment(name);
            store.updateItem(item);
            try {
                Files.deleteIfExists(ATTACHMENTS_DIR.resolve(name));
            } catch (Exception ignored) {
                // the entry no longer references it; a leftover file is harmless
            }
        }, () -> openDetail(item));
    }

    /**
     * Copies the chosen file into data/attachments. Meant to be called only from
     * inside a background task (see runBackground) since it does blocking I/O.
     * nanoTime keeps names unique even when several files are added at once.
     */
    private String storeAttachmentBlocking(File source) {
        try {
            Files.createDirectories(ATTACHMENTS_DIR);
            String safeName = System.currentTimeMillis() + "_" + (System.nanoTime() % 100000) + "_"
                    + source.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            Files.copy(source.toPath(), ATTACHMENTS_DIR.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
            return safeName;
        } catch (Exception e) {
            throw new RuntimeException("Could not copy the attachment: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------- donate

    private Node buildDonate() {
        Label blurb = new Label(
                "If my work has been useful to you, a small contribution helps me keep "
                        + "buying compute time and conference fees. Payments are handled by bKash.");
        blurb.getStyleClass().add("card-text");
        blurb.setWrapText(true);

        TextField amount = new TextField();
        amount.setPromptText("Amount in BDT");
        amount.setPrefWidth(160);
        amount.getStyleClass().add("input");

        HBox presets = new HBox(8);
        for (int value : new int[]{100, 500, 1000, 2000}) {
            Button preset = new Button("৳" + value);
            preset.getStyleClass().add("ghost-button");
            preset.setOnAction(e -> amount.setText(String.valueOf(value)));
            presets.getChildren().add(preset);
        }

        TextField donorName = new TextField();
        donorName.setPromptText("Your name (optional)");
        donorName.setPrefWidth(220);
        donorName.getStyleClass().add("input");

        Button donate = new Button("Donate with bKash");
        donate.getStyleClass().add("primary-button");
        donate.setOnAction(e -> handleDonate(amount.getText(), donorName.getText()));

        HBox row = new HBox(10, amount, donorName, donate);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14, sectionTitle("Donate"), blurb, presets, row);
        section.getStyleClass().add("section");
        UiEffects.apply3DTilt(section);
        return section;
    }

    private void handleDonate(String rawAmount, String donorName) {
        double amount;
        try {
            amount = Double.parseDouble(rawAmount.trim());
        } catch (NumberFormatException e) {
            info("Invalid amount", "Please type a number, for example 500.");
            return;
        }

        String reference = donorName == null || donorName.isBlank() ? "Anonymous" : donorName.trim();
        BkashService.PaymentResult result = bkash.startPayment(amount, reference);

        if (result.success && result.paymentUrl != null) {
            openLink(result.paymentUrl);
        } else {
            info("bKash", result.message);
        }
    }

    // ----------------------------------------------------------- chatbot

    private Node buildChatSection() {
        Label title = sectionTitle("Ask AI Assistant");

        Label subtitle = new Label("Confused about any project, research work, or technical detail? "
                + "Ask Debanjon's AI assistant powered by Google Gemini!");
        subtitle.getStyleClass().add("card-text");
        subtitle.setWrapText(true);

        apiStatusBadge = new Label();
        updateChatApiStatus();

        Button configKeyBtn = new Button("Configure Key");
        configKeyBtn.getStyleClass().add("ghost-button");
        configKeyBtn.setTooltip(new Tooltip("Set or view your Gemini API key"));
        configKeyBtn.setOnAction(e -> {
            boolean visible = !apiSetupCard.isVisible();
            apiSetupCard.setVisible(visible);
            apiSetupCard.setManaged(visible);
        });

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, new VBox(4, title, subtitle), titleSpacer, apiStatusBadge, configKeyBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        apiSetupCard = buildApiSetupCard();
        boolean isConfigured = geminiService != null && geminiService.isConfigured();
        apiSetupCard.setVisible(!isConfigured);
        apiSetupCard.setManaged(!isConfigured);

        Node suggestionChips = buildSuggestionChips();

        chatMessagesBox = new VBox(12);
        chatScroller = new ScrollPane(chatMessagesBox);
        chatScroller.setFitToWidth(true);
        chatScroller.setPrefHeight(340);
        chatScroller.setMaxHeight(460);
        chatScroller.getStyleClass().addAll("scroller", "chat-scroller");

        thinkingIndicator = buildThinkingIndicator();
        thinkingIndicator.setVisible(false);
        thinkingIndicator.setManaged(false);

        chatInputField = new TextField();
        chatInputField.setPromptText("Ask anything (e.g. 'Explain the research work', 'What are your top projects?')...");
        chatInputField.getStyleClass().add("input");
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        chatInputField.setOnAction(e -> handleSendChatMessage(chatInputField.getText()));

        sendChatButton = new Button("Ask AI ➔");
        sendChatButton.getStyleClass().add("primary-button");
        sendChatButton.setOnAction(e -> handleSendChatMessage(chatInputField.getText()));

        Button clearChatButton = new Button("Clear Chat");
        clearChatButton.getStyleClass().add("ghost-button");
        clearChatButton.setTooltip(new Tooltip("Start a new conversation"));
        clearChatButton.setOnAction(e -> resetChat());

        HBox inputRow = new HBox(10, chatInputField, sendChatButton, clearChatButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        resetChat();

        VBox section = new VBox(14, topBar, apiSetupCard, suggestionChips, chatScroller, thinkingIndicator, inputRow);
        section.getStyleClass().addAll("section", "chat-container");
        UiEffects.apply3DTilt(section);
        return section;
    }

    private VBox buildApiSetupCard() {
        Label cardTitle = new Label("🔑 Gemini API Key Configuration");
        cardTitle.getStyleClass().add("card-title");

        Label cardDesc = new Label("The chatbot is powered by Google Gemini. Enter your free API key from Google AI Studio below, "
                + "or configure 'gemini_api_key' in data/profile.properties. Once added, the chatbot activates instantly.");
        cardDesc.getStyleClass().add("card-text");
        cardDesc.setWrapText(true);

        PasswordField keyInput = new PasswordField();
        keyInput.setPromptText("Paste your Gemini API key (AIzaSy...)");
        keyInput.getStyleClass().add("input");
        keyInput.setText(profile.getGeminiApiKey());
        HBox.setHgrow(keyInput, Priority.ALWAYS);

        Button saveBtn = new Button("Save & Connect");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            String key = keyInput.getText().trim();
            profile.setGeminiApiKey(key);
            updateChatApiStatus();
            if (geminiService.isConfigured()) {
                apiSetupCard.setVisible(false);
                apiSetupCard.setManaged(false);
                GeminiService.ChatMessage connectedMsg = new GeminiService.ChatMessage(
                        GeminiService.ChatMessage.Sender.SYSTEM,
                        "✨ Google Gemini connected successfully (" + geminiService.getModel() + ")! You can now ask questions."
                );
                chatHistory.add(connectedMsg);
                renderChatMessage(connectedMsg);
            } else {
                info("API Key", "API key cleared.");
            }
        });

        Hyperlink getLink = new Hyperlink("Get Free API Key (aistudio.google.com) ↗");
        getLink.getStyleClass().add("card-link");
        getLink.setOnAction(e -> openLink("https://aistudio.google.com"));

        Button closeBtn = new Button("Hide");
        closeBtn.getStyleClass().add("ghost-button");
        closeBtn.setOnAction(e -> {
            apiSetupCard.setVisible(false);
            apiSetupCard.setManaged(false);
        });

        HBox inputRow = new HBox(10, keyInput, saveBtn, closeBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, cardTitle, cardDesc, inputRow, getLink);
        card.getStyleClass().add("api-setup-card");
        return card;
    }

    private void updateChatApiStatus() {
        if (apiStatusBadge == null) return;
        if (geminiService != null && geminiService.isConfigured()) {
            apiStatusBadge.setText("● Gemini Ready (" + geminiService.getModel() + ")");
            apiStatusBadge.getStyleClass().setAll("pill", "status-completed");
        } else {
            apiStatusBadge.setText("○ API Key Pending");
            apiStatusBadge.getStyleClass().setAll("pill", "status-ongoing");
        }
    }

    private Node buildSuggestionChips() {
        FlowPane chips = new FlowPane(8, 8);
        List<String> prompts = List.of(
                "👋 Tell me about Debanjon Roy",
                "💻 What are his top projects?",
                "🔬 Explain his research work",
                "📬 How can I contact or hire him?",
                "☕ How do donations work?",
                "🛠️ What technologies and languages does he use?"
        );
        for (String p : prompts) {
            Button chip = new Button(p);
            chip.getStyleClass().add("chat-chip");
            chip.setOnAction(e -> handleSendChatMessage(p));
            chips.getChildren().add(chip);
        }
        return chips;
    }

    private HBox buildThinkingIndicator() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(20, 20);

        Label label = new Label("Gemini is thinking…");
        label.getStyleClass().add("chat-thinking-label");

        HBox box = new HBox(10, spinner, label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("chat-thinking");
        return box;
    }

    private void resetChat() {
        chatHistory.clear();
        if (chatMessagesBox != null) {
            chatMessagesBox.getChildren().clear();
        }

        GeminiService.ChatMessage welcome = new GeminiService.ChatMessage(
                GeminiService.ChatMessage.Sender.BOT,
                "Hello! I am Debanjon's AI assistant powered by Google Gemini.\n\n"
                        + "If you're confused by any project, curious about his research publications, "
                        + "or looking to collaborate, ask me anything!"
        );
        chatHistory.add(welcome);
        renderChatMessage(welcome);
    }

    private void renderChatMessage(GeminiService.ChatMessage msg) {
        if (chatMessagesBox == null) return;

        if (msg.getSender() == GeminiService.ChatMessage.Sender.USER) {
            Label header = new Label("You  ·  " + msg.getFormattedTime());
            header.getStyleClass().add("chat-header-user");

            Label text = new Label(msg.getText());
            text.getStyleClass().add("chat-text");
            text.setWrapText(true);
            text.setMaxWidth(560);

            VBox bubble = new VBox(4, header, text);
            bubble.getStyleClass().add("chat-bubble-user");

            HBox row = new HBox(bubble);
            row.setAlignment(Pos.CENTER_RIGHT);
            chatMessagesBox.getChildren().add(row);

        } else if (msg.getSender() == GeminiService.ChatMessage.Sender.BOT) {
            Label header = new Label("✨ Gemini AI  ·  " + msg.getFormattedTime());
            header.getStyleClass().add("chat-header-bot");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button copyBtn = new Button("Copy");
            copyBtn.getStyleClass().add("ghost-button");
            copyBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
            copyBtn.setOnAction(e -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(msg.getText());
                clipboard.setContent(content);
                copyBtn.setText("Copied!");
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1500), evt -> copyBtn.setText("Copy")));
                timeline.play();
            });

            HBox headerRow = new HBox(8, header, spacer, copyBtn);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label text = new Label(msg.getText());
            text.getStyleClass().add("chat-text");
            text.setWrapText(true);
            text.setMaxWidth(640);

            VBox bubble = new VBox(6, headerRow, text);
            bubble.getStyleClass().add("chat-bubble-bot");

            HBox row = new HBox(bubble);
            row.setAlignment(Pos.CENTER_LEFT);
            chatMessagesBox.getChildren().add(row);

        } else {
            Label text = new Label(msg.getText());
            text.getStyleClass().add("chat-text");
            text.setWrapText(true);

            VBox bubble = new VBox(text);
            bubble.getStyleClass().add("chat-bubble-system");

            HBox row = new HBox(bubble);
            row.setAlignment(Pos.CENTER);
            chatMessagesBox.getChildren().add(row);
        }

        if (chatScroller != null) {
            Platform.runLater(() -> {
                chatMessagesBox.applyCss();
                chatMessagesBox.layout();
                chatScroller.layout();
                chatScroller.setVvalue(1.0);
            });
        }
    }

    private void handleSendChatMessage(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String trimmed = query.trim();
        if (chatInputField != null) {
            chatInputField.clear();
        }

        GeminiService.ChatMessage userMsg = new GeminiService.ChatMessage(
                GeminiService.ChatMessage.Sender.USER, trimmed);
        chatHistory.add(userMsg);
        renderChatMessage(userMsg);

        if (!geminiService.isConfigured()) {
            if (apiSetupCard != null) {
                apiSetupCard.setVisible(true);
                apiSetupCard.setManaged(true);
            }

            GeminiService.ChatMessage notConfiguredMsg = new GeminiService.ChatMessage(
                    GeminiService.ChatMessage.Sender.BOT,
                    "⚠️ Gemini API key is not configured yet.\n\n"
                            + "Please enter your API key in the Setup box above, or add 'gemini_api_key=YOUR_KEY' "
                            + "in data/profile.properties.\n\n"
                            + "You can get a free API key at https://aistudio.google.com (no credit card required)."
            );
            chatHistory.add(notConfiguredMsg);
            renderChatMessage(notConfiguredMsg);
            return;
        }

        if (chatInputField != null) {
            chatInputField.setDisable(true);
        }
        if (sendChatButton != null) {
            sendChatButton.setDisable(true);
        }
        if (thinkingIndicator != null) {
            thinkingIndicator.setVisible(true);
            thinkingIndicator.setManaged(true);
        }

        if (chatScroller != null) {
            Platform.runLater(() -> {
                chatScroller.layout();
                chatScroller.setVvalue(1.0);
            });
        }

        runBackground(
                () -> geminiService.ask(chatHistory, items),
                reply -> {
                    if (thinkingIndicator != null) {
                        thinkingIndicator.setVisible(false);
                        thinkingIndicator.setManaged(false);
                    }
                    if (chatInputField != null) {
                        chatInputField.setDisable(false);
                    }
                    if (sendChatButton != null) {
                        sendChatButton.setDisable(false);
                    }

                    GeminiService.ChatMessage botMsg = new GeminiService.ChatMessage(
                            GeminiService.ChatMessage.Sender.BOT, reply);
                    chatHistory.add(botMsg);
                    renderChatMessage(botMsg);
                    if (chatInputField != null) {
                        chatInputField.requestFocus();
                    }
                },
                error -> {
                    if (thinkingIndicator != null) {
                        thinkingIndicator.setVisible(false);
                        thinkingIndicator.setManaged(false);
                    }
                    if (chatInputField != null) {
                        chatInputField.setDisable(false);
                    }
                    if (sendChatButton != null) {
                        sendChatButton.setDisable(false);
                    }

                    GeminiService.ChatMessage errorMsg = new GeminiService.ChatMessage(
                            GeminiService.ChatMessage.Sender.BOT,
                            "⚠️ Could not contact Gemini: " + describeError(error)
                    );
                    chatHistory.add(errorMsg);
                    renderChatMessage(errorMsg);
                    if (chatInputField != null) {
                        chatInputField.requestFocus();
                    }
                }
        );
    }

    // ------------------------------------------------------------ contact

    private Node buildContact() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);

        grid.add(contactRow("/images/github", "GH", "#24292E",
                "GitHub", profile.getGithubUrl(), profile.getGithubUrl()), 0, 0);
        grid.add(contactRow("/images/linkedin", "in", "#0A66C2",
                "LinkedIn", profile.getLinkedinUrl(), profile.getLinkedinUrl()), 1, 0);
        grid.add(contactRow("/images/email", "@", "#D93025",
                "Email", profile.getEmail(), "mailto:" + profile.getEmail()), 0, 1);
        grid.add(contactRow("/images/whatsapp", "W", "#25D366",
                "WhatsApp", "+" + profile.getWhatsapp(), "https://wa.me/" + profile.getWhatsapp()), 1, 1);

        VBox section = new VBox(14, sectionTitle("Contact Me"), grid);
        section.getStyleClass().add("section");
        return section;
    }

    private Node contactRow(String imageResource, String fallbackText, String badgeColor,
                            String label, String value, String url) {
        Node logo = logoNode(imageResource, fallbackText, badgeColor);

        Label name = new Label(label);
        name.getStyleClass().add("contact-label");

        Label detail = new Label(value);
        detail.getStyleClass().add("contact-value");

        VBox text = new VBox(2, name, detail);
        HBox row = new HBox(14, logo, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("contact-row");
        row.setOnMouseClicked(e -> openLink(url));
        row.setCursor(Cursor.HAND);
        UiEffects.apply3DTilt(row);
        return row;
    }

    // ----------------------------------------------------------- comments

    /**
     * Builds a comments block. itemId == null means the general, site-wide
     * comments section; any other value scopes it to that item's detail page.
     */
    private Node buildCommentsSection(String title, Integer itemId, VBox listBox) {
        TextField author = new TextField();
        author.setPromptText("Your name");
        author.setPrefWidth(200);
        author.getStyleClass().add("input");

        TextArea body = new TextArea();
        body.setPromptText("Leave a comment…");
        body.setPrefRowCount(3);
        body.setWrapText(true);
        body.getStyleClass().add("input");

        Button post = new Button("Post comment");
        post.getStyleClass().add("primary-button");
        post.setOnAction(e -> {
            if (body.getText().isBlank()) {
                info("Empty comment", "Please write something before posting.");
                return;
            }
            Comment comment = new Comment(author.getText(), body.getText(), LocalDateTime.now(), itemId);
            post.setDisable(true);
            runBackground(() -> store.insertComment(comment), () -> {
                post.setDisable(false);
                comments.add(0, comment);
                author.clear();
                body.clear();
                if (itemId == null) {
                    fillGeneralComments();
                } else {
                    fillItemComments(itemId, listBox);
                }
            });
        });

        HBox actions = new HBox(10, author, post);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14, sectionTitle(title), body, actions, listBox);
        section.getStyleClass().add("section");
        return section;
    }

    private Node commentCard(Comment comment, Runnable afterDelete) {
        Label header = new Label(comment.getAuthor() + "  ·  " + comment.getPostedAtDisplay());
        header.getStyleClass().add("comment-header");

        Label text = new Label(comment.getText());
        text.getStyleClass().add("card-text");
        text.setWrapText(true);

        VBox card = new VBox(6, header, text);
        card.getStyleClass().addAll("card", "comment-card");

        if (ownerMode.get()) {
            Button delete = new Button("Delete");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(e -> {
                if (confirm("Delete this comment?")) {
                    delete.setDisable(true);
                    runBackground(() -> store.deleteComment(comment), () -> {
                        comments.remove(comment);
                        afterDelete.run();
                    });
                }
            });
            HBox row = new HBox(delete);
            row.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(row);
        }
        UiEffects.apply3DTilt(card);
        return card;
    }

    // ------------------------------------------------------------ refresh

    private void refreshLists() {
        fillItems(projectsBox, Item.Type.PROJECT);
        fillItems(researchBox, Item.Type.RESEARCH);
        fillItems(achievementsBox, Item.Type.ACHIEVEMENT);
        fillGeneralComments();
    }

    private void fillItems(VBox box, Item.Type type) {
        box.getChildren().clear();
        String query = searchQuery.get();
        List<Item> matching = items.stream()
                .filter(item -> item.getType() == type && item.matches(query))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            box.getChildren().add(emptyLabel(query, type.label().toLowerCase() + "s"));
            return;
        }
        for (Item item : matching) {
            box.getChildren().add(itemCard(item));
        }
    }

    private void fillGeneralComments() {
        commentsBox.getChildren().clear();
        String query = searchQuery.get();
        List<Comment> matching = comments.stream()
                .filter(c -> c.getItemId() == null && c.matches(query))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            commentsBox.getChildren().add(emptyLabel(query, "comments"));
            return;
        }
        for (Comment c : matching) {
            commentsBox.getChildren().add(commentCard(c, this::fillGeneralComments));
        }
    }

    private void fillItemComments(int itemId, VBox box) {
        box.getChildren().clear();
        List<Comment> matching = comments.stream()
                .filter(c -> c.getItemId() != null && c.getItemId() == itemId)
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            box.getChildren().add(emptyLabel(null, "comments"));
            return;
        }
        for (Comment c : matching) {
            box.getChildren().add(commentCard(c, () -> fillItemComments(itemId, box)));
        }
    }

    private Label emptyLabel(String query, String what) {
        String message = (query == null || query.isBlank())
                ? "No " + what + " yet."
                : "No " + what + " match \"" + query + "\".";
        Label label = new Label(message);
        label.getStyleClass().add("empty-label");
        return label;
    }

    // ----------------------------------------------------- owner workflow

    private void onAddClicked() {
        if (!ownerMode.get() && !requestLogin()) {
            return;
        }
        showAddDialog();
    }

    private boolean requestLogin() {
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        VBox content = new VBox(8, new Label("Only the owner can add or remove entries."), password);
        content.setPadding(new Insets(6, 0, 0, 0));

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Owner login");
        dialog.setHeaderText("Enter your password");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
        applyStylesheet(dialog);

        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == null) {
            return false;
        }
        if (Auth.verify(result.get(), profile.getPasswordHash())) {
            ownerMode.set(true);
            return true;
        }
        info("Login failed", "That password is not correct.");
        return false;
    }

    private void showAddDialog() {
        ComboBox<Item.Type> type = new ComboBox<>(FXCollections.observableArrayList(Item.Type.values()));
        type.setValue(Item.Type.PROJECT);
        type.setMaxWidth(Double.MAX_VALUE);

        TextField title = new TextField();
        title.setPromptText("Title");

        TextArea description = new TextArea();
        description.setPromptText("Short description");
        description.setPrefRowCount(4);
        description.setWrapText(true);

        ComboBox<Item.Status> status =
                new ComboBox<>(FXCollections.observableArrayList(Item.Status.values()));
        status.setValue(Item.Status.NOT_STARTED);
        status.setMaxWidth(Double.MAX_VALUE);

        Label statusLabel = new Label("Status");
        status.visibleProperty().bind(type.valueProperty().isEqualTo(Item.Type.RESEARCH));
        status.managedProperty().bind(status.visibleProperty());
        statusLabel.visibleProperty().bind(status.visibleProperty());
        statusLabel.managedProperty().bind(status.visibleProperty());

        TextField link = new TextField();
        link.setPromptText("https://… (optional)");

        TextField year = new TextField();
        year.setPromptText("2026 (optional)");

        Button chooseFile = new Button("Choose files (optional)");
        Button clearFiles = new Button("Clear");
        clearFiles.getStyleClass().add("danger-button");
        Label chosenFileLabel = new Label("No files chosen");
        chosenFileLabel.getStyleClass().add("card-text");
        List<File> chosenFiles = new ArrayList<>();
        Runnable updateFileLabel = () -> {
            clearFiles.setVisible(!chosenFiles.isEmpty());
            clearFiles.setManaged(!chosenFiles.isEmpty());
            chosenFileLabel.setText(chosenFiles.isEmpty() ? "No files chosen"
                    : chosenFiles.size() == 1 ? chosenFiles.get(0).getName()
                    : chosenFiles.size() + " files chosen");
        };
        updateFileLabel.run();
        chooseFile.setOnAction(e -> {
            List<File> picked = attachmentChooser("Choose images or PDFs (select as many as you like)")
                    .showOpenMultipleDialog(stage);
            if (picked != null) {
                for (File f : picked) {
                    if (!chosenFiles.contains(f)) {
                        chosenFiles.add(f);
                    }
                }
                updateFileLabel.run();
            }
        });
        clearFiles.setOnAction(e -> {
            chosenFiles.clear();
            updateFileLabel.run();
        });
        HBox fileRow = new HBox(10, chooseFile, chosenFileLabel, clearFiles);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(8, 0, 0, 0));
        form.addRow(0, new Label("Type"), type);
        form.addRow(1, new Label("Title"), title);
        form.addRow(2, new Label("Description"), description);
        form.addRow(3, statusLabel, status);
        form.addRow(4, new Label("Link"), link);
        form.addRow(5, new Label("Year"), year);
        form.addRow(6, new Label("Attachment"), fileRow);

        Dialog<Item> dialog = new Dialog<>();
        dialog.setTitle("Add entry");
        dialog.setHeaderText("New project, research paper or achievement");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStylesheet(dialog);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        title.textProperty().addListener((obs, oldValue, newValue) ->
                okButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Item.Status chosen = type.getValue() == Item.Type.RESEARCH ? status.getValue() : null;
            return new Item(type.getValue(), title.getText().trim(), description.getText().trim(),
                    chosen, link.getText().trim(), year.getText().trim());
        });

        dialog.showAndWait().ifPresent(item -> {
            List<File> attachmentFiles = new ArrayList<>(chosenFiles);
            runBackground(() -> {
                for (File f : attachmentFiles) {
                    item.addAttachment(storeAttachmentBlocking(f));
                }
                store.insertItem(item);
            }, () -> {
                items.add(item);
                refreshLists();
            });
        });
    }

    // ------------------------------------------------------------- helpers

    private Node circularPhoto(String basePath, double size) {
        Image image = loadImage(basePath);
        Node photoNode;
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(false);
            Circle clip = new Circle(size / 2, size / 2, size / 2);
            view.setClip(clip);
            photoNode = view;
        } else {
            Circle placeholder = new Circle(size / 2, Color.web("#FFFFFF", 0.18));
            placeholder.setStroke(Color.web("#FFFFFF", 0.55));
            placeholder.setStrokeWidth(2);

            Label initials = new Label(initialsOf(profile.getName()));
            initials.getStyleClass().add("photo-initials");

            StackPane stack = new StackPane(placeholder, initials);
            Tooltip.install(stack, new Tooltip("Put your photo at src/main/resources/images/profile.png (or .jpg/.jpeg)"));
            photoNode = stack;
        }

        // Luminous glowing halo ring with gold/amber aura
        Circle haloRing = new Circle(size / 2 + 5);
        haloRing.setFill(Color.TRANSPARENT);
        haloRing.setStroke(Color.web("#FFD54F", 0.85));
        haloRing.setStrokeWidth(3.0);
        haloRing.setEffect(new DropShadow(18, Color.web("#FFD54F", 0.85)));

        Circle innerRim = new Circle(size / 2 + 1);
        innerRim.setFill(Color.TRANSPARENT);
        innerRim.setStroke(Color.web("#FFFFFF", 0.5));
        innerRim.setStrokeWidth(1.2);

        StackPane frame = new StackPane(haloRing, innerRim, photoNode);
        frame.getStyleClass().add("photo-frame");
        UiEffects.apply3DTilt(frame);
        return frame;
    }

    private Node logoNode(String basePath, String fallbackText, String badgeColor) {
        Image image = loadImage(basePath);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(30);
            view.setFitHeight(30);
            view.setPreserveRatio(true);
            return view;
        }

        Circle badge = new Circle(16, Color.web(badgeColor));
        Label text = new Label(fallbackText);
        text.getStyleClass().add("logo-fallback");
        return new StackPane(badge, text);
    }

    private static final String[] IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp", "webp"};

    /**
     * Looks for basePath + one of the common image extensions, in that order,
     * on the classpath first and then next to the jar — so you never have to
     * remember or match an exact file name/extension for your own photos.
     * basePath has no extension, e.g. "/images/profile".
     */
    private Image loadImage(String basePath) {
        for (String ext : IMAGE_EXTENSIONS) {
            String path = basePath + "." + ext;
            Image image = loadImageQuietly(path);
            if (image == null) {
                continue;
            }
            if (image.isError()) {
                System.err.println("Found " + path + " but could not decode it: " + image.getException());
                continue;
            }
            return image;
        }
        System.err.println("No image found for " + basePath
                + " with any of these extensions: " + String.join(", ", IMAGE_EXTENSIONS));
        return null;
    }

    private Image loadImageQuietly(String resourcePath) {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return new Image(stream);
            }
        } catch (Exception ignored) {
            // fall through to the file system
        }
        File file = new File(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        if (file.isFile()) {
            return new Image(file.toURI().toString());
        }
        return null;
    }

    private String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && out.length() < 2) {
                out.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return out.length() == 0 ? "?" : out.toString();
    }

    private void openLink(String url) {
        try {
            getHostServices().showDocument(url);
        } catch (Exception e) {
            info("Could not open link", url);
        }
    }

    private void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setMinWidth(420);
        applyStylesheet(alert);
        alert.showAndWait();
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        applyStylesheet(alert);
        return alert.showAndWait().filter(button -> button == ButtonType.YES).isPresent();
    }

    private void applyStylesheet(Dialog<?> dialog) {
        URL css = getClass().getResource("/style.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
            dialog.getDialogPane().getStyleClass().add("app-dialog");
        }
    }

    // -------------------------------------------------------- cursor effect

    private void onCursorMoved(MouseEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastParticleTime < 20) {
            return;
        }
        lastParticleTime = now;
        UiEffects.spawnCursorSpark(cursorLayer, e.getSceneX(), e.getSceneY());
    }

    public static void main(String[] args) {
        launch(args);
    }
}