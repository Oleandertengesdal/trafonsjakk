package no.trafonsjakk.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.user.UserDtos.Me;
import no.trafonsjakk.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final TokenService tokens;
    private final AuthCookies cookies;
    private final UserRepository users;
    private final PasswordResetService resets;

    public AuthController(AuthService auth, TokenService tokens, AuthCookies cookies, UserRepository users,
                          PasswordResetService resets) {
        this.resets = resets;
        this.auth = auth;
        this.tokens = tokens;
        this.cookies = cookies;
        this.users = users;
    }

    /** Primes the XSRF-TOKEN cookie (written by the CSRF filter on every response). */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    Me login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest request,
             HttpServletResponse response) {
        TokenService.IssuedTokens issued = auth.login(req.username(), req.password(), ClientAddress.of(request));
        cookies.write(response, issued);
        return Me.of(issued.user());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Me register(@Valid @RequestBody AuthDtos.RegisterRequest req, HttpServletRequest request,
                HttpServletResponse response) {
        TokenService.IssuedTokens issued = auth.register(req, ClientAddress.of(request));
        cookies.write(response, issued);
        return Me.of(issued.user());
    }

    @PostMapping("/password-reset/check")
    PasswordResetService.ResetTarget checkReset(@Valid @RequestBody AuthDtos.ResetCheckRequest req) {
        return resets.check(req.token());
    }

    @PostMapping("/password-reset")
    Me resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req, HttpServletResponse response) {
        TokenService.IssuedTokens issued = resets.reset(req.token(), req.newPassword());
        cookies.write(response, issued);
        return Me.of(issued.user());
    }

    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(@CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refresh,
                                 HttpServletResponse response) {
        try {
            cookies.write(response, tokens.rotate(refresh));
        } catch (ApiException e) {
            if (!TokenService.REFRESH_RACED.equals(e.code())) {
                cookies.clear(response);
            }
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refresh,
                                HttpServletResponse response) {
        tokens.revoke(refresh);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.of(jwt).id())
                .map(Me::of)
                .orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
    }
}
