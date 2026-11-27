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

    /** {@code rating}, {@code rd} … are over the board; {@code online} is the separate online rating. */
    public record Profile(long id, String username, String realName, int rating, int rd, boolean provisional,
                          Role role, Integer rank, Instant memberSince, int ratedGames, int peakRating,
                          OnlineRating online) {
    }

    /** {@code games}: rated online games. {@code peak}: the highest established online rating. */
    public record OnlineRating(int rating, int rd, boolean provisional, int games, int peak, Integer rank) {
        public static OnlineRating of(User u, Integer rank) {
            int peak = (int) Math.round(Math.max(u.getOnlinePeakRating(), u.isOnlineProvisional() ? 0 : u.getOnlineRating()));
            return new OnlineRating((int) Math.round(u.getOnlineRating()), (int) Math.round(u.getOnlineRatingDeviation()),
                    u.isOnlineProvisional(), u.getOnlineRatedGames(), peak, rank);
        }
    }

    public record UpdateMeRequest(
            @NotBlank @Size(max = 60) @PlainText String realName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotNull @Size(min = 10, max = 72, message = "must be 10–72 characters") String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }

    /** {@code deleted}: the person deleted their account; it can't be switched back on. */
    public record AdminUser(long id, String username, String realName, Role role, boolean enabled,
                            Instant createdAt, int ratedGames, int rating, boolean deleted) {
        public static AdminUser of(User u) {
            return new AdminUser(u.getId(), u.getUsername(), u.getRealName(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getRatedGames(), (int) Math.round(u.getRating()), u.getDeletedAt() != null);
        }
    }

    public record AdminUpdateUserRequest(Role role, Boolean enabled) {
    }
}
