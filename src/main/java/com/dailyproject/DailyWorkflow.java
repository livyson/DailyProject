package com.dailyproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Orquestra o fluxo completo de uma execução:
 * gerar código → commit develop → PR → comentar → discussão → aprovar → merge.
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

    public void runOnce() throws Exception {
        System.out.println("=== DailyProject run @ " + LocalDateTime.now() + " ===");
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

        String discussionUrl;
        try {
            discussionUrl = github.createDiscussion(
                    PersonaComments.discussionTitle(java.time.LocalDate.now()),
                    PersonaComments.discussionBody(pr.url(), gen.className(), gen.lineCount())
            );
        } catch (Exception e) {
            System.err.println("[github] Discussão falhou (o PR segue): " + e.getMessage());
            discussionUrl = null;
        }

        github.approveAndMerge(pr.number(), PersonaComments.mergeTitle(gen.className()));

        try {
            git.syncSourceWithTargetAfterMerge();
        } catch (Exception e) {
            System.err.println("[git] sync pós-merge falhou: " + e.getMessage());
        }

        System.out.println("[flow] ciclo concluído. PR #" + pr.number()
                + (discussionUrl != null ? " | discussão: " + discussionUrl : ""));
    }
}
