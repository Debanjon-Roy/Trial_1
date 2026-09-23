package portfolio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A message left by a visitor. */
public class Comment {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final String author;
    private final String text;
    private final LocalDateTime postedAt;

    public Comment(String author, String text, LocalDateTime postedAt) {
        this.author = author == null || author.isBlank() ? "Anonymous" : author.trim();
        this.text = text == null ? "" : text.trim();
        this.postedAt = postedAt == null ? LocalDateTime.now() : postedAt;
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

    public boolean matches(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return true;
        }
        String q = queryText.toLowerCase();
        return author.toLowerCase().contains(q) || text.toLowerCase().contains(q);
    }

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