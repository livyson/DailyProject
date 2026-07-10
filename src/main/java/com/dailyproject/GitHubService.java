package com.dailyproject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Cliente da API REST do GitHub: PRs, comentários, discussões e merge.
 */
public final class GitHubService {

    private static final String API = "https://api.github.com";

    private final Config config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GitHubService(Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Abre (ou reutiliza) um PR de develop → main.
     */
    public PullRequest ensurePullRequest(String title, String body) throws IOException, InterruptedException {
        Optional<PullRequest> existing = findOpenPullRequest();
        if (existing.isPresent()) {
            System.out.println("[github] PR já aberto: #" + existing.get().number() + " " + existing.get().url());
            return existing.get();
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", title);
        payload.put("head", config.sourceBranch());
        payload.put("base", config.targetBranch());
        payload.put("body", body);

        JsonNode response = post("/repos/" + config.fullRepo() + "/pulls", payload);
        PullRequest pr = new PullRequest(
                response.get("number").asInt(),
                response.get("html_url").asText(),
                response.get("node_id").asText(),
                response.path("head").path("sha").asText()
        );
        System.out.println("[github] PR criado: #" + pr.number() + " " + pr.url());
        return pr;
    }

    public Optional<PullRequest> findOpenPullRequest() throws IOException, InterruptedException {
        String path = "/repos/" + config.fullRepo() + "/pulls?state=open&base="
                + config.targetBranch() + "&head=" + config.githubOwner() + ":" + config.sourceBranch();
        JsonNode arr = get(path);
        if (!arr.isArray() || arr.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = arr.get(0);
        return Optional.of(new PullRequest(
                first.get("number").asInt(),
                first.get("html_url").asText(),
                first.get("node_id").asText(),
                first.path("head").path("sha").asText()
        ));
    }

    /**
     * Comentário no PR (como o autor autenticado pelo token — tipicamente você).
     */
    public void commentOnPullRequest(int prNumber, String comment) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("body", comment);
        post("/repos/" + config.fullRepo() + "/issues/" + prNumber + "/comments", payload);
        System.out.println("[github] Comentário publicado no PR #" + prNumber);
    }

    /**
     * Abre uma Discussion no repositório (GraphQL). Requer discussions habilitadas.
     */
    public String createDiscussion(String title, String body) throws IOException, InterruptedException {
        String repoId = repositoryNodeId();
        String categoryId = discussionCategoryId(repoId);

        String mutation = """
                mutation($repoId: ID!, $categoryId: ID!, $title: String!, $body: String!) {
                  createDiscussion(input: {
                    repositoryId: $repoId,
                    categoryId: $categoryId,
                    title: $title,
                    body: $body
                  }) {
                    discussion {
                      id
                      url
                      number
                    }
                  }
                }
                """;

        ObjectNode variables = mapper.createObjectNode();
        variables.put("repoId", repoId);
        variables.put("categoryId", categoryId);
        variables.put("title", title);
        variables.put("body", body);

        ObjectNode request = mapper.createObjectNode();
        request.put("query", mutation);
        request.set("variables", variables);

        JsonNode response = graphql(request);
        if (response.has("errors")) {
            throw new IOException("GraphQL errors: " + response.get("errors"));
        }
        JsonNode discussion = response.path("data").path("createDiscussion").path("discussion");
        String url = discussion.path("url").asText();
        System.out.println("[github] Discussão criada: " + url);
        return url;
    }

    /**
     * Aprova o PR (REVIEW) e faz merge.
     * Nota: a API do GitHub recusa APPROVE no próprio PR; nesse caso seguimos direto para o merge.
     */
    public void approveAndMerge(int prNumber, String commitTitle) throws IOException, InterruptedException {
        ObjectNode review = mapper.createObjectNode();
        review.put("event", "APPROVE");
        review.put("body", "Aprovado automaticamente pelo DailyProject após revisão do fluxo diário.");
        try {
            post("/repos/" + config.fullRepo() + "/pulls/" + prNumber + "/reviews", review);
            System.out.println("[github] PR #" + prNumber + " aprovado");
        } catch (IOException e) {
            // GitHub: "Can not approve your own pull request" — tenta merge mesmo assim.
            System.out.println("[github] Approve indisponível (provavelmente PR próprio): " + e.getMessage());
            System.out.println("[github] Seguindo para merge…");
        }

        ObjectNode merge = mapper.createObjectNode();
        merge.put("commit_title", commitTitle);
        merge.put("merge_method", "squash");
        put("/repos/" + config.fullRepo() + "/pulls/" + prNumber + "/merge", merge);
        System.out.println("[github] PR #" + prNumber + " mergeado em " + config.targetBranch());
    }

    private String repositoryNodeId() throws IOException, InterruptedException {
        String query = """
                query($owner: String!, $name: String!) {
                  repository(owner: $owner, name: $name) { id }
                }
                """;
        ObjectNode variables = mapper.createObjectNode();
        variables.put("owner", config.githubOwner());
        variables.put("name", config.githubRepo());
        ObjectNode request = mapper.createObjectNode();
        request.put("query", query);
        request.set("variables", variables);
        JsonNode response = graphql(request);
        String id = response.path("data").path("repository").path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IOException("Não foi possível obter o node id do repositório. Resposta: " + response);
        }
        return id;
    }

    private String discussionCategoryId(String repoId) throws IOException, InterruptedException {
        String query = """
                query($owner: String!, $name: String!) {
                  repository(owner: $owner, name: $name) {
                    discussionCategories(first: 20) {
                      nodes { id name slug }
                    }
                  }
                }
                """;
        ObjectNode variables = mapper.createObjectNode();
        variables.put("owner", config.githubOwner());
        variables.put("name", config.githubRepo());
        ObjectNode request = mapper.createObjectNode();
        request.put("query", query);
        request.set("variables", variables);
        JsonNode response = graphql(request);
        JsonNode nodes = response.path("data").path("repository").path("discussionCategories").path("nodes");
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new IOException(
                    "Discussões não estão habilitadas neste repositório. "
                            + "Ative em Settings → General → Features → Discussions.");
        }
        // Prefere a categoria "Ideas" / "General" / primeira disponível
        for (JsonNode node : nodes) {
            String slug = node.path("slug").asText("").toLowerCase();
            if (slug.contains("idea") || slug.contains("general") || slug.contains("q-a")) {
                return node.get("id").asText();
            }
        }
        return nodes.get(0).get("id").asText();
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path).GET().build();
        return send(request);
    }

    private JsonNode post(String path, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private JsonNode put(String path, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private JsonNode graphql(ObjectNode body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API + "/graphql"))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + config.githubToken())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + config.githubToken())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DailyProject/1.0");
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        String body = response.body();
        if (status < 200 || status >= 300) {
            String hint = "";
            if (status == 403 && body != null
                    && body.contains("not permitted to create or approve pull requests")) {
                hint = "\nDica: em Settings → Actions → General → Workflow permissions, "
                        + "ative 'Read and write permissions' e "
                        + "'Allow GitHub Actions to create and approve pull requests'.";
            }
            throw new IOException("GitHub API " + status + " " + request.method() + " "
                    + request.uri() + "\n" + body + hint);
        }
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(body);
    }

    public record PullRequest(int number, String url, String nodeId, String headSha) {
    }
}
