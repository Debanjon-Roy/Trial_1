package portfolio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Service for integrating bKash Tokenized Checkout (Sandbox & Production).
 *
 * Flow:
 *   1. Token Grant (POST /tokenized/checkout/token/grant)
 *   2. Create Payment (POST /tokenized/checkout/create)
 *   3. User authorizes via bKash Checkout web interface in browser
 *   4. Local callback listener catches redirect (GET /bkash/callback)
 *   5. Execute Payment (POST /tokenized/checkout/execute)
 *   6. Query Payment (POST /tokenized/checkout/payment/status)
 */
public class BkashService {

    // Default official bKash Sandbox credentials
    public static final String SANDBOX_BASE_URL = "https://tokenized.sandbox.bka.sh/v1.2.0-beta";
    public static final String DEFAULT_APP_KEY = "4f6o0cjiki2rfm34kfdadl1eqq";
    public static final String DEFAULT_APP_SECRET = "2is7hdktrekvrbljjh44ll3d9l1dtjo4pasmjvs5vl5qr3fug4b";
    public static final String DEFAULT_USERNAME = "sandboxTokenizedUser02";
    public static final String DEFAULT_PASSWORD = "sandboxTokenizedUser02@12345";

    // Official bKash merchant demo callback URL (publicly reachable by bKash servers)
    public static final String SANDBOX_CALLBACK_URL = "https://merchantdemo.sandbox.bka.sh/callback";

    // Official sandbox test credentials for dummy transactions
    public static final String TEST_WALLET = "01770618575";
    public static final String TEST_OTP = "123456";
    public static final String TEST_PIN = "12121";

    private String baseUrl = SANDBOX_BASE_URL;
    private String appKey = DEFAULT_APP_KEY;
    private String appSecret = DEFAULT_APP_SECRET;
    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;

    private final HttpClient httpClient;
    private String idToken = null;
    private long tokenExpiryMillis = 0;

    private HttpServer callbackServer = null;
    private int callbackPort = -1;
    private BiConsumer<String, String> authCallbackListener = null;

    public BkashService() {
        this.httpClient = HttpClient.newBuilder()
                .sslContext(createSandboxSslContext())
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static SSLContext createSandboxSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc;
        } catch (Exception e) {
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new RuntimeException("Could not initialize SSL context", ex);
            }
        }
    }

    // ----------------------------------------------------------- Results

    public static class PaymentInitResult {
        public final boolean success;
        public final String message;
        public final String paymentId;
        public final String paymentUrl;
        public final double amount;
        public final String invoiceNumber;

        public PaymentInitResult(boolean success, String message, String paymentId,
                                 String paymentUrl, double amount, String invoiceNumber) {
            this.success = success;
            this.message = message;
            this.paymentId = paymentId;
            this.paymentUrl = paymentUrl;
            this.amount = amount;
            this.invoiceNumber = invoiceNumber;
        }
    }

    public static class PaymentExecuteResult {
        public final boolean success;
        public final String message;
        public final String trxId;
        public final String paymentId;
        public final double amount;
        public final String customerMsisdn;
        public final String transactionStatus;
        public final String statusCode;

        public PaymentExecuteResult(boolean success, String message, String trxId,
                                    String paymentId, double amount, String customerMsisdn,
                                    String transactionStatus, String statusCode) {
            this.success = success;
            this.message = message;
            this.trxId = trxId;
            this.paymentId = paymentId;
            this.amount = amount;
            this.customerMsisdn = customerMsisdn;
            this.transactionStatus = transactionStatus;
            this.statusCode = statusCode;
        }
    }

    public static class PaymentStatusResult {
        public final boolean success;
        public final String message;
        public final String paymentId;
        public final String trxId;
        public final double amount;
        public final String transactionStatus;
        public final String verificationStatus;

        public PaymentStatusResult(boolean success, String message, String paymentId,
                                   String trxId, double amount, String transactionStatus,
                                   String verificationStatus) {
            this.success = success;
            this.message = message;
            this.paymentId = paymentId;
            this.trxId = trxId;
            this.amount = amount;
            this.transactionStatus = transactionStatus;
            this.verificationStatus = verificationStatus;
        }
    }

    // ---------------------------------------------------- Callback Server

    /**
     * Ensures an embedded HTTP server is running to intercept bKash browser redirects.
     */
    public synchronized int ensureCallbackServer() {
        if (callbackServer != null && callbackPort > 0) {
            return callbackPort;
        }

        // Try ports starting from 8080, fallback to dynamic port
        int[] preferredPorts = new int[]{8080, 8081, 8082, 8888, 9090, 0};
        for (int port : preferredPorts) {
            try {
                callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                callbackPort = callbackServer.getAddress().getPort();
                callbackServer.setExecutor(Executors.newCachedThreadPool());
                callbackServer.createContext("/bkash/callback", new CallbackHttpHandler());
                callbackServer.start();
                System.out.println("[bKash] Callback server running on port " + callbackPort);
                return callbackPort;
            } catch (IOException e) {
                // Try next port
            }
        }
        throw new IllegalStateException("Could not start local HTTP callback server for bKash.");
    }

    public synchronized String getCallbackUrl() {
        int port = ensureCallbackServer();
        return "http://localhost:" + port + "/bkash/callback";
    }

    public void setAuthCallbackListener(BiConsumer<String, String> listener) {
        this.authCallbackListener = listener;
    }

    public synchronized void stopCallbackServer() {
        if (callbackServer != null) {
            try {
                callbackServer.stop(0);
            } catch (Exception ignored) {
            }
            callbackServer = null;
            callbackPort = -1;
        }
    }

    private class CallbackHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestUri = exchange.getRequestURI();
            String query = requestUri.getQuery();
            Map<String, String> params = parseQueryParams(query);

            String paymentId = params.get("paymentID");
            String status = params.get("status"); // success, failure, cancel

            boolean isSuccess = "success".equalsIgnoreCase(status);
            boolean isCancel = "cancel".equalsIgnoreCase(status);

            String title = isSuccess ? "Payment Authorized!" : (isCancel ? "Payment Cancelled" : "Payment Failed");
            String subText = isSuccess
                    ? "Your bKash transaction is being executed. You can return to the Portfolio application."
                    : (isCancel ? "You cancelled the payment in the bKash checkout." : "Payment could not be completed.");
            String badgeColor = isSuccess ? "#2e7d32" : (isCancel ? "#f57c00" : "#d32f2f");
            String badgeBg = isSuccess ? "#e8f5e9" : (isCancel ? "#fff3e0" : "#ffebee");

            String html = "<!DOCTYPE html>\n"
                    + "<html lang=\"en\">\n"
                    + "<head>\n"
                    + "  <meta charset=\"utf-8\">\n"
                    + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                    + "  <title>" + title + "</title>\n"
                    + "  <style>\n"
                    + "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; "
                    + "           background: linear-gradient(135deg, #fdf2f6 0%, #fbe4ee 100%); margin: 0; min-height: 100vh; "
                    + "           display: flex; align-items: center; justify-content: center; padding: 20px; box-sizing: border-box; }\n"
                    + "    .card { background: #ffffff; border-radius: 20px; box-shadow: 0 14px 40px rgba(226, 19, 110, 0.12); "
                    + "            max-width: 440px; width: 100%; padding: 36px 30px; text-align: center; border-top: 6px solid #e2136e; }\n"
                    + "    .bkash-logo { color: #e2136e; font-size: 28px; font-weight: 900; letter-spacing: -1px; margin-bottom: 20px; }\n"
                    + "    .status-badge { display: inline-block; padding: 6px 18px; border-radius: 30px; font-size: 13px; font-weight: 700; "
                    + "                    background: " + badgeBg + "; color: " + badgeColor + "; margin-bottom: 16px; }\n"
                    + "    h2 { color: #1a1a1a; margin: 0 0 10px; font-size: 22px; font-weight: 700; }\n"
                    + "    p { color: #555555; font-size: 14.5px; line-height: 1.5; margin: 0 0 22px; }\n"
                    + "    .info-box { background: #fdf5f8; border: 1px dashed #e2136e; border-radius: 12px; padding: 12px 16px; "
                    + "                font-size: 13px; color: #444; margin-bottom: 24px; text-align: left; }\n"
                    + "    .info-row { display: flex; justify-content: space-between; margin: 4px 0; }\n"
                    + "    .close-btn { background: #e2136e; color: #ffffff; border: none; border-radius: 10px; padding: 10px 24px; "
                    + "                 font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.2s; }\n"
                    + "    .close-btn:hover { background: #c20c5c; }\n"
                    + "  </style>\n"
                    + "</head>\n"
                    + "<body>\n"
                    + "  <div class=\"card\">\n"
                    + "    <div class=\"bkash-logo\">bKash <span style=\"font-size: 14px; font-weight: 500; color: #888;\">Sandbox</span></div>\n"
                    + "    <div class=\"status-badge\">" + (status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT)) + "</div>\n"
                    + "    <h2>" + title + "</h2>\n"
                    + "    <p>" + subText + "</p>\n"
                    + (paymentId != null ? "    <div class=\"info-box\"><div class=\"info-row\"><span>Payment ID:</span><strong>" + paymentId + "</strong></div></div>\n" : "")
                    + "    <button class=\"close-btn\" onclick=\"window.close()\">Close Tab</button>\n"
                    + "  </div>\n"
                    + "</body>\n"
                    + "</html>";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

            if (authCallbackListener != null && paymentId != null) {
                authCallbackListener.accept(paymentId, status);
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length > 0) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                map.put(key, value);
            }
        }
        return map;
    }

    // ---------------------------------------------------- Token Grant

    /**
     * Requests or returns cached bKash id_token.
     */
    public synchronized String getOrRefreshToken() throws Exception {
        long now = System.currentTimeMillis();
        if (idToken != null && now < (tokenExpiryMillis - 60_000)) {
            return idToken;
        }

        String grantBody = "{"
                + "\"app_key\":\"" + SimpleJson.escape(appKey) + "\","
                + "\"app_secret\":\"" + SimpleJson.escape(appSecret) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tokenized/checkout/token/grant"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("username", username)
                .header("password", password)
                .POST(HttpRequest.BodyPublishers.ofString(grantBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> json = SimpleJson.parseObject(response.body());

        String statusCode = String.valueOf(json.get("statusCode"));
        if (!"0000".equals(statusCode)) {
            String msg = (String) json.getOrDefault("statusMessage", "Failed to grant bKash token.");
            throw new IOException("bKash token grant error (" + statusCode + "): " + msg);
        }

        this.idToken = (String) json.get("id_token");
        Object expiresInObj = json.get("expires_in");
        long expiresIn = 3600;
        if (expiresInObj instanceof Number) {
            expiresIn = ((Number) expiresInObj).longValue();
        }
        this.tokenExpiryMillis = now + (expiresIn * 1000L);
        return this.idToken;
    }

    // ---------------------------------------------------- Create Payment

    /**
     * Initiates a payment checkout session.
     */
    public PaymentInitResult createPayment(double amount, String donorName) {
        if (amount <= 0) {
            return new PaymentInitResult(false, "Amount must be greater than zero.", null, null, amount, null);
        }

        try {
            String token = getOrRefreshToken();
            String callbackUrl = SANDBOX_CALLBACK_URL;
            String invoiceNumber = "INV" + System.currentTimeMillis();
            String reference = (donorName == null || donorName.isBlank()) ? "Anonymous" : donorName.trim();

            String createBody = "{"
                    + "\"mode\":\"0011\","
                    + "\"payerReference\":\"" + SimpleJson.escape(reference) + "\","
                    + "\"callbackURL\":\"" + SimpleJson.escape(callbackUrl) + "\","
                    + "\"amount\":\"" + String.format(Locale.US, "%.2f", amount) + "\","
                    + "\"currency\":\"BDT\","
                    + "\"intent\":\"sale\","
                    + "\"merchantInvoiceNumber\":\"" + SimpleJson.escape(invoiceNumber) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tokenized/checkout/create"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", token)
                    .header("X-APP-Key", appKey)
                    .POST(HttpRequest.BodyPublishers.ofString(createBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = SimpleJson.parseObject(response.body());

            String statusCode = String.valueOf(json.get("statusCode"));
            if ("0000".equals(statusCode)) {
                String paymentId = (String) json.get("paymentID");
                String bkashUrl = (String) json.get("bkashURL");
                return new PaymentInitResult(true, "Checkout created.", paymentId, bkashUrl, amount, invoiceNumber);
            } else {
                String msg = (String) json.getOrDefault("statusMessage", "Failed to create payment session.");
                return new PaymentInitResult(false, msg + " (" + statusCode + ")", null, null, amount, null);
            }
        } catch (Exception e) {
            return new PaymentInitResult(false, "Payment error: " + e.getMessage(), null, null, amount, null);
        }
    }

    // ---------------------------------------------------- Execute Payment

    /**
     * Executes / confirms a payment after user authorization in the bKash interface.
     */
    public PaymentExecuteResult executePayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return new PaymentExecuteResult(false, "Payment ID is required.", null, paymentId, 0, null, null, null);
        }

        try {
            String token = getOrRefreshToken();
            String execBody = "{\"paymentID\":\"" + SimpleJson.escape(paymentId) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tokenized/checkout/execute"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", token)
                    .header("X-APP-Key", appKey)
                    .POST(HttpRequest.BodyPublishers.ofString(execBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = SimpleJson.parseObject(response.body());

            String statusCode = String.valueOf(json.get("statusCode"));
            String statusMessage = (String) json.getOrDefault("statusMessage", "");
            String trxStatus = (String) json.getOrDefault("transactionStatus", "");

            if ("0000".equals(statusCode) && "Completed".equalsIgnoreCase(trxStatus)) {
                String trxId = (String) json.get("trxID");
                String customerMsisdn = (String) json.get("customerMsisdn");
                double amt = 0;
                Object amtObj = json.get("amount");
                if (amtObj != null) {
                    try {
                        amt = Double.parseDouble(String.valueOf(amtObj));
                    } catch (Exception ignored) {
                    }
                }
                return new PaymentExecuteResult(true, "Payment successful! TrxID: " + trxId,
                        trxId, paymentId, amt, customerMsisdn, trxStatus, statusCode);
            } else {
                return new PaymentExecuteResult(false, statusMessage + " (" + statusCode + ")",
                        null, paymentId, 0, null, trxStatus, statusCode);
            }
        } catch (Exception e) {
            return new PaymentExecuteResult(false, "Execution error: " + e.getMessage(),
                    null, paymentId, 0, null, null, "500");
        }
    }

    // ---------------------------------------------------- Query Status

    /**
     * Queries the status of an ongoing or completed payment.
     */
    public PaymentStatusResult queryPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return new PaymentStatusResult(false, "Payment ID is required.", paymentId, null, 0, null, null);
        }

        try {
            String token = getOrRefreshToken();
            String queryBody = "{\"paymentID\":\"" + SimpleJson.escape(paymentId) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tokenized/checkout/payment/status"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", token)
                    .header("X-APP-Key", appKey)
                    .POST(HttpRequest.BodyPublishers.ofString(queryBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = SimpleJson.parseObject(response.body());

            String statusCode = String.valueOf(json.get("statusCode"));
            if ("0000".equals(statusCode)) {
                String trxId = (String) json.get("trxID");
                String trxStatus = (String) json.get("transactionStatus");
                String verStatus = (String) json.get("verificationStatus");
                double amt = 0;
                Object amtObj = json.get("amount");
                if (amtObj != null) {
                    try {
                        amt = Double.parseDouble(String.valueOf(amtObj));
                    } catch (Exception ignored) {
                    }
                }
                return new PaymentStatusResult(true, "Status: " + trxStatus, paymentId, trxId, amt, trxStatus, verStatus);
            } else {
                String msg = (String) json.getOrDefault("statusMessage", "Failed to query status.");
                return new PaymentStatusResult(false, msg, paymentId, null, 0, null, null);
            }
        } catch (Exception e) {
            return new PaymentStatusResult(false, "Query error: " + e.getMessage(), paymentId, null, 0, null, null);
        }
    }

    // ---------------------------------------------------- Getters / Setters

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}