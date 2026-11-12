package no.trafonsjakk.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.auth.AuthCookies;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.AutoConfirmService;
import no.trafonsjakk.stats.StatsService;
import no.trafonsjakk.user.UserDtos.ChangePasswordRequest;
import no.trafonsjakk.user.UserDtos.DeleteAccountRequest;
import no.trafonsjakk.user.UserDtos.Me;
import no.trafonsjakk.user.UserDtos.Profile;
import no.trafonsjakk.user.UserDtos.UpdateMeRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final StatsService stats;
    private final AuthCookies cookies;
    private final AutoConfirmService autoConfirm;
    private final AccountService accounts;

    public UserController(UserService userService, StatsService stats, AuthCookies cookies,
                          AutoConfirmService autoConfirm, AccountService accounts) {
        this.accounts = accounts;
        this.userService = userService;
        this.stats = stats;
        this.cookies = cookies;
        this.autoConfirm = autoConfirm;
    }

    @GetMapping
    List<PlayerRef> list() {
        return userService.activePlayers();
    }

    @GetMapping("/{username}")
    Profile profile(@PathVariable String username) {
        autoConfirm.sweepIfDue();
        return stats.profile(username);
    }

    @GetMapping("/{username}/stats")
    StatsService.Stats stats(@PathVariable String username) {
        autoConfirm.sweepIfDue();
        return stats.stats(username);
    }

    @PatchMapping("/me")
    Me updateMe(@Valid @RequestBody UpdateMeRequest req, @AuthenticationPrincipal Jwt jwt) {
        return userService.updateRealName(CurrentUser.of(jwt), req.realName());
    }

    @PostMapping("/me/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                        @AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
                                        HttpServletResponse response) {
        cookies.write(response, userService.changePassword(CurrentUser.of(jwt), req.currentPassword(),
                req.newPassword(), ClientAddress.of(request)));
        return ResponseEntity.noContent().build();
    }

    /** Everything stored about you, as a JSON file. */
    @GetMapping("/me/export")
    ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trafonsjakk-data.json\"")
                .body(accounts.export(CurrentUser.of(jwt)));
    }

    /** Deletes your account (see AccountService) and logs you out. */
    @PostMapping("/me/delete")
    ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req, @AuthenticationPrincipal Jwt jwt,
                                       HttpServletRequest request, HttpServletResponse response) {
        accounts.delete(CurrentUser.of(jwt), req.password(), ClientAddress.of(request));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
