package no.trafonsjakk.review;

import java.time.Instant;
import no.trafonsjakk.award.AwardService;
import no.trafonsjakk.game.AutoConfirmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/year/{year}")
public class YearReviewController {

    private final YearReviewService review;
    private final AutoConfirmService autoConfirm;
    private final AwardService awards;

    public YearReviewController(YearReviewService review, AutoConfirmService autoConfirm, AwardService awards) {
        this.review = review;
        this.autoConfirm = autoConfirm;
        this.awards = awards;
    }

    @GetMapping
    YearReviewDtos.GroupYear group(@PathVariable int year) {
        ready();
        return review.group(year);
    }

    @GetMapping("/players/{username}")
    YearReviewDtos.PlayerYear player(@PathVariable int year, @PathVariable String username) {
        ready();
        return review.player(year, username);
    }

    private void ready() {
        autoConfirm.sweepIfDue();
        awards.decideDueMonths(Instant.now());
    }
}
