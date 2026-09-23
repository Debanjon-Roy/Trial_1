package portfolio;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private static final String MY_NAME = "Your Name";
    private static final String MY_TAGLINE = "Computer Science Undergraduate · Developer · Researcher";
    private static final String MY_ABOUT =
            "I build software and study problems that sit close to real life. "
                    + "Most of my work is in Java and Python, with a growing interest in "
                    + "applied machine learning for climate and language data. I like projects "
                    + "that ship, papers that answer a narrow question well, and code that the "
                    + "next person can read.";

    private static final String GITHUB_URL = "https://github.com/yourusername";
    private static final String LINKEDIN_URL = "https://www.linkedin.com/in/yourusername";
    private static final String EMAIL = "you@example.com";
    private static final String WHATSAPP_NUMBER = "8801XXXXXXXXX"; // country code, no + and no spaces

    // ------------------------------------------------------------- state
    private final Store store = new Store();
    private final BkashService bkash = new BkashService();
    private final BooleanProperty ownerMode = new SimpleBooleanProperty(false);
    private final StringProperty searchQuery = new SimpleStringProperty("");

    private VBox projectsBox;
    private VBox researchBox;
    private VBox achievementsBox;
    private VBox commentsBox;

    @Override
    public void start(Stage stage) {
        store.load();

        projectsBox = new VBox(14);
        researchBox = new VBox(14);
        achievementsBox = new VBox(14);
        commentsBox = new VBox(12);

        VBox researchSection = buildItemSection("Research Work", researchBox);
        researchSection.getChildren().add(1, buildStatusLegend());

        VBox content = new VBox(30);
        content.getStyleClass().add("content");
        content.getChildren().addAll(
                buildAbout(),
                buildItemSection("Projects", projectsBox),
                researchSection,
                buildItemSection("Achievements", achievementsBox),
                buildDonate(),
                buildContact(),
                buildComments());

        ScrollPane scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("scroller");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(scroller);

        searchQuery.addListener((obs, oldValue, newValue) -> refresh());
        ownerMode.addListener((obs, oldValue, newValue) -> refresh());
        refresh();

        Scene scene = new Scene(root, 1060, 780);
        URL css = getClass().getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

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
            card.getChildren().add(link);
        }

        if (ownerMode.get()) {
            Button delete = new Button("Remove");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(e -> {
                if (confirm("Remove \"" + item.getTitle() + "\"?")) {
                    store.removeItem(item);
                    refresh();
                }
            });
            HBox actions = new HBox(delete);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        }

        card.getStyleClass().add("card");
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
            store.addItem(item);
            refresh();
        });
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
    }

    public static void main(String[] args) {
        launch(args);
    }
}