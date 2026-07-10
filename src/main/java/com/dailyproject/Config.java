package com.dailyproject;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuração carregada de variáveis de ambiente e arquivo .env (opcional).
 */
public final class Config {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String githubToken;
    private final String githubOwner;
    private final String githubRepo;
    private final String sourceBranch;
    private final String targetBranch;
    private final int minLines;
    private final List<LocalTime> scheduleTimes;
    private final String authorName;
    private final String authorEmail;
    private final Path repoRoot;
    private final boolean githubActions;

    private Config(
            String githubToken,
            String githubOwner,
            String githubRepo,
            String sourceBranch,
            String targetBranch,
            int minLines,
            List<LocalTime> scheduleTimes,
            String authorName,
            String authorEmail,
            Path repoRoot,
            boolean githubActions
    ) {
        this.githubToken = githubToken;
        this.githubOwner = githubOwner;
        this.githubRepo = githubRepo;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.minLines = minLines;
        this.scheduleTimes = scheduleTimes;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.repoRoot = repoRoot;
        this.githubActions = githubActions;
    }

    public static Config load(Path repoRoot) {
        Map<String, String> env = EnvLoader.load(repoRoot.resolve(".env"));

        String token = resolveToken(env);
        String owner = firstNonBlank(
                System.getenv("GITHUB_OWNER"),
                System.getenv("GITHUB_REPOSITORY_OWNER"),
                env.get("GITHUB_OWNER"),
                "livyson"
        );
        String repo = firstNonBlank(
                System.getenv("GITHUB_REPO"),
                repositoryNameFromEnv(),
                env.get("GITHUB_REPO"),
                "DailyProject"
        );
        String source = get(env, "GIT_SOURCE_BRANCH", "develop");
        String target = get(env, "GIT_TARGET_BRANCH", "main");
        int minLines = Integer.parseInt(get(env, "MIN_LINES", "100"));
        String times = get(env, "SCHEDULE_TIMES", "09:00,14:00,19:00");
        String authorName = get(env, "AUTHOR_NAME", "Livyson");
        String authorEmail = get(env, "AUTHOR_EMAIL", "livyson@users.noreply.github.com");
        boolean githubActions = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));

        List<LocalTime> schedule = Arrays.stream(times.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> LocalTime.parse(s, TIME))
                .toList();

        if (schedule.isEmpty()) {
            throw new IllegalStateException("SCHEDULE_TIMES precisa ter ao menos um horário (ex: 09:00,14:00,19:00)");
        }

        return new Config(token, owner, repo, source, target, minLines, schedule,
                authorName, authorEmail, repoRoot, githubActions);
    }

    /**
     * Preferência: PAT dedicado (DAILY_GITHUB_TOKEN / GH_PAT) → GITHUB_TOKEN (Actions ou local).
     */
    private static String resolveToken(Map<String, String> env) {
        String token = firstNonBlank(
                System.getenv("DAILY_GITHUB_TOKEN"),
                System.getenv("GH_PAT"),
                System.getenv("GH_TOKEN"),
                System.getenv("GITHUB_TOKEN"),
                env.get("DAILY_GITHUB_TOKEN"),
                env.get("GH_PAT"),
                env.get("GITHUB_TOKEN")
        );
        if (token == null || token.isBlank() || token.contains("seu_token_aqui")) {
            throw new IllegalStateException(
                    "Defina GITHUB_TOKEN (ou DAILY_GITHUB_TOKEN) no ambiente/.env. "
                            + "No GitHub Actions o GITHUB_TOKEN já é injetado automaticamente.");
        }
        return token;
    }

    private static String repositoryNameFromEnv() {
        String full = System.getenv("GITHUB_REPOSITORY");
        if (full == null || !full.contains("/")) {
            return null;
        }
        return full.substring(full.indexOf('/') + 1);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String get(Map<String, String> env, String key, String defaultValue) {
        String fromSystem = System.getenv(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim();
        }
        String fromFile = env.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    public String githubToken() {
        return githubToken;
    }

    public String githubOwner() {
        return githubOwner;
    }

    public String githubRepo() {
        return githubRepo;
    }

    public String sourceBranch() {
        return sourceBranch;
    }

    public String targetBranch() {
        return targetBranch;
    }

    public int minLines() {
        return minLines;
    }

    public List<LocalTime> scheduleTimes() {
        return scheduleTimes;
    }

    public String authorName() {
        return authorName;
    }

    public String authorEmail() {
        return authorEmail;
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public boolean githubActions() {
        return githubActions;
    }

    public String fullRepo() {
        return githubOwner + "/" + githubRepo;
    }

    @Override
    public String toString() {
        return "Config{repo=" + fullRepo()
                + ", " + sourceBranch + "→" + targetBranch
                + ", minLines=" + minLines
                + ", schedule=" + scheduleTimes
                + ", author=" + authorName
                + ", actions=" + githubActions
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Config config)) return false;
        return minLines == config.minLines
                && Objects.equals(githubOwner, config.githubOwner)
                && Objects.equals(githubRepo, config.githubRepo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(githubOwner, githubRepo, minLines);
    }
}
