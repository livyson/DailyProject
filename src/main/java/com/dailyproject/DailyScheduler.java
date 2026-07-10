package com.dailyproject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agenda até três execuções por dia nos horários configurados.
 */
public final class DailyScheduler {

    private final Config config;
    private final DailyWorkflow workflow;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DailyScheduler(Config config, DailyWorkflow workflow) {
        this.config = config;
        this.workflow = workflow;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "daily-scheduler");
            t.setDaemon(false);
            return t;
        });
    }

    public void start() {
        List<LocalTime> times = config.scheduleTimes();
        System.out.println("[scheduler] horários: " + times);
        scheduleNext();
    }

    private void scheduleNext() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = nextSlot(now, config.scheduleTimes());
        long delayMs = Duration.between(now, next).toMillis();
        if (delayMs < 0) {
            delayMs = 0;
        }
        System.out.println("[scheduler] próxima execução em " + next + " (daqui a "
                + Duration.ofMillis(delayMs).toMinutes() + " min)");

        executor.schedule(() -> {
            try {
                executeSafely();
            } finally {
                scheduleNext();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void executeSafely() {
        if (!running.compareAndSet(false, true)) {
            System.out.println("[scheduler] execução anterior ainda em andamento — pulando slot");
            return;
        }
        try {
            workflow.openPullRequestCycle();
        } catch (Exception e) {
            System.err.println("[scheduler] falha na execução: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            running.set(false);
        }
    }

    static LocalDateTime nextSlot(LocalDateTime now, List<LocalTime> times) {
        LocalDate today = now.toLocalDate();
        for (LocalTime t : times.stream().sorted().toList()) {
            LocalDateTime candidate = LocalDateTime.of(today, t);
            if (candidate.isAfter(now)) {
                return candidate;
            }
        }
        LocalTime first = times.stream().sorted().findFirst().orElse(LocalTime.of(9, 0));
        return LocalDateTime.of(today.plusDays(1), first);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
