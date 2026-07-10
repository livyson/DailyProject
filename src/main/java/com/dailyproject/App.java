package com.dailyproject;

import java.nio.file.Path;

/**
 * Entrypoint do DailyProject.
 *
 * <pre>
 *   mvn -q package
 *   java -jar target/daily-project-1.0.0.jar           # agenda 3x/dia
 *   java -jar target/daily-project-1.0.0.jar --once    # executa uma vez agora
 * </pre>
 */
public final class App {

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath().normalize();
        Config config = Config.load(repoRoot);
        DailyWorkflow workflow = new DailyWorkflow(config);

        boolean once = args.length > 0 && ("--once".equals(args[0]) || "once".equalsIgnoreCase(args[0]));
        if (once) {
            workflow.runOnce();
            return;
        }

        System.out.println("DailyProject em modo agenda. Use --once para uma execução imediata.");
        DailyScheduler scheduler = new DailyScheduler(config, workflow);
        scheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
        // Mantém a JVM viva
        Thread.currentThread().join();
    }

    private App() {
    }
}
