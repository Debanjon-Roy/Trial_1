package portfolio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure persistence layer backed by a SQLite database at data/portfolio.db.
 *
 * Deliberately holds no ObservableList and touches no UI: every method here
 * does blocking JDBC work and nothing else, which is exactly why PortfolioApp
 * always calls these methods from a background thread (see
 * PortfolioApp.runBackground and Concurrency.java) rather than directly on the
 * JavaFX Application Thread.
 *
 * If data/items.txt or data/comments.txt exist from an older, file-based
 * version of this app, their contents are imported into the database the
 * first time it is created, then left alone.
 */
public class Store {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path DB_FILE = DATA_DIR.resolve("portfolio.db");
    private static final Path LEGACY_ITEMS = DATA_DIR.resolve("items.txt");
    private static final Path LEGACY_COMMENTS = DATA_DIR.resolve("comments.txt");
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Modern sqlite-jdbc versions self-register; this is just a safety net.
        }
    }

    /** Everything read back from the database in one go. */
    public static final class LoadResult {
        public final List<Item> items;
        public final List<Comment> comments;

        LoadResult(List<Item> items, List<Comment> comments) {
            this.items = items;
            this.comments = comments;
        }
    }

    /**
     * Reads (and, on first run, seeds or migrates) everything from the database.
     * This does real disk and SQL work — always call it from a background
     * thread via PortfolioApp.runBackground, never directly on the FX thread.
     */
    public LoadResult load() {
        try {
            Files.createDirectories(DATA_DIR);
            try (Connection c = connect()) {
                createSchema(c);
                migrateLegacyIfNeeded(c);
                LoadResult result = readAllPlain(c);
                if (result.items.isEmpty() && result.comments.isEmpty()) {
                    seedSampleContent(c);
                    result = readAllPlain(c);
                }
                return result;
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Could not open the database at " + DB_FILE, e);
        }
    }

    // -------------------------------------------------------------- mutators
    // Each of these opens its own short-lived connection and is safe to call
    // from any thread; PortfolioApp always calls them from a background thread.

    public void insertItem(Item item) {
        String sql = "INSERT INTO items(type,title,description,status,link,year,attachment_path) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindItem(ps, item);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save the item", e);
        }
    }

    public void updateItem(Item item) {
        String sql = "UPDATE items SET type=?, title=?, description=?, status=?, link=?, year=?, "
                + "attachment_path=? WHERE id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindItem(ps, item);
            ps.setInt(8, item.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update the item", e);
        }
    }

    public void deleteItem(Item item) {
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM comments WHERE item_id=?")) {
                ps.setInt(1, item.getId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM items WHERE id=?")) {
                ps.setInt(1, item.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete the item", e);
        }
    }

    public void insertComment(Comment comment) {
        String sql = "INSERT INTO comments(item_id, author, text, posted_at) VALUES (?,?,?,?)";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (comment.getItemId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, comment.getItemId());
            }
            ps.setString(2, comment.getAuthor());
            ps.setString(3, comment.getText());
            ps.setString(4, comment.getPostedAt().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    comment.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save the comment", e);
        }
    }

    public void deleteComment(Comment comment) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM comments WHERE id=?")) {
            ps.setInt(1, comment.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete the comment", e);
        }
    }

    // ------------------------------------------------------------------ setup

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "type TEXT NOT NULL,"
                    + "title TEXT NOT NULL,"
                    + "description TEXT,"
                    + "status TEXT,"
                    + "link TEXT,"
                    + "year TEXT,"
                    + "attachment_path TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS comments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "item_id INTEGER,"
                    + "author TEXT,"
                    + "text TEXT,"
                    + "posted_at TEXT)");
        }
        // Covers databases created by an earlier version of this schema (e.g. before
        // attachments existed): add any column that CREATE TABLE IF NOT EXISTS would
        // have skipped because the table already existed without it.
        addColumnIfMissing(c, "items", "attachment_path", "TEXT");
        addColumnIfMissing(c, "comments", "item_id", "INTEGER");
    }

    private void addColumnIfMissing(Connection c, String table, String column, String sqlType) throws SQLException {
        if (columnExists(c, table, column)) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
        }
    }

    private boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void bindItem(PreparedStatement ps, Item item) throws SQLException {
        ps.setString(1, item.getType().name());
        ps.setString(2, item.getTitle());
        ps.setString(3, item.getDescription());
        ps.setString(4, item.getStatus() == null ? null : item.getStatus().name());
        ps.setString(5, item.getLink());
        ps.setString(6, item.getYear());
        ps.setString(7, item.getAttachmentPath());
    }

    private LoadResult readAllPlain(Connection c) throws SQLException {
        List<Item> itemList = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM items ORDER BY id")) {
            while (rs.next()) {
                Item.Type type = Item.Type.valueOf(rs.getString("type"));
                String statusStr = rs.getString("status");
                Item.Status status = statusStr == null ? null : Item.Status.valueOf(statusStr);
                itemList.add(new Item(rs.getInt("id"), type, rs.getString("title"), rs.getString("description"),
                        status, rs.getString("link"), rs.getString("year"), rs.getString("attachment_path")));
            }
        }
        List<Comment> commentList = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM comments ORDER BY id DESC")) {
            while (rs.next()) {
                int rawItemId = rs.getInt("item_id");
                Integer itemId = rs.wasNull() ? null : rawItemId;
                LocalDateTime when;
                try {
                    when = LocalDateTime.parse(rs.getString("posted_at"));
                } catch (Exception e) {
                    when = LocalDateTime.now();
                }
                Comment comment = new Comment(rs.getString("author"), rs.getString("text"), when, itemId);
                comment.setId(rs.getInt("id"));
                commentList.add(comment);
            }
        }
        return new LoadResult(itemList, commentList);
    }

    // ---------------------------------------------------- one-time migration

    private void migrateLegacyIfNeeded(Connection c) throws SQLException {
        if (countRows(c, "items") > 0) {
            return;
        }
        if (Files.exists(LEGACY_ITEMS)) {
            for (String line : readLinesQuietly(LEGACY_ITEMS)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    insertItemRaw(c, Item.parse(line));
                } catch (Exception badRecord) {
                    System.err.println("Skipping unreadable legacy item: " + line);
                }
            }
        }
        if (Files.exists(LEGACY_COMMENTS)) {
            for (String line : readLinesQuietly(LEGACY_COMMENTS)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    insertCommentRaw(c, Comment.parse(line));
                } catch (Exception badRecord) {
                    System.err.println("Skipping unreadable legacy comment: " + line);
                }
            }
        }
    }

    private int countRows(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<String> readLinesQuietly(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    private void insertItemRaw(Connection c, Item item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO items(type,title,description,status,link,year,attachment_path) "
                        + "VALUES (?,?,?,?,?,?,?)")) {
            bindItem(ps, item);
            ps.executeUpdate();
        }
    }

    private void insertCommentRaw(Connection c, Comment comment) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO comments(item_id, author, text, posted_at) VALUES (?,?,?,?)")) {
            if (comment.getItemId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, comment.getItemId());
            }
            ps.setString(2, comment.getAuthor());
            ps.setString(3, comment.getText());
            ps.setString(4, comment.getPostedAt().toString());
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------- seeding

    private void seedSampleContent(Connection c) throws SQLException {
        insertItemRaw(c, new Item(Item.Type.PROJECT,
                "Portfolio Desktop App",
                "A JavaFX application that presents my work, research and achievements, "
                        + "with an owner-only editing mode and a bKash donation flow.",
                null, "https://github.com/yourusername/portfolio-app", "2026"));
        insertItemRaw(c, new Item(Item.Type.PROJECT,
                "Library Management System",
                "Java and MySQL system for issuing, returning and tracking books, "
                        + "with overdue fine calculation and monthly reports.",
                null, "", "2025"));
        insertItemRaw(c, new Item(Item.Type.RESEARCH,
                "Bangla Text Summarisation with Transformer Models",
                "Comparing fine-tuned multilingual transformers on a hand-annotated "
                        + "Bangla news corpus. Draft submitted to a local conference.",
                Item.Status.COMPLETED, "", "2025"));
        insertItemRaw(c, new Item(Item.Type.RESEARCH,
                "Flood Prediction for the Khulna Delta",
                "Building a rainfall and tide dataset and testing gradient boosted trees "
                        + "against an LSTM baseline.",
                Item.Status.ONGOING, "", "2026"));
        insertItemRaw(c, new Item(Item.Type.RESEARCH,
                "Energy Aware Scheduling on Edge Devices",
                "Planned study on scheduling heuristics that trade latency against "
                        + "battery drain on ARM boards.",
                Item.Status.NOT_STARTED, "", "2026"));
        insertItemRaw(c, new Item(Item.Type.ACHIEVEMENT,
                "Dean's List",
                "Awarded for academic performance in the top decile of the cohort.",
                null, "", "2025"));
        insertItemRaw(c, new Item(Item.Type.ACHIEVEMENT,
                "Inter-University Hackathon Finalist",
                "Placed in the final six out of ninety teams with a flood alert prototype.",
                null, "", "2024"));
        insertCommentRaw(c, new Comment("Sample Visitor",
                "Great collection of work. Looking forward to the flood prediction paper.",
                LocalDateTime.now()));
    }
}