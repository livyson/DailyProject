package com.dailyproject;

import java.nio.file.Path;

/**
 * Entrypoint do DailyProject.
 *
 * <pre>
 *   java -jar target/daily-project-1.0.0.jar --open    # gera + commit develop + PR + comentário + discussão
 *   java -jar target/daily-project-1.0.0.jar --merge   # aprova/mergeia PR aberto
 *   java -jar target/daily-project-1.0.0.jar --once    # ciclo completo (open + merge)
 *   java -jar target/daily-project-1.0.0.jar           # agenda local (só open; merge via --merge)
 * </pre>
 */
public final class App {

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath().normalize();
        Config config = Config.load(repoRoot);
        DailyWorkflow workflow = new DailyWorkflow(config);

        String mode = resolveMode(args, config);
        switch (mode) {
            case "open" -> workflow.openPullRequestCycle();
            case "merge" -> workflow.mergeOpenPullRequest();
            case "once" -> workflow.runOnce();
            case "schedule" -> {
                System.out.println("DailyProject em modo agenda local (open nos horários).");
                System.out.println("Merge: rode com --merge ou use o workflow daily-merge.yml.");
                DailyScheduler scheduler = new DailyScheduler(config, workflow);
                scheduler.start();
                Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
                Thread.currentThread().join();
            }
            default -> throw new IllegalArgumentException("Modo desconhecido: " + mode
                    + " (use --open, --merge, --once ou sem args para agenda)");
        }
    }

    private static String resolveMode(String[] args, Config config) {
        if (args.length > 0) {
            String a = args[0].trim().toLowerCase();
            if (a.startsWith("--")) {
                a = a.substring(2);
            }
            return switch (a) {
                case "open", "pr" -> "open";
                case "merge", "close" -> "merge";
                case "once", "full", "all" -> "once";
                case "schedule", "daemon" -> "schedule";
                default -> a;
            };
        }
        // No Actions, default é abrir PR (visível). Merge é outro workflow.
        if (config.githubActions()) {
            return "open";
        }
        return "schedule";
    }

    private App() {
    }
}
