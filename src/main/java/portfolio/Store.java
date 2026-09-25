package portfolio;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Keeps everything in memory (for the UI to bind against) and mirrors it to a
 * SQLite database file at ./data/portfolio.db. Every add/remove is written
 * through immediately, so there is no separate "save" step to remember to call.
 */
public class Store {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path DB_FILE = DATA_DIR.resolve("portfolio.db");
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_FILE;

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ObservableList<Comment> comments = FXCollections.observableArrayList();

    // Item/Comment don't carry a database id themselves, so we track each in-memory
    // object's row id here (by identity) to know what to UPDATE/DELETE later.
    private final Map<Item, Integer> itemIds = new IdentityHashMap<>();
    private final Map<Comment, Integer> commentIds = new IdentityHashMap<>();

    public ObservableList<Item> items() {
        return items;
    }

    public ObservableList<Comment> comments() {
        return comments;
    }

    public void load() {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }

        try (Connection conn = connect()) {
            createSchema(conn);
            loadItems(conn);
            loadComments(conn);
        } catch (SQLException e) {
            System.err.println("Could not open database: " + e.getMessage());
        }

        if (items.isEmpty()) {
            seedSampleContent();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    private void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(
                    "CREATE TABLE IF NOT EXISTS items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "type TEXT NOT NULL," +
                            "title TEXT NOT NULL," +
                            "description TEXT," +
                            "status TEXT," +
                            "link TEXT," +
                            "year TEXT" +
                            ")");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS comments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "author TEXT NOT NULL," +
                            "text TEXT," +
                            "posted_at TEXT NOT NULL" +
                            ")");
        }
    }

    // ------------------------------------------------------------- loading

    private void loadItems(Connection conn) throws SQLException {
        items.clear();
        itemIds.clear();
        String sql = "SELECT id, type, title, description, status, link, year FROM items ORDER BY id";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    Item.Type type = Item.Type.valueOf(rs.getString("type"));
                    String rawStatus = rs.getString("status");
                    Item.Status status = (rawStatus == null || rawStatus.isBlank())
                            ? null : Item.Status.valueOf(rawStatus);
                    Item item = new Item(type, rs.getString("title"), rs.getString("description"),
                            status, rs.getString("link"), rs.getString("year"));
                    items.add(item);
                    itemIds.put(item, rs.getInt("id"));
                } catch (Exception badRow) {
                    System.err.println("Skipping unreadable item row: " + badRow.getMessage());
                }
            }
        }
    }

    private void loadComments(Connection conn) throws SQLException {
        comments.clear();
        commentIds.clear();
        String sql = "SELECT id, author, text, posted_at FROM comments ORDER BY id DESC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    LocalDateTime when = LocalDateTime.parse(rs.getString("posted_at"));
                    Comment comment = new Comment(rs.getString("author"), rs.getString("text"), when);
                    comments.add(comment);
                    commentIds.put(comment, rs.getInt("id"));
                } catch (Exception badRow) {
                    System.err.println("Skipping unreadable comment row: " + badRow.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------- items

    public void addItem(Item item) {
        String sql = "INSERT INTO items (type, title, description, status, link, year) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getType().name());
            ps.setString(2, item.getTitle());
            ps.setString(3, item.getDescription());
            ps.setString(4, item.getStatus() == null ? null : item.getStatus().name());
            ps.setString(5, item.getLink());
            ps.setString(6, item.getYear());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    itemIds.put(item, keys.getInt(1));
                }
            }
            items.add(item);
        } catch (SQLException e) {
            System.err.println("Could not save item: " + e.getMessage());
        }
    }

    public void removeItem(Item item) {
        Integer id = itemIds.remove(item);
        items.remove(item);
        if (id == null) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not delete item: " + e.getMessage());
        }
    }

    /** Call after changing an existing Item's fields in place, so the edit reaches the database. */
    public void updateItem(Item item) {
        Integer id = itemIds.get(item);
        if (id == null) {
            addItem(item);
            return;
        }
        String sql = "UPDATE items SET type=?, title=?, description=?, status=?, link=?, year=? WHERE id=?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getType().name());
            ps.setString(2, item.getTitle());
            ps.setString(3, item.getDescription());
            ps.setString(4, item.getStatus() == null ? null : item.getStatus().name());
            ps.setString(5, item.getLink());
            ps.setString(6, item.getYear());
            ps.setInt(7, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not update item: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- comments

    public void addComment(Comment comment) {
        String sql = "INSERT INTO comments (author, text, posted_at) VALUES (?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, comment.getAuthor());
            ps.setString(2, comment.getText());
            ps.setString(3, comment.getPostedAt().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    commentIds.put(comment, keys.getInt(1));
                }
            }
            comments.add(0, comment);
        } catch (SQLException e) {
            System.err.println("Could not save comment: " + e.getMessage());
        }
    }

    public void removeComment(Comment comment) {
        Integer id = commentIds.remove(comment);
        comments.remove(comment);
        if (id == null) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM comments WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not delete comment: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- seeding

    private void seedSampleContent() {
        addItem(new Item(Item.Type.PROJECT,
                "Portfolio Desktop App",
                "A JavaFX application that presents my work, research and achievements, "
                        + "with an owner-only editing mode and a bKash donation flow.",
                null,
                "https://github.com/yourusername/portfolio-app",
                "2026"));
        addItem(new Item(Item.Type.PROJECT,
                "Library Management System",
                "Java and MySQL system for issuing, returning and tracking books, "
                        + "with overdue fine calculation and monthly reports.",
                null,
                "",
                "2025"));
        addItem(new Item(Item.Type.RESEARCH,
                "Bangla Text Summarisation with Transformer Models",
                "Comparing fine-tuned multilingual transformers on a hand-annotated "
                        + "Bangla news corpus. Draft submitted to a local conference.",
                Item.Status.COMPLETED,
                "",
                "2025"));
        addItem(new Item(Item.Type.RESEARCH,
                "Flood Prediction for the Khulna Delta",
                "Building a rainfall and tide dataset and testing gradient boosted trees "
                        + "against an LSTM baseline.",
                Item.Status.ONGOING,
                "",
                "2026"));
        addItem(new Item(Item.Type.RESEARCH,
                "Energy Aware Scheduling on Edge Devices",
                "Planned study on scheduling heuristics that trade latency against "
                        + "battery drain on ARM boards.",
                Item.Status.NOT_STARTED,
                "",
                "2026"));
        addItem(new Item(Item.Type.ACHIEVEMENT,
                "Dean's List",
                "Awarded for academic performance in the top decile of the cohort.",
                null,
                "",
                "2025"));
        addItem(new Item(Item.Type.ACHIEVEMENT,
                "Inter-University Hackathon Finalist",
                "Placed in the final six out of ninety teams with a flood alert prototype.",
                null,
                "",
                "2024"));

        addComment(new Comment("Sample Visitor",
                "Great collection of work. Looking forward to the flood prediction paper.",
                LocalDateTime.now()));
    }
}