package com.dailyproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Orquestra o fluxo diário em duas fases:
 * <ul>
 *   <li>{@link #openPullRequestCycle()} — gera código, commit em develop, abre PR, comenta e discute</li>
 *   <li>{@link #mergeOpenPullRequest()} — aprova (se possível) e mergeia o PR aberto</li>
 * </ul>
 */
public final class DailyWorkflow {

    private static final DateTimeFormatter COMMIT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Config config;
    private final CodeGenerator generator;
    private final GitService git;
    private final GitHubService github;

    public DailyWorkflow(Config config) {
        this.config = config;
        this.generator = new CodeGenerator(config.repoRoot(), config.minLines());
        this.git = new GitService(config);
        this.github = new GitHubService(config);
    }

    /** Compat: ciclo completo (abrir + merge). Preferir {@link #openPullRequestCycle()} no CI. */
    public void runOnce() throws Exception {
        openPullRequestCycle();
        mergeOpenPullRequest();
    }

    /**
     * Gera ≥100 linhas, commit/push em develop, abre PR, comenta e abre discussão.
     * Não mergeia — o PR permanece aberto.
     */
    public void openPullRequestCycle() throws Exception {
        System.out.println("=== DailyProject OPEN @ " + LocalDateTime.now() + " ===");
        System.out.println(config);

        git.ensureBranchesReady();

        CodeGenerator.GenerationResult gen = generator.generate();
        System.out.println("[gen] " + gen.className() + " (" + gen.lineCount() + " linhas) → " + gen.file());

        if (gen.lineCount() < config.minLines()) {
            throw new IllegalStateException("Geração abaixo do mínimo: " + gen.lineCount() + " < " + config.minLines());
        }

        String commitMessage = "feat(daily): add " + gen.className() + " (" + gen.lineCount() + " lines) @ "
                + LocalDateTime.now().format(COMMIT_TS);
        git.commitGeneratedCode(gen.file(), commitMessage);
        String sha = git.latestCommitSha();
        System.out.println("[git] commit " + sha + " em " + config.sourceBranch());

        String prTitle = "Daily update: " + gen.className();
        String prBody = PersonaComments.pullRequestBody(gen.className(), gen.lineCount(), sha);
        GitHubService.PullRequest pr = github.ensurePullRequest(prTitle, prBody);

        String comment = PersonaComments.prComment(gen.className(), gen.lineCount());
        github.commentOnPullRequest(pr.number(), comment);

        String discussionUrl = null;
        try {
            discussionUrl = github.createDiscussion(
                    PersonaComments.discussionTitle(java.time.LocalDate.now()),
                    PersonaComments.discussionBody(pr.url(), gen.className(), gen.lineCount())
            );
        } catch (Exception e) {
            System.err.println("[github] Discussão falhou (o PR segue aberto): " + e.getMessage());
        }

        System.out.println("[flow] PR aberto #" + pr.number() + " → " + pr.url()
                + (discussionUrl != null ? " | discussão: " + discussionUrl : ""));
        System.out.println("[flow] Merge fica para o job/comando --merge (PR permanece visível).");
    }

    /**
     * Localiza PR aberto develop→main, tenta aprovar e faz squash merge.
     */
    public void mergeOpenPullRequest() throws Exception {
        System.out.println("=== DailyProject MERGE @ " + LocalDateTime.now() + " ===");
        System.out.println(config);

        Optional<GitHubService.PullRequest> open = github.findOpenPullRequest();
        if (open.isEmpty()) {
            System.out.println("[flow] Nenhum PR aberto de " + config.sourceBranch()
                    + " → " + config.targetBranch() + ". Nada a mergear.");
            return;
        }

        GitHubService.PullRequest pr = open.get();
        System.out.println("[github] PR aberto encontrado: #" + pr.number() + " " + pr.url());

        github.approveAndMerge(pr.number(), PersonaComments.mergeTitle("PR-" + pr.number()));

        try {
            git.ensureBranchesReady();
            git.syncSourceWithTargetAfterMerge();
        } catch (Exception e) {
            System.err.println("[git] sync pós-merge falhou: " + e.getMessage());
        }

        System.out.println("[flow] merge concluído. PR #" + pr.number());
    }
}
