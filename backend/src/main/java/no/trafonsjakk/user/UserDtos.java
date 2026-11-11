package no.trafonsjakk.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import no.trafonsjakk.common.validation.PlainText;

public final class UserDtos {

    private UserDtos() {
    }

    /** The logged-in user. */
    public record Me(long id, String username, String realName, int rating, int rd, boolean provisional,
                     Role role, int ratedGames) {
        public static Me of(User u) {
            PlayerRef p = PlayerRef.of(u);
            return new Me(p.id(), p.username(), p.realName(), p.rating(), p.rd(), p.provisional(), u.getRole(),
                    u.getRatedGames());
        }
    }

    public record Profile(long id, String username, String realName, int rating, int rd, boolean provisional,
                          Role role, Integer rank, Instant memberSince, int ratedGames, int peakRating) {
    }

    public record UpdateMeRequest(
            @NotBlank @Size(max = 60) @PlainText String realName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotNull @Size(min = 10, max = 72, message = "must be 10–72 characters") String newPassword) {
    }

    public record AdminUser(long id, String username, String realName, Role role, boolean enabled,
                            Instant createdAt, int ratedGames, int rating) {
        public static AdminUser of(User u) {
            return new AdminUser(u.getId(), u.getUsername(), u.getRealName(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getRatedGames(), (int) Math.round(u.getRating()));
        }
    }

    public record AdminUpdateUserRequest(Role role, Boolean enabled) {
    }
}
