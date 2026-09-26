package portfolio;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
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
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<Comment> comments = FXCollections.observableArrayList();
    private final BooleanProperty ownerMode = new SimpleBooleanProperty(false);
    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final Map<String, Node> sectionAnchors = new LinkedHashMap<>();

    private Stage stage;
    private VBox mainContent;
    private ScrollPane mainScroller;
    private ScrollPane detailScroller;
    private StackPane loadingOverlay;
    private Pane cursorLayer;

    private VBox projectsBox;
    private VBox researchBox;
    private VBox achievementsBox;
    private VBox commentsBox;
    private Item currentDetailItem;
    private long lastParticleTime = 0;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        profile.load();

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
        Node contactSection = buildContact();
        Node commentsSection = buildCommentsSection("Comments", null, commentsBox);

        sectionAnchors.put("About", about);
        sectionAnchors.put("Projects", projectsSection);
        sectionAnchors.put("Research", researchSection);
        sectionAnchors.put("Achievements", achievementsSection);
        sectionAnchors.put("Donate", donateSection);
        sectionAnchors.put("Contact", contactSection);
        sectionAnchors.put("Comments", commentsSection);

        mainContent = new VBox(30, about, projectsSection, researchSection, achievementsSection,
                donateSection, contactSection, commentsSection);
        mainContent.getStyleClass().add("content");

        mainScroller = new ScrollPane(mainContent);
        mainScroller.setFitToWidth(true);
        mainScroller.getStyleClass().add("scroller");

        detailScroller = new ScrollPane();
        detailScroller.setFitToWidth(true);
        detailScroller.getStyleClass().add("scroller");
        detailScroller.setVisible(false);
        detailScroller.setManaged(false);

        StackPane centerStack = new StackPane(mainScroller, detailScroller);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(new VBox(buildHeader(), buildNavBar()));
        root.setCenter(centerStack);

        cursorLayer = new Pane();
        cursorLayer.setMouseTransparent(true);
        cursorLayer.setPickOnBounds(false);

        loadingOverlay = buildLoadingOverlay();

        StackPane sceneRoot = new StackPane(root, cursorLayer, loadingOverlay);
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
            info("Something went wrong", ex == null ? "Unknown error." : describeError(ex));
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
                "Donate", "Contact", "Comments")) {
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

        return card;
    }

    // ---------------------------------------------------------- detail view

    private void openDetail(Item item) {
        currentDetailItem = item;
        detailScroller.setContent(buildDetailContent(item));
        showDetailView();
    }

    private void showDetailView() {
        mainScroller.setVisible(false);
        mainScroller.setManaged(false);
        detailScroller.setVisible(true);
        detailScroller.setManaged(true);
    }

    private void showMainView() {
        currentDetailItem = null;
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

        if (!item.getAttachmentPath().isBlank()) {
            Path file = ATTACHMENTS_DIR.resolve(item.getAttachmentPath());
            if (Files.exists(file)) {
                if (isImageFile(item.getAttachmentPath())) {
                    ImageView iv = new ImageView(new Image(file.toUri().toString()));
                    iv.setPreserveRatio(true);
                    iv.setFitWidth(480);
                    StackPane frame = new StackPane(iv);
                    frame.getStyleClass().add("attachment-frame");
                    detailCard.getChildren().add(frame);
                } else {
                    Button openFile = new Button("Open attached file (" + item.getAttachmentPath() + ")");
                    openFile.getStyleClass().add("ghost-button");
                    openFile.setOnAction(e -> openLink(file.toUri().toString()));
                    detailCard.getChildren().add(openFile);
                }
            }
        }

        if (!item.getLink().isBlank()) {
            Hyperlink link = new Hyperlink(item.getLink());
            link.getStyleClass().add("card-link");
            link.setOnAction(e -> openLink(item.getLink()));
            detailCard.getChildren().add(link);
        }

        if (ownerMode.get()) {
            Button attach = new Button(item.getAttachmentPath().isBlank()
                    ? "Attach a file (image or PDF)" : "Replace attached file");
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

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    /** Runs the file copy and the database update off the FX thread; see runBackground(...). */
    private void chooseAndAttach(Item item) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image or PDF");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images and PDF", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.pdf"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        runBackground(() -> {
            String stored = storeAttachmentBlocking(chosen);
            item.setAttachmentPath(stored);
            store.updateItem(item);
        }, () -> openDetail(item));
    }

    /**
     * Copies the chosen file into data/attachments. Meant to be called only from
     * inside a background task (see runBackground) since it does blocking I/O.
     */
    private String storeAttachmentBlocking(File source) {
        try {
            Files.createDirectories(ATTACHMENTS_DIR);
            String safeName = System.currentTimeMillis() + "_"
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

        Button chooseFile = new Button("Choose file (optional)");
        Label chosenFileLabel = new Label("No file chosen");
        chosenFileLabel.getStyleClass().add("card-text");
        File[] chosenFile = new File[1];
        chooseFile.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose an image or PDF (optional)");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images and PDF", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.pdf"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File chosen = chooser.showOpenDialog(stage);
            if (chosen != null) {
                chosenFile[0] = chosen;
                chosenFileLabel.setText(chosen.getName());
            }
        });
        HBox fileRow = new HBox(10, chooseFile, chosenFileLabel);
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
            File attachment = chosenFile[0];
            runBackground(() -> {
                if (attachment != null) {
                    item.setAttachmentPath(storeAttachmentBlocking(attachment));
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
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(false);
            Circle clip = new Circle(size / 2, size / 2, size / 2);
            view.setClip(clip);
            StackPane frame = new StackPane(view);
            frame.getStyleClass().add("photo-frame");
            return frame;
        }

        Circle placeholder = new Circle(size / 2, Color.web("#FFFFFF", 0.18));
        placeholder.setStroke(Color.web("#FFFFFF", 0.55));
        placeholder.setStrokeWidth(2);

        Label initials = new Label(initialsOf(profile.getName()));
        initials.getStyleClass().add("photo-initials");

        StackPane stack = new StackPane(placeholder, initials);
        Tooltip.install(stack, new Tooltip("Put your photo at src/main/resources/images/profile.png (or .jpg/.jpeg)"));
        return stack;
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
        if (now - lastParticleTime < 28) {
            return;
        }
        lastParticleTime = now;
        spawnParticle(e.getSceneX(), e.getSceneY());
    }

    private void spawnParticle(double x, double y) {
        boolean gold = Math.random() < 0.4;
        Circle dot = new Circle(x, y, 4, gold ? Color.web("#FFD54F") : Color.web("#FFFFFF"));
        dot.setOpacity(0.85);
        dot.setMouseTransparent(true);
        dot.setEffect(new Glow(0.6));
        cursorLayer.getChildren().add(dot);

        ScaleTransition scale = new ScaleTransition(Duration.millis(650), dot);
        scale.setToX(2.2);
        scale.setToY(2.2);

        FadeTransition fade = new FadeTransition(Duration.millis(650), dot);
        fade.setToValue(0);

        ParallelTransition transition = new ParallelTransition(dot, scale, fade);
        transition.setOnFinished(ev -> cursorLayer.getChildren().remove(dot));
        transition.play();

        if (cursorLayer.getChildren().size() > 60) {
            cursorLayer.getChildren().remove(0);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}