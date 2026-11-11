package no.trafonsjakk.game;

import jakarta.validation.Valid;
import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.GameDtos.CreateGameRequest;
import no.trafonsjakk.game.GameDtos.DisputeRequest;
import no.trafonsjakk.game.GameDtos.GameDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService games;
    private final AutoConfirmService autoConfirm;
    private final GameContextService context;

    public GameController(GameService games, AutoConfirmService autoConfirm, GameContextService context) {
        this.games = games;
        this.autoConfirm = autoConfirm;
        this.context = context;
    }

    @GetMapping
    GameService.PageDto<GameDto> list(@RequestParam(required = false) String player,
                                      @RequestParam(required = false) GameStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @AuthenticationPrincipal Jwt jwt) {
        autoConfirm.sweepIfDue();
        return games.list(player, status, page, size, CurrentUser.of(jwt));
    }

    @GetMapping("/pending")
    List<GameDto> pending(@AuthenticationPrincipal Jwt jwt) {
        autoConfirm.sweepIfDue();
        return games.awaitingMyConfirmation(CurrentUser.of(jwt));
    }

    @GetMapping("/{id}")
    GameDto get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        autoConfirm.sweepIfDue();
        return games.get(id, CurrentUser.of(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GameDto create(@Valid @RequestBody CreateGameRequest req, @AuthenticationPrincipal Jwt jwt) {
        return games.create(req, CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/confirm")
    GameDto confirm(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return games.confirm(id, CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/dispute")
    GameDto dispute(@PathVariable long id, @Valid @RequestBody DisputeRequest req, @AuthenticationPrincipal Jwt jwt) {
        return games.dispute(id, req.reason(), CurrentUser.of(jwt));
    }

    /** Expected score, upset and head-to-head for the game page. */
    @GetMapping("/{id}/context")
    GameContextService.GameContext context(@PathVariable long id) {
        return context.context(id);
    }

    @PatchMapping("/{id}/moves")
    GameDto updateMoves(@PathVariable long id, @Valid @RequestBody GameDtos.MovesRequest req, @AuthenticationPrincipal Jwt jwt) {
        return games.updateMoves(id, req.moves(), CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/withdraw")
    GameDto withdraw(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return games.withdraw(id, CurrentUser.of(jwt));
    }
}
