package no.trafonsjakk.auth;

import java.util.List;
import no.trafonsjakk.common.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService invites;

    public InviteController(InviteService invites) {
        this.invites = invites;
    }

    @GetMapping
    List<InviteService.InviteView> list(@AuthenticationPrincipal Jwt jwt) {
        return invites.list(CurrentUser.of(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InviteService.CreatedInvite create(@AuthenticationPrincipal Jwt jwt) {
        return invites.create(CurrentUser.of(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        invites.revoke(id, CurrentUser.of(jwt));
    }
}
