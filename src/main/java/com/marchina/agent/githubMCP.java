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

public class githubMCP {

    private final String githubToken;
    private final String openaiKey;
    private final String openaiEndpoint;
    private final String openaiDeploymentId;
    private final String openaiApiVersion;

    public githubMCP(Dotenv dotenv) {
        this.githubToken = dotenv.get("GITHUB_TOKEN");
        this.openaiKey = dotenv.get("AZURE_OPENAI_API_KEY");
        this.openaiEndpoint = dotenv.get("AZURE_OPENAI_ENDPOINT");
        this.openaiDeploymentId = dotenv.get("AZURE_OPENAI_REALTIME_DEPLOYMENT_ID");
        this.openaiApiVersion = dotenv.get("AZURE_OPENAI_API_VERSION");

        if (githubToken == null || openaiKey == null || openaiEndpoint == null ||
            openaiDeploymentId == null || openaiApiVersion == null) {
            throw new IllegalStateException("Missing required configuration in .env");
        }
    }

    // Step 1: Fetch GitHub Issues
    public List<String> fetchGitHubIssues(String owner, String repo) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/issues";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONArray issues = new JSONArray(response.body());

        List<String> summaries = new ArrayList<>();
        for (int i = 0; i < Math.min(5, issues.length()); i++) {
            JSONObject issue = issues.getJSONObject(i);
            String title = issue.getString("title");
            String body = issue.optString("body", "");
            summaries.add("Title: " + title + "\nDescription: " + body);
        }

        return summaries;
    }

    // Step 2: Generate context via Azure OpenAI
    public String generateContextFromGitHubIssues(List<String> issues) throws Exception {
        String prompt = String.join("\n\n", issues);

        String userMessage = "Based on the following GitHub issues, summarize key themes, potential risks, and what action items should be prioritized:\n\n" + prompt;

        JSONObject requestBody = new JSONObject();
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You are a senior software project manager."));
        messages.put(new JSONObject().put("role", "user").put("content", userMessage));
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.5);
        requestBody.put("max_tokens", 800);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openaiEndpoint + "openai/deployments/" + openaiDeploymentId + "/chat/completions?api-version=" + openaiApiVersion))
                .header("Content-Type", "application/json")
                .header("api-key", openaiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
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
        githubMCP generator = new githubMCP(dotenv);

        // Step 1: GitHub data
        List<String> issues = generator.fetchGitHubIssues("openai", "openai-java");

        // Step 2: Generate insight via Azure OpenAI
        String aiSummary = generator.generateContextFromGitHubIssues(issues);

        System.out.println("AI-Generated Context Summary:");
        System.out.println(aiSummary);
    }
}
