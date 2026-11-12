package no.trafonsjakk.award;

import java.time.Instant;
import java.util.List;
import no.trafonsjakk.game.AutoConfirmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/awards")
public class AwardController {

    private final AwardService awards;
    private final AutoConfirmService autoConfirm;

    public AwardController(AwardService awards, AutoConfirmService autoConfirm) {
        this.awards = awards;
        this.autoConfirm = autoConfirm;
    }

    /** Awards that were won, newest first. {@code player}: only that person's (for the profile). */
    @GetMapping
    List<AwardService.Award> list(@RequestParam(required = false) String player) {
        ready();
        return awards.won(player);
    }

    /** Standings for a month (default: this one) and its winners once decided. */
    @GetMapping("/race")
    AwardService.Race race(@RequestParam(required = false) String month) {
        ready();
        return awards.race(month);
    }

    /** Overdue results are confirmed and finished months decided before anyone reads them. */
    private void ready() {
        autoConfirm.sweepIfDue();
        awards.decideDueMonths(Instant.now());
    }
}
