package portfolio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 password hashing and constant-time verification.
 *
 * The expected hash now lives in data/profile.properties (password_hash), not
 * in this file — see Profile.java. That means setting your own password never
 * means touching Java code, and it survives future updates to this file.
 *
 * To set your own password:
 *   1. Run:  java src/main/java/portfolio/Auth.java "my new password"
 *   2. Copy the printed hash into password_hash= in data/profile.properties.
 *   3. Restart the app — no rebuild needed, it's a plain text file.
 */
public final class Auth {

    private Auth() {
    }

    public static boolean verify(String candidate, String expectedHash) {
        if (candidate == null || candidate.isEmpty() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return constantTimeEquals(sha256(candidate), expectedHash);
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

    /** Small helper so you can generate a hash for your own password. */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java portfolio.Auth \"your password\"");
            return;
        }
        System.out.println(sha256(args[0]));
    }
}