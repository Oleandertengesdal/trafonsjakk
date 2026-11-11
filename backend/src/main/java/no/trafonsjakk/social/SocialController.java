package no.trafonsjakk.social;

import jakarta.validation.Valid;
import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.social.SocialDtos.CommentDto;
import no.trafonsjakk.social.SocialDtos.CommentRequest;
import no.trafonsjakk.social.SocialDtos.KudosDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}")
public class SocialController {

    private final SocialService social;

    public SocialController(SocialService social) {
        this.social = social;
    }

    @GetMapping("/comments")
    List<CommentDto> comments(@PathVariable long gameId, @AuthenticationPrincipal Jwt jwt) {
        return social.comments(gameId, CurrentUser.of(jwt));
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentDto add(@PathVariable long gameId, @Valid @RequestBody CommentRequest req, @AuthenticationPrincipal Jwt jwt) {
        return social.add(gameId, req.body(), CurrentUser.of(jwt));
    }

    @PatchMapping("/comments/{commentId}")
    CommentDto edit(@PathVariable long gameId, @PathVariable long commentId, @Valid @RequestBody CommentRequest req,
                    @AuthenticationPrincipal Jwt jwt) {
        return social.edit(gameId, commentId, req.body(), CurrentUser.of(jwt));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long gameId, @PathVariable long commentId, @AuthenticationPrincipal Jwt jwt) {
        social.delete(gameId, commentId, CurrentUser.of(jwt));
    }

    @GetMapping("/kudos")
    KudosDto kudos(@PathVariable long gameId, @AuthenticationPrincipal Jwt jwt) {
        return social.kudos(gameId, CurrentUser.of(jwt));
    }

    @PutMapping("/kudos")
    KudosDto give(@PathVariable long gameId, @AuthenticationPrincipal Jwt jwt) {
        return social.give(gameId, CurrentUser.of(jwt));
    }

    @DeleteMapping("/kudos")
    KudosDto take(@PathVariable long gameId, @AuthenticationPrincipal Jwt jwt) {
        return social.take(gameId, CurrentUser.of(jwt));
    }
}
