package portfolio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Owner authentication. The plain password is never stored anywhere, only its SHA-256 hash.
 *
 * The hash normally lives in data/auth.txt, written the moment you change your password from
 * inside the app (Owner login, then "Change password"). Until you've changed it at least once,
 * DEFAULT_PASSWORD_HASH below is used instead, which is the hash of "admin123" — change it before
 * showing this app to anyone else.
 *
 * You can also set a password manually without going through the app:
 *   1. Run:  java portfolio.Auth "my new password"
 *   2. Paste the printed hash into data/auth.txt (create the file if it doesn't exist yet),
 *      or into DEFAULT_PASSWORD_HASH below if you'd rather it ship as the built-in default.
 *   3. Rebuild if you edited the source.
 */
public final class Auth {

    private static final String DEFAULT_PASSWORD_HASH =
            "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

    private static final Path HASH_FILE = Paths.get("data", "auth.txt");

    private Auth() {
    }

    public static boolean verify(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return constantTimeEquals(sha256(candidate), currentHash());
    }

    /**
     * Changes the owner password by writing its hash to data/auth.txt, so it's picked up by
     * every future login (and every future run of the app) without needing a rebuild.
     * Returns false if the new password couldn't be saved (e.g. no write access to ./data).
     */
    public static boolean changePassword(String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            return false;
        }
        try {
            Files.createDirectories(HASH_FILE.getParent());
            Files.writeString(HASH_FILE, sha256(newPassword), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            System.err.println("Could not save new password: " + e.getMessage());
            return false;
        }
    }

    /** Reads the current hash from data/auth.txt if it exists, otherwise falls back to the default. */
    private static String currentHash() {
        try {
            if (Files.isRegularFile(HASH_FILE)) {
                String stored = Files.readString(HASH_FILE, StandardCharsets.UTF_8).trim();
                if (!stored.isEmpty()) {
                    return stored;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + HASH_FILE + ": " + e.getMessage());
        }
        return DEFAULT_PASSWORD_HASH;
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    /** Avoids leaking information through how long the comparison takes. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return diff == 0;
    }

    /** Small helper so you can generate a hash for your own password without running the app. */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java portfolio.Auth \"your password\"");
            return;
        }
        System.out.println(sha256(args[0]));
    }
}