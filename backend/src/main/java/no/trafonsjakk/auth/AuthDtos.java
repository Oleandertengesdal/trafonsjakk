package no.trafonsjakk.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.trafonsjakk.common.validation.PlainText;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 20) @PlainText String username,
            @NotBlank @Size(max = 72) String password) {
    }

    public record RegisterRequest(
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                    message = "3–20 characters: letters, digits and underscore") String username,
            @NotBlank @Size(max = 60) @PlainText String realName,
            @NotNull @Size(min = 10, max = 72, message = "must be 10–72 characters") String password,
            @NotBlank @Size(max = 100) String inviteCode) {
    }

    public record ResetCheckRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotNull @Size(min = 10, max = 72, message = "must be 10–72 characters") String newPassword) {
    }
}
