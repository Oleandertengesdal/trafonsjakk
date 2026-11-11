package no.trafonsjakk.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import no.trafonsjakk.common.validation.PlainText;
import no.trafonsjakk.user.PlayerRef;

public final class SocialDtos {

    private SocialDtos() {
    }

    public record CommentRequest(@NotBlank @Size(max = 1000) @PlainText String body) {
    }

    public record CommentDto(long id, PlayerRef author, String body, Instant createdAt, Instant editedAt,
                             boolean canEdit, boolean canDelete) {
    }

    public record KudosDto(int count, boolean givenByMe, List<PlayerRef> givers) {
    }
}
