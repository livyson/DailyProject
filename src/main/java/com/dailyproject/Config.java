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
            Path repoRoot
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
    }

    public static Config load(Path repoRoot) {
        Map<String, String> env = EnvLoader.load(repoRoot.resolve(".env"));

        String token = required(env, "GITHUB_TOKEN");
        String owner = get(env, "GITHUB_OWNER", "livyson");
        String repo = get(env, "GITHUB_REPO", "DailyProject");
        String source = get(env, "GIT_SOURCE_BRANCH", "develop");
        String target = get(env, "GIT_TARGET_BRANCH", "main");
        int minLines = Integer.parseInt(get(env, "MIN_LINES", "100"));
        String times = get(env, "SCHEDULE_TIMES", "09:00,14:00,19:00");
        String authorName = get(env, "AUTHOR_NAME", "Livyson");
        String authorEmail = get(env, "AUTHOR_EMAIL", "livyson@users.noreply.github.com");

        List<LocalTime> schedule = Arrays.stream(times.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> LocalTime.parse(s, TIME))
                .toList();

        if (schedule.isEmpty()) {
            throw new IllegalStateException("SCHEDULE_TIMES precisa ter ao menos um horário (ex: 09:00,14:00,19:00)");
        }

        return new Config(token, owner, repo, source, target, minLines, schedule,
                authorName, authorEmail, repoRoot);
    }

    private static String required(Map<String, String> env, String key) {
        String value = get(env, key, null);
        if (value == null || value.isBlank() || value.contains("seu_token_aqui")) {
            throw new IllegalStateException(
                    "Defina " + key + " no ambiente ou no arquivo .env (veja .env.example)");
        }
        return value;
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
