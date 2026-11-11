package no.trafonsjakk.admin;

import jakarta.validation.Valid;
import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.game.GameDtos.ResolveRequest;
import no.trafonsjakk.game.GameService;
import no.trafonsjakk.rating.RatingService;
import no.trafonsjakk.user.UserDtos.AdminUpdateUserRequest;
import no.trafonsjakk.user.UserDtos.AdminUser;
import no.trafonsjakk.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only endpoints. Access is enforced by SecurityConfig ({@code /api/admin/**} → ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final GameService games;
    private final UserService users;
    private final RatingService ratings;

    public AdminController(GameService games, UserService users, RatingService ratings) {
        this.games = games;
        this.users = users;
        this.ratings = ratings;
    }

    @GetMapping("/games/disputed")
    List<GameDto> disputed(@AuthenticationPrincipal Jwt jwt) {
        return games.disputed(CurrentUser.of(jwt));
    }

    @PostMapping("/games/{id}/resolve")
    GameDto resolve(@PathVariable long id, @Valid @RequestBody ResolveRequest req, @AuthenticationPrincipal Jwt jwt) {
        return games.resolve(id, req, CurrentUser.of(jwt));
    }

    @GetMapping("/users")
    List<AdminUser> users() {
        return users.allForAdmin();
    }

    @PatchMapping("/users/{id}")
    AdminUser updateUser(@PathVariable long id, @RequestBody AdminUpdateUserRequest req,
                         @AuthenticationPrincipal Jwt jwt) {
        return users.adminUpdate(id, req, CurrentUser.of(jwt));
    }

    @PostMapping("/ratings/recompute")
    RatingService.RecomputeSummary recompute() {
        return ratings.recomputeNow();
    }
}
