package no.trafonsjakk.online;

import jakarta.validation.Valid;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.online.OnlineDtos.ChallengeDto;
import no.trafonsjakk.online.OnlineDtos.ChallengeRequest;
import no.trafonsjakk.online.OnlineDtos.Lobby;
import no.trafonsjakk.online.OnlineDtos.MoveRequest;
import no.trafonsjakk.online.OnlineDtos.OnlineGameDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/online")
public class OnlineGameController {

    private final OnlineGameService online;

    public OnlineGameController(OnlineGameService online) {
        this.online = online;
    }

    @GetMapping
    Lobby lobby(@AuthenticationPrincipal Jwt jwt) {
        return online.lobby(CurrentUser.of(jwt));
    }

    // ---- challenges ---------------------------------------------------------------------------------------

    @PostMapping("/challenges")
    @ResponseStatus(HttpStatus.CREATED)
    ChallengeDto challenge(@Valid @RequestBody ChallengeRequest req, @AuthenticationPrincipal Jwt jwt) {
        return online.challenge(req, CurrentUser.of(jwt));
    }

    @PostMapping("/challenges/{id}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    OnlineGameDto accept(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.accept(id, CurrentUser.of(jwt));
    }

    @PostMapping("/challenges/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        online.decline(id, CurrentUser.of(jwt));
    }

    @PostMapping("/challenges/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        online.cancel(id, CurrentUser.of(jwt));
    }

    // ---- games --------------------------------------------------------------------------------------------

    @GetMapping("/games/{id}")
    OnlineGameDto get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.get(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/moves")
    OnlineGameDto move(@PathVariable long id, @Valid @RequestBody MoveRequest req, @AuthenticationPrincipal Jwt jwt) {
        return online.move(id, req, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/resign")
    OnlineGameDto resign(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.resign(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/abort")
    OnlineGameDto abort(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.abort(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/offer-draw")
    OnlineGameDto offerDraw(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.offerDraw(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/accept-draw")
    OnlineGameDto acceptDraw(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.acceptDraw(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/decline-draw")
    OnlineGameDto declineDraw(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.declineDraw(id, CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/rematch")
    @ResponseStatus(HttpStatus.CREATED)
    ChallengeDto rematch(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return online.rematch(id, CurrentUser.of(jwt));
    }
}
