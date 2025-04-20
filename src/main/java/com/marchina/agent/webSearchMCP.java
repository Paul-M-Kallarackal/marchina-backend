import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class webSearchMCP {

    private static final Logger logger = LoggerFactory.getLogger(webSearchMCP.class);

    private final String apiKey;
    private final String endpoint;
    private final String deploymentId;
    private final String apiVersion;
    private final String bingApiKey;
    private final String bingSearchUrl;

    public webSearchMCP(Dotenv dotenv) {
        logger.info("Initializing webSearchMCP with Azure OpenAI");

        this.apiKey = dotenv.get("AZURE_OPENAI_API_KEY");
        this.endpoint = dotenv.get("AZURE_OPENAI_ENDPOINT");
        this.deploymentId = dotenv.get("AZURE_OPENAI_REALTIME_DEPLOYMENT_ID");
        this.apiVersion = dotenv.get("AZURE_OPENAI_API_VERSION");
        this.bingApiKey = dotenv.get("BING_API_KEY");
        this.bingSearchUrl = "https://api.bing.microsoft.com/v7.0/search";

        if (apiKey == null || endpoint == null || deploymentId == null || apiVersion == null || bingApiKey == null) {
            String message = "Missing required configuration. Please check your .env file.";
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    public List<String> searchWeb(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bingSearchUrl + "?q=" + encodedQuery))
                .header("Ocp-Apim-Subscription-Key", bingApiKey)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        List<String> snippets = new ArrayList<>();

        if (json.has("webPages")) {
            JSONArray items = json.getJSONObject("webPages").getJSONArray("value");
            for (int i = 0; i < items.length(); i++) {
                snippets.add(items.getJSONObject(i).getString("snippet"));
            }
        }

        return snippets;
    }

    public String generateContext(List<String> snippets) throws Exception {
        String prompt = "Summarize the following search results:\n\n" + String.join("\n\n", snippets);

        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You are a helpful assistant."));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 500);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "openai/deployments/" + deploymentId + "/chat/completions?api-version=" + apiVersion))
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());

        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        webSearchMCP generator = new webSearchMCP(dotenv);
        List<String> results = generator.searchWeb("latest trends in AI for 2025");
        String context = generator.generateContext(results);
        System.out.println("Generated Context:\n" + context);
    }
}
