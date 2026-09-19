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
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Personal portfolio desktop app.
 *
 * Sections: About, Projects, Research, Achievements, Donate, Contact, Comments.
 * The "+" button is gated behind a password so only the owner can add entries.
 */
public class PortfolioApp extends Application {

    // ----------------------------------------------------- personal details
    // Edit these six lines and the app is yours.
    private static final String MY_NAME = "Debanjon Roy";
    private static final String MY_TAGLINE = "Computer Science Undergraduate · Developer · Researcher";
    private static final String MY_ABOUT =
            "I build software and study problems that sit close to real life. "
                    + "Most of my work is in C++, Dart and Python, with a growing interest in "
                    + "applied machine learning and language data and developing desktop and mobile apps. I like projects "
                    + "that ship, papers that answer a narrow question well, and code that the "
                    + "next person can read.";

    private static final String GITHUB_URL = "https://github.com/Debanjon-Roy";
    private static final String LINKEDIN_URL = "https://www.linkedin.com/in/yourusername";
    private static final String EMAIL = "roydebanjon2004@gmail.com";
    private static final String WHATSAPP_NUMBER = "8801741816336"; // country code, no + and no spaces

    private static final Path ATTACHMENTS_DIR = Paths.get("data", "attachments");

    // ------------------------------------------------------------- state
    private final Store store = new Store();
    private final BkashService bkash = new BkashService();
    private final BooleanProperty ownerMode = new SimpleBooleanProperty(false);
    private final StringProperty searchQuery = new SimpleStringProperty("");

    private VBox projectsBox;
    private VBox researchBox;
    private VBox achievementsBox;
    private VBox commentsBox;

    private VBox content;
    private ScrollPane scroller;

    // Kept so a detail page can swap itself into the window and back out again.
    private Stage stage;
    private Scene scene;
    private BorderPane mainRoot;

    // The current page (main or a detail page) lives inside pageHost; cursorLayer sits on
    // top of it always, so the cursor trail survives swapping between pages.
    private StackPane pageHost;
    private Pane cursorLayer;
    private long lastTrailSpawn;

    // Set while a detail page is open, so an edit made from that page can redraw it immediately.
    private Item currentDetailItem;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        store.load();

        projectsBox = new VBox(14);
        researchBox = new VBox(14);
        achievementsBox = new VBox(14);
        commentsBox = new VBox(12);

        VBox researchSection = buildItemSection("Research Work", researchBox, Item.Type.RESEARCH);
        researchSection.getChildren().add(1, buildStatusLegend());

        Node aboutSection = buildAbout();
        Node projectsSection = buildItemSection("Projects", projectsBox, Item.Type.PROJECT);
        Node achievementsSection = buildItemSection("Achievements", achievementsBox, Item.Type.ACHIEVEMENT);
        Node donateSection = buildDonate();
        Node contactSection = buildContact();
        Node commentsSection = buildComments();

        content = new VBox(30);
        content.getStyleClass().add("content");
        content.getChildren().addAll(
                aboutSection,
                projectsSection,
                researchSection,
                achievementsSection,
                donateSection,
                contactSection,
                commentsSection);

        scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("scroller");

        Node navBar = buildNavBar(
                aboutSection, projectsSection, researchSection,
                achievementsSection, donateSection, contactSection, commentsSection);
        VBox topBar = new VBox(buildHeader(), navBar);

        mainRoot = new BorderPane();
        mainRoot.getStyleClass().add("root-pane");
        mainRoot.setTop(topBar);
        mainRoot.setCenter(scroller);

        searchQuery.addListener((obs, oldValue, newValue) -> refresh());
        ownerMode.addListener((obs, oldValue, newValue) -> refresh());
        refresh();

        pageHost = new StackPane(mainRoot);

        cursorLayer = new Pane();
        cursorLayer.setMouseTransparent(true);

        StackPane sceneRoot = new StackPane(pageHost, cursorLayer);

        scene = new Scene(sceneRoot, 1060, 780);
        URL css = getClass().getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        scene.setOnMouseMoved(e -> spawnCursorGlow(e.getSceneX(), e.getSceneY()));
        scene.setOnMouseClicked(e -> spawnClickRipple(e.getSceneX(), e.getSceneY()));

        stage.setTitle(MY_NAME + " — Portfolio");
        stage.setMinWidth(860);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------- header

    private Node buildHeader() {
        Label brand = new Label(MY_NAME);
        brand.getStyleClass().add("brand");

        TextField search = new TextField();
        search.setPromptText("Search projects, research, achievements, comments…");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(360);
        searchQuery.bind(search.textProperty());

        Button clear = new Button("✕");
        clear.getStyleClass().add("clear-button");
        clear.setTooltip(new Tooltip("Clear search"));
        clear.setOnAction(e -> search.clear());
        clear.visibleProperty().bind(search.textProperty().isEmpty().not());
        clear.managedProperty().bind(clear.visibleProperty());

        Button add = new Button("+");
        add.getStyleClass().add("add-button");
        add.setTooltip(new Tooltip("Add a project, research paper or achievement"));
        add.setOnAction(e -> onAddClicked());
        add.visibleProperty().bind(ownerMode);
        add.managedProperty().bind(ownerMode);

        Button changePassword = new Button("Change password");
        changePassword.getStyleClass().add("ghost-button");
        changePassword.visibleProperty().bind(ownerMode);
        changePassword.managedProperty().bind(ownerMode);
        changePassword.setOnAction(e -> showChangePasswordDialog());

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

        HBox header = new HBox(10, brand, badge, spacer, search, clear, add, changePassword, lock);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    // ------------------------------------------------------------ nav bar

    private Node buildNavBar(Node about, Node projects, Node research,
                             Node achievements, Node donate, Node contact, Node comments) {
        HBox nav = new HBox(4,
                navButton("About", about),
                navButton("Projects", projects),
                navButton("Research", research),
                navButton("Achievements", achievements),
                navButton("Donate", donate),
                navButton("Contact", contact),
                navButton("Comments", comments));
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("nav-bar");
        return nav;
    }

    private Button navButton(String label, Node target) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-button");
        button.setOnAction(e -> scrollTo(target));
        return button;
    }

    /** Smoothly scrolls the page so the given section lands at the top of the viewport. */
    private void scrollTo(Node target) {
        if (target == null || scroller == null || content == null) {
            return;
        }
        // Force a layout pass first, since a recent search or owner-mode toggle
        // may have changed how tall the content is since it was last measured.
        content.applyCss();
        content.layout();

        Bounds targetBounds = content.sceneToLocal(target.localToScene(target.getBoundsInLocal()));
        double scrollableHeight = content.getHeight() - scroller.getViewportBounds().getHeight();
        double targetVvalue = scrollableHeight <= 0
                ? 0
                : clamp(targetBounds.getMinY() / scrollableHeight, 0, 1);

        Timeline animation = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(scroller.vvalueProperty(), targetVvalue, Interpolator.EASE_BOTH)));
        animation.play();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // -------------------------------------------------------------- about

    private Node buildAbout() {
        Node photo = circularPhoto("/images/profile.jpeg", 130);

        Label name = new Label(MY_NAME);
        name.getStyleClass().add("hero-name");

        Label tagline = new Label(MY_TAGLINE);
        tagline.getStyleClass().add("hero-tagline");
        tagline.setWrapText(true);

        Label about = new Label(MY_ABOUT);
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

    private VBox buildItemSection(String title, VBox listBox, Item.Type type) {
        VBox section = new VBox(14);
        section.getStyleClass().add("section");

        Label heading = sectionTitle(title);

        Button addButton = new Button("+ Add");
        addButton.getStyleClass().add("ghost-button");
        addButton.visibleProperty().bind(ownerMode);
        addButton.managedProperty().bind(ownerMode);
        addButton.setOnAction(e -> {
            if (!ownerMode.get() && !requestLogin()) {
                return;
            }
            showItemDialog(null, type);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerRow = new HBox(10, heading, spacer, addButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(headerRow, listBox);
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

        VBox card = new VBox(8, title);
        if (!meta.getChildren().isEmpty()) {
            card.getChildren().add(meta);
        }
        card.getChildren().add(description);

        if (!item.getLink().isBlank()) {
            Hyperlink link = new Hyperlink(item.getLink());
            link.getStyleClass().add("card-link");
            link.setOnAction(e -> openLink(item.getLink()));
            link.addEventHandler(MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
            card.getChildren().add(link);
        }

        if (ownerMode.get()) {
            Button edit = new Button("Edit");
            edit.getStyleClass().add("ghost-button");
            edit.setOnAction(e -> showItemDialog(item, null));
            edit.addEventHandler(MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);

            Button delete = new Button("Remove");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(e -> {
                if (confirm("Remove \"" + item.getTitle() + "\"?")) {
                    store.removeItem(item);
                    refresh();
                }
            });
            delete.addEventHandler(MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);

            HBox actions = new HBox(8, edit, delete);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        }

        Label hint = new Label("View details →");
        hint.getStyleClass().add("card-hint");
        card.getChildren().add(hint);

        card.getStyleClass().addAll("card", "card-clickable");
        card.setOnMouseClicked(e -> showItemDetail(item));
        return card;
    }

    // -------------------------------------------------------- detail page

    /** Swaps the window's content for a full page about one item, with attachments and its own comments. */
    private void showItemDetail(Item item) {
        Node header = buildDetailHeader();
        Node body = buildDetailBody(item);

        ScrollPane detailScroller = new ScrollPane(body);
        detailScroller.setFitToWidth(true);
        detailScroller.getStyleClass().add("scroller");

        BorderPane detailRoot = new BorderPane();
        detailRoot.getStyleClass().add("root-pane");
        detailRoot.setTop(header);
        detailRoot.setCenter(detailScroller);

        pageHost.getChildren().setAll(detailRoot);
        currentDetailItem = item;
    }

    /** Swaps the window's content back to the main page. */
    private void showMain() {
        pageHost.getChildren().setAll(mainRoot);
        currentDetailItem = null;
        refresh();
    }

    /** Rebuilds the detail page in place if it's the one currently open, so an edit shows up right away. */
    private void refreshDetailIfShowing(Item item) {
        if (currentDetailItem == item) {
            showItemDetail(item);
        }
    }

    // ------------------------------------------------------- cursor effects

    /** Drops a small glowing dot at the cursor that quickly fades and shrinks, leaving a trail. */
    private void spawnCursorGlow(double sceneX, double sceneY) {
        long now = System.currentTimeMillis();
        if (now - lastTrailSpawn < 35) {
            return;
        }
        lastTrailSpawn = now;

        Circle dot = new Circle(4, Color.web("#FFD54F", 0.85));
        dot.setEffect(new Glow(0.8));
        dot.setLayoutX(sceneX);
        dot.setLayoutY(sceneY);
        cursorLayer.getChildren().add(dot);

        FadeTransition fade = new FadeTransition(Duration.millis(550), dot);
        fade.setFromValue(0.85);
        fade.setToValue(0);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(550), dot);
        shrink.setFromX(1);
        shrink.setFromY(1);
        shrink.setToX(0.2);
        shrink.setToY(0.2);

        ParallelTransition trail = new ParallelTransition(dot, fade, shrink);
        trail.setOnFinished(e -> cursorLayer.getChildren().remove(dot));
        trail.play();
    }

    /** Expands a soft ring outward from wherever the user clicks. */
    private void spawnClickRipple(double sceneX, double sceneY) {
        Circle ring = new Circle(6, Color.TRANSPARENT);
        ring.setStroke(Color.web("#FFFFFF", 0.85));
        ring.setStrokeWidth(2);
        ring.setLayoutX(sceneX);
        ring.setLayoutY(sceneY);
        cursorLayer.getChildren().add(ring);

        ScaleTransition grow = new ScaleTransition(Duration.millis(450), ring);
        grow.setFromX(0.2);
        grow.setFromY(0.2);
        grow.setToX(4);
        grow.setToY(4);
        grow.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(450), ring);
        fade.setFromValue(0.85);
        fade.setToValue(0);

        ParallelTransition ripple = new ParallelTransition(ring, grow, fade);
        ripple.setOnFinished(e -> cursorLayer.getChildren().remove(ring));
        ripple.play();
    }

    private Node buildDetailHeader() {
        Button back = new Button("← Back");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> showMain());

        Label brand = new Label(MY_NAME);
        brand.getStyleClass().add("brand");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, back, spacer, brand);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildDetailBody(Item item) {
        Label title = new Label(item.getTitle());
        title.getStyleClass().add("hero-name");
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

        Label description = new Label(item.getDescription());
        description.getStyleClass().add("hero-about");
        description.setWrapText(true);

        Button editButton = new Button("Edit");
        editButton.getStyleClass().add("ghost-button");
        editButton.visibleProperty().bind(ownerMode);
        editButton.managedProperty().bind(ownerMode);
        editButton.setOnAction(e -> showItemDialog(item, null));

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, titleSpacer, editButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(12, titleRow, meta, description);
        if (!item.getLink().isBlank()) {
            Hyperlink link = new Hyperlink(item.getLink());
            link.getStyleClass().add("card-link");
            link.setOnAction(e -> openLink(item.getLink()));
            infoBox.getChildren().add(link);
        }
        VBox infoSection = new VBox(infoBox);
        infoSection.getStyleClass().addAll("section", "hero");

        VBox attachmentsSection = new VBox(16,
                sectionTitle("Attachments"),
                new Label("Photo"),
                buildDetailPhoto(item),
                new Label("PDF"),
                buildDetailPdf(item));
        attachmentsSection.getStyleClass().add("section");

        VBox body = new VBox(24, infoSection, attachmentsSection, buildItemCommentsSection(item));
        body.getStyleClass().add("content");
        return body;
    }

    private Node buildDetailPhoto(Item item) {
        File file = resolveAttachment(item.getImagePath());
        if (file != null && file.isFile()) {
            ImageView view = new ImageView(new Image(file.toURI().toString()));
            view.setFitWidth(360);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            StackPane frame = new StackPane(view);
            frame.getStyleClass().add("photo-frame");
            return frame;
        }
        Label empty = new Label("Empty");
        empty.getStyleClass().add("empty-label");
        return empty;
    }

    private Node buildDetailPdf(Item item) {
        File file = resolveAttachment(item.getPdfPath());
        if (file != null && file.isFile()) {
            Button open = new Button("Open attached PDF");
            open.getStyleClass().add("primary-button");
            open.setOnAction(e -> openLink(file.toURI().toString()));
            return open;
        }
        Label empty = new Label("Empty");
        empty.getStyleClass().add("empty-label");
        return empty;
    }

    /** Null/blank path means nothing was attached; otherwise resolves it relative to the app's working directory. */
    private File resolveAttachment(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        return new File(storedPath);
    }

    // ------------------------------------------------------ item comments

    private Node buildItemCommentsSection(Item item) {
        VBox listBox = new VBox(12);
        refreshItemComments(item, listBox);

        TextField author = new TextField();
        author.setPromptText("Your name");
        author.setPrefWidth(200);
        author.getStyleClass().add("input");

        TextArea body = new TextArea();
        body.setPromptText("Leave a comment on this " + item.getType().label().toLowerCase() + "…");
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
            if (item.getId() == null) {
                info("Not saved yet", "This item hasn't finished saving. Please try again in a moment.");
                return;
            }
            store.addComment(new Comment(author.getText(), body.getText(), LocalDateTime.now(), item.getId()));
            author.clear();
            body.clear();
            refreshItemComments(item, listBox);
        });

        HBox actions = new HBox(10, author, post);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14, sectionTitle("Comments"), body, actions, listBox);
        section.getStyleClass().add("section");
        return section;
    }

    private void refreshItemComments(Item item, VBox listBox) {
        listBox.getChildren().clear();
        List<Comment> matching = store.comments().stream()
                .filter(c -> item.getId() != null && item.getId().equals(c.getItemId()))
                .collect(Collectors.toList());
        if (matching.isEmpty()) {
            listBox.getChildren().add(emptyLabel(null, "comments"));
            return;
        }
        for (Comment comment : matching) {
            listBox.getChildren().add(itemCommentCard(item, comment, listBox));
        }
    }

    private Node itemCommentCard(Item item, Comment comment, VBox listBox) {
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
                    store.removeComment(comment);
                    refreshItemComments(item, listBox);
                }
            });
            HBox row = new HBox(delete);
            row.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(row);
        }
        return card;
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

        grid.add(contactRow("/images/github.png", "GH", "#24292E",
                "GitHub", GITHUB_URL, GITHUB_URL), 0, 0);
        grid.add(contactRow("/images/linkedin.png", "in", "#0A66C2",
                "LinkedIn", LINKEDIN_URL, LINKEDIN_URL), 1, 0);
        grid.add(contactRow("/images/email.png", "@", "#D93025",
                "Email", EMAIL, "mailto:" + EMAIL), 0, 1);
        grid.add(contactRow("/images/whatsapp.png", "W", "#25D366",
                "WhatsApp", "+" + WHATSAPP_NUMBER, "https://wa.me/" + WHATSAPP_NUMBER), 1, 1);

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
        row.setCursor(javafx.scene.Cursor.HAND);
        return row;
    }

    // ----------------------------------------------------------- comments

    private Node buildComments() {
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
            store.addComment(new Comment(author.getText(), body.getText(), LocalDateTime.now()));
            author.clear();
            body.clear();
            refresh();
        });

        HBox actions = new HBox(10, author, post);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(14,
                sectionTitle("Comments"),
                body,
                actions,
                commentsBox);
        section.getStyleClass().add("section");
        return section;
    }

    private Node commentCard(Comment comment) {
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
                    store.removeComment(comment);
                    refresh();
                }
            });
            HBox row = new HBox(delete);
            row.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(row);
        }
        return card;
    }

    // ------------------------------------------------------------ refresh

    private void refresh() {
        fillItems(projectsBox, Item.Type.PROJECT);
        fillItems(researchBox, Item.Type.RESEARCH);
        fillItems(achievementsBox, Item.Type.ACHIEVEMENT);
        fillComments();
    }

    private void fillItems(VBox box, Item.Type type) {
        box.getChildren().clear();
        String query = searchQuery.get();
        List<Item> matching = store.items().stream()
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

    private void fillComments() {
        commentsBox.getChildren().clear();
        String query = searchQuery.get();
        List<Comment> matching = store.comments().stream()
                .filter(comment -> comment.getItemId() == null)
                .filter(comment -> comment.matches(query))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            commentsBox.getChildren().add(emptyLabel(query, "comments"));
            return;
        }
        for (Comment comment : matching) {
            commentsBox.getChildren().add(commentCard(comment));
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
        showItemDialog(null, null);
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

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == null) {
            return false;
        }
        if (Auth.verify(result.get())) {
            ownerMode.set(true);
            return true;
        }
        info("Login failed", "That password is not correct.");
        return false;
    }

    private void showChangePasswordDialog() {
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");

        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");

        VBox content = new VBox(8,
                new Label("Current password"), current,
                new Label("New password"), newPassword,
                new Label("Confirm new password"), confirm);
        content.setPadding(new Insets(6, 0, 0, 0));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change password");
        dialog.setHeaderText("Set a new owner password");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button);
        applyStylesheet(dialog);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        if (!Auth.verify(current.getText())) {
            info("Incorrect password", "Your current password is incorrect. Nothing was changed.");
            return;
        }
        if (newPassword.getText() == null || newPassword.getText().isBlank()) {
            info("Password required", "Please enter a new password. Nothing was changed.");
            return;
        }
        if (!newPassword.getText().equals(confirm.getText())) {
            info("Passwords don't match", "The new password and its confirmation must match. Nothing was changed.");
            return;
        }

        if (Auth.changePassword(newPassword.getText())) {
            info("Password changed", "Your owner password has been updated.");
        } else {
            info("Could not save", "The new password could not be saved. "
                    + "Check that the app can write to the data folder.");
        }
    }

    /**
     * Shared Add/Edit dialog. existing == null adds a new entry; otherwise the form is pre-filled
     * from it and OK updates it in place instead of creating a new one. presetType only applies
     * when existing is null (e.g. opened from a specific section's "+ Add"); it's ignored otherwise
     * since an existing item's own type is used instead.
     */
    private void showItemDialog(Item existing, Item.Type presetType) {
        boolean editing = existing != null;

        ComboBox<Item.Type> type = new ComboBox<>(FXCollections.observableArrayList(Item.Type.values()));
        type.setValue(editing ? existing.getType() : (presetType != null ? presetType : Item.Type.PROJECT));
        type.setMaxWidth(Double.MAX_VALUE);

        TextField title = new TextField(editing ? existing.getTitle() : "");
        title.setPromptText("Title");

        TextArea description = new TextArea(editing ? existing.getDescription() : "");
        description.setPromptText("Short description");
        description.setPrefRowCount(4);
        description.setWrapText(true);

        ComboBox<Item.Status> status =
                new ComboBox<>(FXCollections.observableArrayList(Item.Status.values()));
        status.setValue(editing && existing.getStatus() != null ? existing.getStatus() : Item.Status.NOT_STARTED);
        status.setMaxWidth(Double.MAX_VALUE);

        Label statusLabel = new Label("Status");
        status.visibleProperty().bind(type.valueProperty().isEqualTo(Item.Type.RESEARCH));
        status.managedProperty().bind(status.visibleProperty());
        statusLabel.visibleProperty().bind(status.visibleProperty());
        statusLabel.managedProperty().bind(status.visibleProperty());

        TextField link = new TextField(editing ? existing.getLink() : "");
        link.setPromptText("https://… (optional)");

        TextField year = new TextField(editing ? existing.getYear() : "");
        year.setPromptText("2026 (optional)");

        HBox photoRow = buildAttachmentRow(
                editing ? existing.getImagePath() : "",
                "Choose photo…",
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File[] chosenPhoto = (File[]) photoRow.getProperties().get("chosen");
        boolean[] removePhoto = (boolean[]) photoRow.getProperties().get("remove");

        HBox pdfRow = buildAttachmentRow(
                editing ? existing.getPdfPath() : "",
                "Choose PDF…",
                new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File[] chosenPdf = (File[]) pdfRow.getProperties().get("chosen");
        boolean[] removePdf = (boolean[]) pdfRow.getProperties().get("remove");

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
        form.addRow(6, new Label("Photo"), photoRow);
        form.addRow(7, new Label("PDF"), pdfRow);

        Dialog<Item> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit entry" : "Add entry");
        dialog.setHeaderText(editing
                ? "Editing \"" + existing.getTitle() + "\""
                : "New project, research paper or achievement");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStylesheet(dialog);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(title.getText().isBlank());
        title.textProperty().addListener((obs, oldValue, newValue) ->
                okButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Item.Status chosenStatus = type.getValue() == Item.Type.RESEARCH ? status.getValue() : null;
            String imagePath = resolveAttachmentPath(
                    chosenPhoto[0], removePhoto[0], editing ? existing.getImagePath() : "", "images");
            String pdfPath = resolveAttachmentPath(
                    chosenPdf[0], removePdf[0], editing ? existing.getPdfPath() : "", "pdfs");

            if (editing) {
                existing.setType(type.getValue());
                existing.setTitle(title.getText().trim());
                existing.setDescription(description.getText().trim());
                existing.setStatus(chosenStatus);
                existing.setLink(link.getText().trim());
                existing.setYear(year.getText().trim());
                existing.setImagePath(imagePath);
                existing.setPdfPath(pdfPath);
                return existing;
            }
            return new Item(type.getValue(), title.getText().trim(), description.getText().trim(),
                    chosenStatus, link.getText().trim(), year.getText().trim(), imagePath, pdfPath);
        });

        dialog.showAndWait().ifPresent(item -> {
            if (editing) {
                store.updateItem(item);
            } else {
                store.addItem(item);
            }
            refresh();
            refreshDetailIfShowing(item);
        });
    }

    /**
     * Builds a "Choose file… [Remove] current-or-chosen-name" row for the Add/Edit dialog. The
     * File[1] and boolean[1] arrays tracking the user's choice are stashed on the row's own
     * properties map (under "chosen"/"remove") so the caller can read them back after the dialog
     * closes, without needing three near-identical private fields per attachment type.
     */
    private HBox buildAttachmentRow(String existingPath, String chooseLabel, FileChooser.ExtensionFilter filter) {
        boolean hasExisting = existingPath != null && !existingPath.isBlank();

        File[] chosen = new File[1];
        boolean[] remove = new boolean[1];

        Label label = new Label(hasExisting ? "Current: " + new File(existingPath).getName() : "None attached");
        label.getStyleClass().add("card-text");

        Button chooseButton = new Button(chooseLabel);
        chooseButton.getStyleClass().add("ghost-button");

        Button removeButton = new Button("Remove");
        removeButton.getStyleClass().add("danger-button");
        removeButton.setVisible(hasExisting);
        removeButton.setManaged(hasExisting);

        chooseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(chooseLabel.replace("…", ""));
            chooser.getExtensionFilters().add(filter);
            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                chosen[0] = selected;
                remove[0] = false;
                label.setText(selected.getName());
                removeButton.setVisible(true);
                removeButton.setManaged(true);
            }
        });

        removeButton.setOnAction(e -> {
            chosen[0] = null;
            remove[0] = true;
            label.setText("None attached");
            removeButton.setVisible(false);
            removeButton.setManaged(false);
        });

        HBox row = new HBox(10, chooseButton, removeButton, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getProperties().put("chosen", chosen);
        row.getProperties().put("remove", remove);
        return row;
    }

    /** Newly chosen file wins; otherwise "removed" clears it; otherwise the existing path is kept as-is. */
    private String resolveAttachmentPath(File chosen, boolean removed, String existingPath, String subfolder) {
        if (chosen != null) {
            return copyAttachment(chosen, subfolder);
        }
        if (removed) {
            return "";
        }
        return existingPath == null ? "" : existingPath;
    }

    /**
     * Copies a chosen file into ./data/attachments/{subfolder} under a unique name, so the entry
     * still works even if the original file is later moved or deleted. Returns "" on failure.
     */
    private String copyAttachment(File source, String subfolder) {
        try {
            Path targetDir = ATTACHMENTS_DIR.resolve(subfolder);
            Files.createDirectories(targetDir);
            String safeName = UUID.randomUUID() + "-" + source.getName();
            Path target = targetDir.resolve(safeName);
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            info("Could not attach file", "Could not copy \"" + source.getName() + "\": " + e.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------- helpers

    private Node circularPhoto(String resourcePath, double size) {
        Image image = loadImage(resourcePath);
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

        Label initials = new Label(initialsOf(MY_NAME));
        initials.getStyleClass().add("photo-initials");

        StackPane stack = new StackPane(placeholder, initials);
        Tooltip.install(stack, new Tooltip("Put your photo at src/main/resources/images/profile.png"));
        return stack;
    }

    private Node logoNode(String resourcePath, String fallbackText, String badgeColor) {
        Image image = loadImage(resourcePath);
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

    /** Looks on the classpath first, then next to the jar, so either layout works. */
    private Image loadImage(String resourcePath) {
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
        animateDialogIn(dialog);
    }

    /** Fades and scales the dialog pane in the moment the dialog becomes visible. */
    private void animateDialogIn(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.setOpacity(0);
        pane.setScaleX(0.92);
        pane.setScaleY(0.92);

        dialog.setOnShown(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(220), pane);
            fade.setFromValue(0);
            fade.setToValue(1);

            ScaleTransition scale = new ScaleTransition(Duration.millis(220), pane);
            scale.setFromX(0.92);
            scale.setFromY(0.92);
            scale.setToX(1);
            scale.setToY(1);
            scale.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(pane, fade, scale).play();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}