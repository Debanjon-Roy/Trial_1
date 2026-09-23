package portfolio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Owner authentication. The plain password is never stored in the source, only
 * its SHA-256 hash, so reading the file does not hand anyone the password.
 *
 * To set your own password:
 *   1. Run:  java portfolio.Auth "my new password"
 *   2. Copy the printed hash into PASSWORD_HASH below.
 *   3. Rebuild.
 *
 * The default below is the hash of "admin123". Change it before you show this
 * to anyone.
 */
public final class Auth {

    private static final String PASSWORD_HASH =
            "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

    private Auth() {
    }

    public static boolean verify(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return constantTimeEquals(sha256(candidate), PASSWORD_HASH);
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