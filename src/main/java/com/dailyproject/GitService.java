package com.dailyproject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Operações Git locais via processo {@code git}.
 */
public final class GitService {

    private final Path repoRoot;
    private final String authorName;
    private final String authorEmail;
    private final String sourceBranch;
    private final String targetBranch;

    public GitService(Config config) {
        this.repoRoot = config.repoRoot();
        this.authorName = config.authorName();
        this.authorEmail = config.authorEmail();
        this.sourceBranch = config.sourceBranch();
        this.targetBranch = config.targetBranch();
    }

    public void ensureBranchesReady() throws IOException, InterruptedException {
        run("git", "config", "user.name", authorName);
        run("git", "config", "user.email", authorEmail);

        String current = quiet("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
        boolean hasCommits = quietExit("git", "rev-parse", "--verify", "HEAD") == 0;

        if (!hasCommits) {
            // Repositório vazio: primeiro commit em main, depois cria develop.
            Path keep = repoRoot.resolve(".gitkeep");
            if (!java.nio.file.Files.exists(keep)) {
                java.nio.file.Files.writeString(keep, "", StandardCharsets.UTF_8);
            }
            run("git", "add", "-A");
            run("git", "commit", "-m", "chore: bootstrap initial main branch");
            run("git", "branch", "-M", targetBranch);
            push(targetBranch);
            run("git", "checkout", "-B", sourceBranch);
            push(sourceBranch);
            return;
        }

        fetch();

        if (!branchExistsLocal(sourceBranch) && !branchExistsRemote(sourceBranch)) {
            checkout(targetBranch);
            run("git", "checkout", "-B", sourceBranch);
            push(sourceBranch);
        } else {
            ensureCheckout(sourceBranch);
            tryPull(sourceBranch);
        }
    }

    public void commitGeneratedCode(Path relativeOrAbsoluteFile, String message)
            throws IOException, InterruptedException {
        ensureCheckout(sourceBranch);
        String pathArg = repoRoot.relativize(
                relativeOrAbsoluteFile.isAbsolute()
                        ? relativeOrAbsoluteFile
                        : repoRoot.resolve(relativeOrAbsoluteFile)
        ).toString().replace('\\', '/');

        run("git", "add", pathArg);
        // Garante que há algo a commitar (às vezes pathspec precisa ser pasta)
        run("git", "add", "src/main/java/com/dailyproject/generated/");
        int dirty = quietExit("git", "diff", "--cached", "--quiet");
        if (dirty == 0) {
            throw new IllegalStateException("Nada staged para commit em " + sourceBranch);
        }
        run("git", "commit", "-m", message);
        push(sourceBranch);
    }

    public String latestCommitSha() throws IOException, InterruptedException {
        return quiet("git", "rev-parse", "HEAD").trim();
    }

    public void fetch() throws IOException, InterruptedException {
        run("git", "fetch", "origin");
    }

    /**
     * Após squash-merge, realinha {@code develop} com {@code main} para o próximo ciclo.
     */
    public void syncSourceWithTargetAfterMerge() throws IOException, InterruptedException {
        fetch();
        run("git", "checkout", targetBranch);
        run("git", "pull", "--ff-only", "origin", targetBranch);
        run("git", "checkout", "-B", sourceBranch, targetBranch);
        run("git", "push", "--force-with-lease", "origin", sourceBranch);
        System.out.println("[git] " + sourceBranch + " realinhada com " + targetBranch);
    }

    private void push(String branch) throws IOException, InterruptedException {
        run("git", "push", "-u", "origin", branch);
    }

    private void tryPull(String branch) throws IOException, InterruptedException {
        int code = quietExit("git", "pull", "--ff-only", "origin", branch);
        if (code != 0) {
            System.out.println("[git] pull --ff-only falhou (continuando com estado local)");
        }
    }

    private void ensureCheckout(String branch) throws IOException, InterruptedException {
        String current = quiet("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
        if (branch.equals(current)) {
            return;
        }
        if (branchExistsLocal(branch)) {
            checkout(branch);
        } else if (branchExistsRemote(branch)) {
            run("git", "checkout", "-B", branch, "origin/" + branch);
        } else {
            checkout(targetBranch);
            run("git", "checkout", "-B", branch);
        }
    }

    private void checkout(String branch) throws IOException, InterruptedException {
        run("git", "checkout", branch);
    }

    private boolean branchExistsLocal(String branch) throws IOException, InterruptedException {
        return quietExit("git", "show-ref", "--verify", "--quiet", "refs/heads/" + branch) == 0;
    }

    private boolean branchExistsRemote(String branch) throws IOException, InterruptedException {
        return quietExit("git", "show-ref", "--verify", "--quiet", "refs/remotes/origin/" + branch) == 0;
    }

    private void run(String... command) throws IOException, InterruptedException {
        ProcessResult result = execute(command, true);
        if (result.exitCode() != 0) {
            throw new IOException("Comando falhou (" + result.exitCode() + "): "
                    + String.join(" ", command) + "\n" + result.output());
        }
        if (!result.output().isBlank()) {
            System.out.println(result.output().trim());
        }
    }

    private String quiet(String... command) throws IOException, InterruptedException {
        return execute(command, false).output();
    }

    private int quietExit(String... command) throws IOException, InterruptedException {
        return execute(command, false).exitCode();
    }

    private ProcessResult execute(String[] command, boolean inheritError)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timeout ao executar: " + String.join(" ", command));
        }
        int code = process.exitValue();
        if (inheritError && code != 0 && !output.isBlank()) {
            System.err.println(output);
        }
        return new ProcessResult(code, output);
    }

    private record ProcessResult(int exitCode, String output) {
    }

    public List<String> recentLog(int n) throws IOException, InterruptedException {
        String out = quiet("git", "log", "-" + n, "--oneline");
        List<String> lines = new ArrayList<>();
        for (String line : out.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }
}
