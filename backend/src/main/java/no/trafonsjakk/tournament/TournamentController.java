package no.trafonsjakk.tournament;

import jakarta.validation.Valid;
import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.AutoConfirmService;
import no.trafonsjakk.tournament.TournamentDtos.CreateTournamentRequest;
import no.trafonsjakk.tournament.TournamentDtos.MyMatchDto;
import no.trafonsjakk.tournament.TournamentDtos.ParticipantsRequest;
import no.trafonsjakk.tournament.TournamentDtos.TournamentDetail;
import no.trafonsjakk.tournament.TournamentDtos.TournamentSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournaments;
    private final AutoConfirmService autoConfirm;

    public TournamentController(TournamentService tournaments, AutoConfirmService autoConfirm) {
        this.tournaments = tournaments;
        this.autoConfirm = autoConfirm;
    }

    @GetMapping
    List<TournamentSummary> list() {
        autoConfirm.sweepIfDue();
        return tournaments.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TournamentDetail create(@Valid @RequestBody CreateTournamentRequest req, @AuthenticationPrincipal Jwt jwt) {
        return tournaments.create(req, CurrentUser.of(jwt));
    }

    @GetMapping("/matches/mine")
    List<MyMatchDto> myMatches(@AuthenticationPrincipal Jwt jwt) {
        autoConfirm.sweepIfDue();
        return tournaments.myMatches(CurrentUser.of(jwt));
    }

    @GetMapping("/{id}")
    TournamentDetail get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        autoConfirm.sweepIfDue();
        return tournaments.get(id, CurrentUser.of(jwt));
    }

    @PutMapping("/{id}/participants")
    TournamentDetail setParticipants(@PathVariable long id, @Valid @RequestBody ParticipantsRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        return tournaments.setParticipants(id, req.participantIds(), CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/join")
    TournamentDetail join(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tournaments.join(id, CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/leave")
    TournamentDetail leave(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tournaments.leave(id, CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/start")
    TournamentDetail start(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tournaments.start(id, CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/cancel")
    TournamentDetail cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return tournaments.cancel(id, CurrentUser.of(jwt));
    }
}
