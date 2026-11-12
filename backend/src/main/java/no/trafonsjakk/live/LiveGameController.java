package no.trafonsjakk.live;

import jakarta.validation.Valid;
import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.live.LiveDtos.FinishLiveRequest;
import no.trafonsjakk.live.LiveDtos.LiveGameDto;
import no.trafonsjakk.live.LiveDtos.SaveMovesRequest;
import no.trafonsjakk.live.LiveDtos.StartLiveRequest;
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
@RequestMapping("/api/live")
public class LiveGameController {

    private final LiveGameService live;

    public LiveGameController(LiveGameService live) {
        this.live = live;
    }

    @GetMapping
    List<LiveGameDto> running(@AuthenticationPrincipal Jwt jwt) {
        return live.running(CurrentUser.of(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LiveGameDto start(@Valid @RequestBody StartLiveRequest req, @AuthenticationPrincipal Jwt jwt) {
        return live.start(req, CurrentUser.of(jwt));
    }

    @GetMapping("/{id}")
    LiveGameDto get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return live.get(id, CurrentUser.of(jwt));
    }

    @PutMapping("/{id}/moves")
    LiveGameDto saveMoves(@PathVariable long id, @Valid @RequestBody SaveMovesRequest req, @AuthenticationPrincipal Jwt jwt) {
        return live.saveMoves(id, req.moves(), req.version(), CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/finish")
    @ResponseStatus(HttpStatus.CREATED)
    GameDto finish(@PathVariable long id, @Valid @RequestBody FinishLiveRequest req, @AuthenticationPrincipal Jwt jwt) {
        return live.finish(id, req.result(), CurrentUser.of(jwt));
    }

    @PostMapping("/{id}/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abandon(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        live.abandon(id, CurrentUser.of(jwt));
    }
}
