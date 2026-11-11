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

    /** {@code parentId}: answer in that comment's thread (replying to a reply stays in the same thread). */
    public record CommentRequest(@NotBlank @Size(max = 1000) @PlainText String body, Long parentId) {
    }

    /** {@code author} is null and {@code body} empty for a deleted comment kept for its replies. */
    public record CommentDto(long id, Long parentId, PlayerRef author, PlayerRef replyTo, String body, Instant createdAt,
                             Instant editedAt, boolean deleted, int likeCount, boolean likedByMe, List<PlayerRef> likedBy,
                             boolean canEdit, boolean canDelete, boolean canLike, List<CommentDto> replies) {
    }

    public record CommentLikes(int likeCount, boolean likedByMe, List<PlayerRef> likedBy) {
    }

    public record KudosDto(int count, boolean givenByMe, List<PlayerRef> givers) {
    }
}
