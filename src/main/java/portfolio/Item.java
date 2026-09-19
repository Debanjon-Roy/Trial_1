package portfolio;

/**
 * One entry in the portfolio. A project, a research paper or an achievement.
 * Status is only meaningful for research papers, but it is stored for every item
 * so the file format stays uniform.
 *
 * id is -1 until the item has been saved to the database, at which point Store
 * fills in the real database id. attachmentPath holds just the file name inside
 * data/attachments (not a full path), or "" when there is no attachment.
 */
public class Item {

    public enum Type {
        PROJECT("Project"),
        RESEARCH("Research Paper"),
        ACHIEVEMENT("Achievement");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Status {
        NOT_STARTED("Not yet started", "status-not-started"),
        ONGOING("Ongoing", "status-ongoing"),
        COMPLETED("Completed", "status-completed");

        private final String label;
        private final String styleClass;

        Status(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }

        public String label() {
            return label;
        }

        public String styleClass() {
            return styleClass;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private int id;
    private Type type;
    private String title;
    private String description;
    private Status status;
    private String link;
    private String year;
    private String attachmentPath;

    /** Convenience constructor for a brand new item that has not been saved yet. */
    public Item(Type type, String title, String description, Status status, String link, String year) {
        this(-1, type, title, description, status, link, year, "");
    }

    public Item(int id, Type type, String title, String description, Status status,
                String link, String year, String attachmentPath) {
        this.id = id;
        this.type = type;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.status = status;
        this.link = link == null ? "" : link;
        this.year = year == null ? "" : year;
        this.attachmentPath = attachmentPath == null ? "" : attachmentPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath == null ? "" : attachmentPath;
    }

    /** Used by the search bar. An empty query matches everything. */
    public boolean matches(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return true;
        }
        String q = queryText.toLowerCase();
        return contains(title, q)
                || contains(description, q)
                || contains(type.label(), q)
                || contains(link, q)
                || contains(year, q)
                || (status != null && contains(status.label(), q));
    }

    private static boolean contains(String value, String lowercaseQuery) {
        return value != null && value.toLowerCase().contains(lowercaseQuery);
    }

    // ------------------------------------------------------- legacy text format
    // Kept only so Store can do a one-time import of the old items.txt file
    // from before SQLite was added. Not used for ongoing storage any more.

    public String serialize() {
        return String.join("|",
                escape(type.name()),
                escape(title),
                escape(description),
                escape(status == null ? "" : status.name()),
                escape(link),
                escape(year));
    }

    public static Item parse(String line) {
        String[] parts = line.split("\\|", -1);
        Type type = Type.valueOf(unescape(parts[0]));
        String title = parts.length > 1 ? unescape(parts[1]) : "";
        String description = parts.length > 2 ? unescape(parts[2]) : "";
        String rawStatus = parts.length > 3 ? unescape(parts[3]) : "";
        Status status = rawStatus.isBlank() ? null : Status.valueOf(rawStatus);
        String link = parts.length > 4 ? unescape(parts[4]) : "";
        String year = parts.length > 5 ? unescape(parts[5]) : "";
        return new Item(type, title, description, status, link, year);
    }

    /** Escapes the field separator and newlines so one record always fits on one line. */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                if (next == 'p') {
                    out.append('|');
                } else if (next == 'n') {
                    out.append('\n');
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}