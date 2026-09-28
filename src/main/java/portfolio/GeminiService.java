package portfolio;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to communicate with Google Gemini API for the portfolio chatbot.
 * Zero external dependencies: uses standard Java 11+ HttpClient and SimpleJson.
 */
public class GeminiService {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private final Profile profile;
    private final HttpClient httpClient;

    public static class ChatMessage {
        public enum Sender {
            USER,
            BOT,
            SYSTEM
        }

        private final Sender sender;
        private final String text;
        private final LocalDateTime timestamp;

        public ChatMessage(Sender sender, String text) {
            this(sender, text, LocalDateTime.now());
        }

        public ChatMessage(Sender sender, String text, LocalDateTime timestamp) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
        }

        public Sender getSender() {
            return sender;
        }

        public String getText() {
            return text;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getFormattedTime() {
            return timestamp != null ? timestamp.format(TIME_FORMATTER) : "";
        }
    }

    public GeminiService(Profile profile) {
        this.profile = profile;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public String getApiKey() {
        return profile.getEffectiveGeminiApiKey();
    }

    public String getModel() {
        String model = profile.getGeminiModel();
        return (model != null && !model.isBlank()) ? model.trim() : "gemini-3.5-flash-lite";
    }

    /**
     * Sends the conversation history to Gemini and returns the assistant's reply.
     * Synchronous; call via Concurrency.pool() / runBackground from UI threads.
     */
    public String ask(List<ChatMessage> conversationHistory, List<Item> portfolioItems) throws Exception {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "⚠️ Gemini API key is not configured yet.\n\n"
                    + "Please enter your API key in the Setup box above, or add 'gemini_api_key=YOUR_KEY' "
                    + "to data/profile.properties.\n\n"
                    + "You can get a free API key at https://aistudio.google.com (no credit card required).";
        }

        String model = getModel();
        String endpoint = BASE_URL + model + ":generateContent";

        String systemInstruction = buildSystemPrompt(profile, portfolioItems);
        String requestJson = buildRequestBody(systemInstruction, conversationHistory);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Unable to reach Google Gemini servers. Please check your internet connection: "
                    + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        String responseBody = response.body();

        Map<String, Object> root;
        try {
            root = SimpleJson.parseObject(responseBody);
        } catch (Exception e) {
            throw new IOException("Failed to parse response from Gemini (HTTP " + statusCode + "): " + responseBody, e);
        }

        if (statusCode >= 200 && statusCode < 300) {
            return extractCandidateText(root);
        }

        // Handle error responses from Gemini
        if (root.containsKey("error")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) root.get("error");
            String message = error != null && error.get("message") != null
                    ? String.valueOf(error.get("message"))
                    : "Unknown error";

            if (statusCode == 400 && message.toLowerCase().contains("api key")) {
                return "❌ Invalid API key. Please check your Gemini API key in data/profile.properties or re-enter it above.";
            } else if (statusCode == 429) {
                return "⏳ Gemini quota or rate limit exceeded. Please wait a short moment and try again.";
            } else {
                return "⚠️ Gemini API Error (HTTP " + statusCode + "): " + message;
            }
        }

        return "⚠️ Request failed with status HTTP " + statusCode + ":\n" + responseBody;
    }

    private String buildRequestBody(String systemInstruction, List<ChatMessage> conversationHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // system_instruction
        sb.append("  \"system_instruction\": {\n");
        sb.append("    \"parts\": [{\"text\": \"")
                .append(SimpleJson.escape(systemInstruction))
                .append("\"}]\n");
        sb.append("  },\n");

        // contents
        sb.append("  \"contents\": [\n");

        List<ChatMessage> validTurns = new ArrayList<>();

        for (ChatMessage msg : conversationHistory) {
            if (msg.getSender() == ChatMessage.Sender.USER ||
                    msg.getSender() == ChatMessage.Sender.BOT) {
                validTurns.add(msg);
            }
        }

        boolean first = true;

        for (int i = 0; i < validTurns.size(); i++) {
            ChatMessage msg = validTurns.get(i);

            String role = msg.getSender() == ChatMessage.Sender.USER
                    ? "user"
                    : "model";

            if (!first) {
                sb.append(",\n");
            }

            first = false;

            sb.append("    {\n");
            sb.append("      \"role\": \"")
                    .append(role)
                    .append("\",\n");

            sb.append("      \"parts\": [{\"text\": \"")
                    .append(SimpleJson.escape(msg.getText()))
                    .append("\"}]\n");

            sb.append("    }");
        }

        sb.append("\n  ],\n");

        // generationConfig
        sb.append("  \"generationConfig\": {\n");
        sb.append("    \"maxOutputTokens\": 1024\n");
        sb.append("  }\n");

        sb.append("}");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractCandidateText(Map<String, Object> root) {
        Object candObj = root.get("candidates");
        if (candObj instanceof List) {
            List<Object> candidates = (List<Object>) candObj;
            if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
                Map<String, Object> firstCandidate = (Map<String, Object>) candidates.get(0);
                Object contentObj = firstCandidate.get("content");
                if (contentObj instanceof Map) {
                    Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                    Object partsObj = contentMap.get("parts");
                    if (partsObj instanceof List) {
                        List<Object> parts = (List<Object>) partsObj;
                        StringBuilder sb = new StringBuilder();
                        for (Object part : parts) {
                            if (part instanceof Map) {
                                Object text = ((Map<String, Object>) part).get("text");
                                if (text != null) {
                                    sb.append(text);
                                }
                            }
                        }
                        String result = sb.toString().trim();
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                }

                // If text was empty, check finishReason
                Object finishReason = firstCandidate.get("finishReason");
                if (finishReason != null && !"STOP".equals(finishReason)) {
                    return "Response completed with reason: " + finishReason;
                }
            }
        }
        return "No response text received from Gemini.";
    }

    /**
     * Builds a comprehensive system prompt grounding Gemini with all portfolio details.
     */
    public static String buildSystemPrompt(Profile profile, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the intelligent, helpful, and friendly AI Assistant for ").append(profile.getName())
                .append("'s personal portfolio desktop application.\n\n");
        sb.append("Your mission is to assist visitors, recruiters, collaborators, or students who have questions ")
                .append("or get confused about anything in ").append(profile.getName()).append("'s portfolio, ")
                .append("projects, research work, achievements, skills, or background.\n\n");

        sb.append("--- ABOUT ").append(profile.getName().toUpperCase()).append(" ---\n");
        sb.append("Name: ").append(profile.getName()).append("\n");
        sb.append("Tagline: ").append(profile.getTagline()).append("\n");
        sb.append("Bio: ").append(profile.getAbout()).append("\n");
        sb.append("Email: ").append(profile.getEmail()).append("\n");
        sb.append("GitHub: ").append(profile.getGithubUrl()).append("\n");
        sb.append("LinkedIn: ").append(profile.getLinkedinUrl()).append("\n");
        sb.append("WhatsApp: +").append(profile.getWhatsapp()).append("\n\n");

        sb.append("--- PORTFOLIO SECTIONS IN THIS APP ---\n");
        sb.append("1. About: Overview of ").append(profile.getName()).append("'s background and bio.\n");
        sb.append("2. Projects: Software applications, tools, and systems built by ").append(profile.getName()).append(".\n");
        sb.append("3. Research Work: Academic research publications, ongoing studies, and findings.\n");
        sb.append("4. Achievements: Competitions, awards, and milestones.\n");
        sb.append("5. Donate: Allows visitors to test dummy transactions and donations via the live bKash Sandbox payment gateway (Test wallet: 01770618575, OTP: 123456, PIN: 12121).\n");
        sb.append("6. Ask AI: This interactive chatbot powered by Google Gemini.\n");
        sb.append("7. Contact: Direct contact options (GitHub, LinkedIn, Email, WhatsApp).\n");
        sb.append("8. Comments: Public discussion where visitors can leave feedback on the portfolio or specific items.\n\n");

        if (items != null && !items.isEmpty()) {
            sb.append("--- PORTFOLIO ITEMS (PROJECTS, RESEARCH, ACHIEVEMENTS) ---\n");
            for (Item item : items) {
                sb.append("• [").append(item.getType().label()).append("] ").append(item.getTitle());
                if (!item.getYear().isBlank()) {
                    sb.append(" (").append(item.getYear()).append(")");
                }
                if (item.getType() == Item.Type.RESEARCH && item.getStatus() != null) {
                    sb.append(" [Status: ").append(item.getStatus().label()).append("]");
                }
                sb.append("\n");
                if (!item.getDescription().isBlank()) {
                    sb.append("  Description: ").append(item.getDescription().trim()).append("\n");
                }
                if (!item.getLink().isBlank()) {
                    sb.append("  Link: ").append(item.getLink().trim()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("--- GUIDELINES ---\n");
        sb.append("1. Always be courteous, inspiring, clear, and professional.\n");
        sb.append("2. When asked about a specific project or research, summarize its goals, technical achievements, and status accurately.\n");
        sb.append("3. If the user asks how to contact, collaborate, or hire ").append(profile.getName())
                .append(", provide the relevant contact channels.\n");
        sb.append("4. If the user is confused about how to use this desktop application, explain how they can click items to view details and attachments (images/PDFs), use the top nav bar to jump between sections, or leave comments.\n");
        sb.append("5. If asked general technical or programming questions, answer clearly and relate them to ").append(profile.getName())
                .append("'s tech stack where appropriate.\n");
        sb.append("6. Format your responses with clean, readable structure (short paragraphs, bullet points, or bold text) so it looks beautiful in the chat window.\n");

        return sb.toString();
    }
}
