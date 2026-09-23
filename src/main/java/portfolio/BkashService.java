package portfolio;

/**
 * bKash donation handling.
 *
 * The live integration is commented out on purpose. Drop your credentials into
 * the constants below, uncomment the block, and wire startPayment() to it.
 *
 * bKash Checkout (tokenized) is a three step flow:
 *   1. POST /tokenized/checkout/token/grant      -> id_token
 *   2. POST /tokenized/checkout/create           -> paymentID + bkashURL
 *   3. POST /tokenized/checkout/execute          -> final status (after the
 *      user approves the payment in the bKash page/app)
 *
 * Step 2 returns a URL that has to be opened in a browser. In this desktop app
 * you would hand it to HostServices.showDocument(...), then poll or wait for the
 * callback before calling execute.
 */
public class BkashService {

    // ------------------------------------------------------------ credentials
    // private static final String BASE_URL      = "https://tokenized.sandbox.bka.sh/v1.2.0-beta";
    // private static final String APP_KEY       = "YOUR_APP_KEY";
    // private static final String APP_SECRET    = "YOUR_APP_SECRET";
    // private static final String USERNAME      = "YOUR_USERNAME";
    // private static final String PASSWORD      = "YOUR_PASSWORD";
    // private static final String CALLBACK_URL  = "https://your-site.example/bkash/callback";

    /** Result of a donation attempt, handed back to the UI. */
    public static class PaymentResult {
        public final boolean success;
        public final String message;
        public final String paymentUrl;

        public PaymentResult(boolean success, String message, String paymentUrl) {
            this.success = success;
            this.message = message;
            this.paymentUrl = paymentUrl;
        }
    }

    /**
     * Placeholder. Returns a "not configured yet" result so the donate button
     * still does something sensible while the API keys are missing.
     */
    public PaymentResult startPayment(double amount, String reference) {
        if (amount <= 0) {
            return new PaymentResult(false, "Please enter an amount greater than zero.", null);
        }

        return new PaymentResult(false,
                "bKash is not connected yet.\n\n"
                        + "Amount: BDT " + String.format("%.2f", amount) + "\n"
                        + "Reference: " + reference + "\n\n"
                        + "Add your app key and secret in BkashService.java and uncomment "
                        + "the integration block to enable live donations.",
                null);

        /* ----------------------------------------------------------------
         * LIVE INTEGRATION — uncomment once the credentials above are set.
         *
         * try {
         *     HttpClient client = HttpClient.newHttpClient();
         *
         *     // 1. Grant token -------------------------------------------------
         *     String grantBody = "{"
         *             + "\"app_key\":\"" + APP_KEY + "\","
         *             + "\"app_secret\":\"" + APP_SECRET + "\"}";
         *
         *     HttpRequest grantRequest = HttpRequest.newBuilder()
         *             .uri(URI.create(BASE_URL + "/tokenized/checkout/token/grant"))
         *             .header("Content-Type", "application/json")
         *             .header("Accept", "application/json")
         *             .header("username", USERNAME)
         *             .header("password", PASSWORD)
         *             .POST(HttpRequest.BodyPublishers.ofString(grantBody))
         *             .build();
         *
         *     HttpResponse<String> grantResponse =
         *             client.send(grantRequest, HttpResponse.BodyHandlers.ofString());
         *     String idToken = readJsonField(grantResponse.body(), "id_token");
         *     if (idToken == null) {
         *         return new PaymentResult(false, "Could not get a bKash token.", null);
         *     }
         *
         *     // 2. Create payment ----------------------------------------------
         *     String createBody = "{"
         *             + "\"mode\":\"0011\","
         *             + "\"payerReference\":\"" + reference + "\","
         *             + "\"callbackURL\":\"" + CALLBACK_URL + "\","
         *             + "\"amount\":\"" + String.format("%.2f", amount) + "\","
         *             + "\"currency\":\"BDT\","
         *             + "\"intent\":\"sale\","
         *             + "\"merchantInvoiceNumber\":\"DON" + System.currentTimeMillis() + "\"}";
         *
         *     HttpRequest createRequest = HttpRequest.newBuilder()
         *             .uri(URI.create(BASE_URL + "/tokenized/checkout/create"))
         *             .header("Content-Type", "application/json")
         *             .header("Accept", "application/json")
         *             .header("Authorization", idToken)
         *             .header("X-APP-Key", APP_KEY)
         *             .POST(HttpRequest.BodyPublishers.ofString(createBody))
         *             .build();
         *
         *     HttpResponse<String> createResponse =
         *             client.send(createRequest, HttpResponse.BodyHandlers.ofString());
         *     String paymentId = readJsonField(createResponse.body(), "paymentID");
         *     String bkashUrl  = readJsonField(createResponse.body(), "bkashURL");
         *
         *     if (paymentId == null || bkashUrl == null) {
         *         return new PaymentResult(false, "bKash rejected the payment request.", null);
         *     }
         *
         *     // Open bkashUrl with HostServices.showDocument(bkashUrl) and wait
         *     // for the user to approve, then:
         *
         *     // 3. Execute payment ---------------------------------------------
         *     // HttpRequest executeRequest = HttpRequest.newBuilder()
         *     //         .uri(URI.create(BASE_URL + "/tokenized/checkout/execute"))
         *     //         .header("Content-Type", "application/json")
         *     //         .header("Accept", "application/json")
         *     //         .header("Authorization", idToken)
         *     //         .header("X-APP-Key", APP_KEY)
         *     //         .POST(HttpRequest.BodyPublishers.ofString(
         *     //                 "{\"paymentID\":\"" + paymentId + "\"}"))
         *     //         .build();
         *     // HttpResponse<String> executeResponse =
         *     //         client.send(executeRequest, HttpResponse.BodyHandlers.ofString());
         *     // String status = readJsonField(executeResponse.body(), "transactionStatus");
         *
         *     return new PaymentResult(true, "Redirecting you to bKash…", bkashUrl);
         *
         * } catch (Exception e) {
         *     return new PaymentResult(false, "bKash request failed: " + e.getMessage(), null);
         * }
         * ---------------------------------------------------------------- */
    }

    /* Minimal field reader so the sample above needs no JSON library.
     * Swap it for Jackson or Gson in real use.
     *
     * private static String readJsonField(String json, String field) {
     *     String needle = "\"" + field + "\"";
     *     int keyAt = json.indexOf(needle);
     *     if (keyAt < 0) return null;
     *     int firstQuote = json.indexOf('"', json.indexOf(':', keyAt) + 1);
     *     int lastQuote = json.indexOf('"', firstQuote + 1);
     *     if (firstQuote < 0 || lastQuote < 0) return null;
     *     return json.substring(firstQuote + 1, lastQuote);
     * }
     */
}