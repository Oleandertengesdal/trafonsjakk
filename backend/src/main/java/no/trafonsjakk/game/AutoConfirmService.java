package no.trafonsjakk.game;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import no.trafonsjakk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Auto-confirms results nobody responded to. Runs on a schedule, on startup, and lazily before reads
 * (free hosting sleeps when idle, so the schedule alone can't be trusted).
 */
@Service
public class AutoConfirmService {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmService.class);
    private static final long LAZY_THROTTLE_MS = 60_000;

    private final GameService gameService;
    private final GameRepository games;
    private final AppProperties props;
    private final AtomicLong lastLazyRun = new AtomicLong();

    public AutoConfirmService(GameService gameService, GameRepository games, AppProperties props) {
        this.gameService = gameService;
        this.games = games;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void scheduled() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    /** Cheap to call on every read: at most one check per minute. */
    public void sweepIfDue() {
        long now = System.currentTimeMillis();
        long last = lastLazyRun.get();
        if (now - last < LAZY_THROTTLE_MS || !lastLazyRun.compareAndSet(last, now)) {
            return;
        }
        run();
    }

    private void run() {
        try {
            Instant cutoff = Instant.now().minus(props.games().autoConfirmAfter());
            if (games.existsOverduePending(cutoff)) {
                int n = gameService.confirmOverdue(cutoff);
                log.info("Auto-confirmed {} game(s)", n);
            }
        } catch (RuntimeException e) {
            log.warn("Auto-confirm sweep failed", e);
        }
    }
}
