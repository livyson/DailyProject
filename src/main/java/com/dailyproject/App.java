package com.dailyproject;

import java.nio.file.Path;

/**
 * Entrypoint do DailyProject.
 *
 * <pre>
 *   mvn -q package
 *   java -jar target/daily-project-1.0.0.jar --once    # uma execução (CI / teste)
 *   java -jar target/daily-project-1.0.0.jar           # agenda local 3x/dia
 * </pre>
 *
 * No GitHub Actions ({@code GITHUB_ACTIONS=true}) sempre executa uma vez e encerra.
 */
public final class App {

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath().normalize();
        Config config = Config.load(repoRoot);
        DailyWorkflow workflow = new DailyWorkflow(config);

        boolean once = config.githubActions()
                || (args.length > 0 && ("--once".equals(args[0]) || "once".equalsIgnoreCase(args[0])));
        if (once) {
            workflow.runOnce();
            return;
        }

        System.out.println("DailyProject em modo agenda local. No GitHub use Actions (daily.yml).");
        System.out.println("Use --once para uma execução imediata.");
        DailyScheduler scheduler = new DailyScheduler(config, workflow);
        scheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
        Thread.currentThread().join();
    }

    private App() {
    }
}
