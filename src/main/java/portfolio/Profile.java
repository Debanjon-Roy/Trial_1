package portfolio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Your personal details and owner password, kept in data/profile.properties —
 * a plain text file outside the Java source, so future updates to the app's
 * code never touch it. Edit that file directly with any text editor; there is
 * nothing to change in PortfolioApp.java for this any more.
 *
 * The file is created automatically, filled with placeholders, the first time
 * the app runs and doesn't find one. After that, this class only ever reads
 * it — nothing in the codebase overwrites your edits.
 */
public class Profile {

    private static final Path FILE = Paths.get("data", "profile.properties");

    private String name = "Your Name";
    private String tagline = "Computer Science Undergraduate · Developer · Researcher";
    private String about = "I build software and study problems that sit close to real life. "
            + "Most of my work is in Java and Python, with a growing interest in applied machine "
            + "learning for climate and language data.";
    private String githubUrl = "https://github.com/yourusername";
    private String linkedinUrl = "https://www.linkedin.com/in/yourusername";
    private String email = "you@example.com";
    private String whatsapp = "8801XXXXXXXXX";
    // SHA-256 of "admin123". Change the password by editing password_hash in
    // data/profile.properties, not here.
    private String passwordHash = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";
    // Free key from https://aistudio.google.com — see GeminiService.java.
    private String geminiApiKey = "";
    private String geminiModel = "gemini-2.5-flash";

    /** Reads data/profile.properties, creating it with placeholders on first run. */
    public void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                writeDefaults();
                return; // the file now matches the defaults already set above
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            }
            name = props.getProperty("name", name);
            tagline = props.getProperty("tagline", tagline);
            about = props.getProperty("about", about);
            githubUrl = props.getProperty("github", githubUrl);
            linkedinUrl = props.getProperty("linkedin", linkedinUrl);
            email = props.getProperty("email", email);
            whatsapp = props.getProperty("whatsapp", whatsapp);
            passwordHash = props.getProperty("password_hash", passwordHash);
            geminiApiKey = props.getProperty("gemini_api_key", geminiApiKey);
            geminiModel = props.getProperty("gemini_model", geminiModel);
        } catch (IOException e) {
            System.err.println("Could not read data/profile.properties, using defaults: " + e.getMessage());
        }
    }

    public void save() throws IOException {
        String content = "# Your personal details for the portfolio app.\n"
                + "# Edit the value after each '=' below, save this file, then restart the app.\n"
                + "# This file lives outside the Java source, so future code updates never touch it.\n"
                + "# Keep each value on a single line (no manual line breaks in the middle of 'about').\n"
                + "\n"
                + "name=" + name + "\n"
                + "tagline=" + tagline + "\n"
                + "about=" + about + "\n"
                + "github=" + githubUrl + "\n"
                + "linkedin=" + linkedinUrl + "\n"
                + "email=" + email + "\n"
                + "\n"
                + "# Country code, no plus sign, no spaces, e.g. 8801712345678\n"
                + "whatsapp=" + whatsapp + "\n"
                + "\n"
                + "# Owner login password, stored as a SHA-256 hash rather than plain text.\n"
                + "# To set your own: run  java src/main/java/portfolio/Auth.java \"your password\"\n"
                + "# then paste the printed hash below.\n"
                + "password_hash=" + passwordHash + "\n"
                + "\n"
                + "# Powers the \"Ask a Question\" chatbot (Google Gemini). Free to get: create a\n"
                + "# key at https://aistudio.google.com (no credit card needed) and paste it below.\n"
                + "# Leave blank to keep that section showing a \"not set up yet\" message instead.\n"
                + "gemini_api_key=" + geminiApiKey + "\n"
                + "gemini_model=" + geminiModel + "\n";
        Files.writeString(FILE, content);
    }

    private void writeDefaults() throws IOException {
        save();
    }

    public String getName() {
        return name;
    }

    public String getTagline() {
        return tagline;
    }

    public String getAbout() {
        return about;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public String getEffectiveGeminiApiKey() {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return geminiApiKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return "";
    }

    public synchronized void setGeminiApiKey(String apiKey) {
        this.geminiApiKey = apiKey == null ? "" : apiKey.trim();
        try {
            save();
        } catch (IOException e) {
            System.err.println("Could not save Gemini API key: " + e.getMessage());
        }
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public synchronized void setGeminiModel(String model) {
        this.geminiModel = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model.trim();
        try {
            save();
        } catch (IOException e) {
            System.err.println("Could not save Gemini model: " + e.getMessage());
        }
    }
}