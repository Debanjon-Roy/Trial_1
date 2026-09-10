package portfolio;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps everything in memory and mirrors it to two plain text files inside ./data
 * so nothing is lost between runs. No database and no external library needed.
 */
public class Store {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path ITEMS_FILE = DATA_DIR.resolve("items.txt");
    private static final Path COMMENTS_FILE = DATA_DIR.resolve("comments.txt");

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<Comment> comments = FXCollections.observableArrayList();

    public ObservableList<Item> items() {
        return items;
    }

    public ObservableList<Comment> comments() {
        return comments;
    }

    public void load() {
        items.setAll(readAll(ITEMS_FILE, Item::parse));
        comments.setAll(readAll(COMMENTS_FILE, Comment::parse));
        if (items.isEmpty()) {
            seedSampleContent();
            saveItems();
        }
    }

    public void addItem(Item item) {
        items.add(item);
        saveItems();
    }

    public void removeItem(Item item) {
        items.remove(item);
        saveItems();
    }

    public void addComment(Comment comment) {
        comments.add(0, comment);
        saveComments();
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        saveComments();
    }

    public void saveItems() {
        write(ITEMS_FILE, items.stream().map(Item::serialize).collect(Collectors.toList()));
    }

    public void saveComments() {
        write(COMMENTS_FILE, comments.stream().map(Comment::serialize).collect(Collectors.toList()));
    }

    // ------------------------------------------------------------------ io

    private <T> List<T> readAll(Path path, Function<String, T> parser) {
        List<T> result = new ArrayList<>();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    result.add(parser.apply(line));
                } catch (Exception badRecord) {
                    System.err.println("Skipping unreadable record in " + path + ": " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + path + ": " + e.getMessage());
        }
        return result;
    }

    private void write(Path path, List<String> lines) {
        try {
            Files.createDirectories(DATA_DIR);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write " + path + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- seeding

    private void seedSampleContent() {
        items.addAll(List.of(
                new Item(Item.Type.PROJECT,
                        "Portfolio Desktop App",
                        "A JavaFX application that presents my work, research and achievements, "
                                + "with an owner-only editing mode and a bKash donation flow.",
                        null,
                        "https://github.com/yourusername/portfolio-app",
                        "2026"),
                new Item(Item.Type.PROJECT,
                        "Library Management System",
                        "Java and MySQL system for issuing, returning and tracking books, "
                                + "with overdue fine calculation and monthly reports.",
                        null,
                        "",
                        "2025"),
                new Item(Item.Type.RESEARCH,
                        "Bangla Text Summarisation with Transformer Models",
                        "Comparing fine-tuned multilingual transformers on a hand-annotated "
                                + "Bangla news corpus. Draft submitted to a local conference.",
                        Item.Status.COMPLETED,
                        "",
                        "2025"),
                new Item(Item.Type.RESEARCH,
                        "Flood Prediction for the Khulna Delta",
                        "Building a rainfall and tide dataset and testing gradient boosted trees "
                                + "against an LSTM baseline.",
                        Item.Status.ONGOING,
                        "",
                        "2026"),
                new Item(Item.Type.RESEARCH,
                        "Energy Aware Scheduling on Edge Devices",
                        "Planned study on scheduling heuristics that trade latency against "
                                + "battery drain on ARM boards.",
                        Item.Status.NOT_STARTED,
                        "",
                        "2026"),
                new Item(Item.Type.ACHIEVEMENT,
                        "Dean's List",
                        "Awarded for academic performance in the top decile of the cohort.",
                        null,
                        "",
                        "2025"),
                new Item(Item.Type.ACHIEVEMENT,
                        "Inter-University Hackathon Finalist",
                        "Placed in the final six out of ninety teams with a flood alert prototype.",
                        null,
                        "",
                        "2024")));

        comments.add(new Comment("Sample Visitor",
                "Great collection of work. Looking forward to the flood prediction paper.",
                LocalDateTime.now()));
        saveComments();
    }
}