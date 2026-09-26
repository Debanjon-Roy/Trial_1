package portfolio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A message left by a visitor.
 *
 * itemId is null for a comment left in the general "Comments" section at the
 * bottom of the page, or the id of a specific Item when the comment was left
 * on that project/research paper/achievement's own detail page.
 */
public class Comment {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private int id = -1;
    private final String author;
    private final String text;
    private final LocalDateTime postedAt;
    private Integer itemId;

    public Comment(String author, String text, LocalDateTime postedAt) {
        this(author, text, postedAt, null);
    }

    public Comment(String author, String text, LocalDateTime postedAt, Integer itemId) {
        this.author = author == null || author.isBlank() ? "Anonymous" : author.trim();
        this.text = text == null ? "" : text.trim();
        this.postedAt = postedAt == null ? LocalDateTime.now() : postedAt;
        this.itemId = itemId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public String getPostedAtDisplay() {
        return postedAt.format(DISPLAY);
    }

    /** Null means this is a general, site-wide comment rather than one tied to an item. */
    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public boolean matches(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return true;
        }
        String q = queryText.toLowerCase();
        return author.toLowerCase().contains(q) || text.toLowerCase().contains(q);
    }

    // ------------------------------------------------------- legacy text format
    // Kept only so Store can do a one-time import of the old comments.txt file
    // from before SQLite was added. Not used for ongoing storage any more.

    public String serialize() {
        return String.join("|",
                Item.escape(author),
                Item.escape(text),
                Item.escape(postedAt.toString()));
    }

    public static Comment parse(String line) {
        String[] parts = line.split("\\|", -1);
        String author = Item.unescape(parts[0]);
        String text = parts.length > 1 ? Item.unescape(parts[1]) : "";
        LocalDateTime when = LocalDateTime.now();
        if (parts.length > 2 && !parts[2].isBlank()) {
            try {
                when = LocalDateTime.parse(Item.unescape(parts[2]));
            } catch (Exception ignored) {
                // keep "now" if the stored timestamp is unreadable
            }
        }
        return new Comment(author, text, when);
    }
}