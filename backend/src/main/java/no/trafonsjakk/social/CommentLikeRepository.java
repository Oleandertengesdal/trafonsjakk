package no.trafonsjakk.social;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Likes on comments: a plain (comment, person) pair; "on conflict do nothing" makes double taps harmless. */
@Repository
public class CommentLikeRepository {

    private final JdbcTemplate jdbc;

    public CommentLikeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void like(long commentId, long userId) {
        jdbc.update("insert into comment_likes (comment_id, user_id, created_at) values (?, ?, now()) on conflict do nothing",
                commentId, userId);
    }

    public void unlike(long commentId, long userId) {
        jdbc.update("delete from comment_likes where comment_id = ? and user_id = ?", commentId, userId);
    }

    public void deleteAll(long commentId) {
        jdbc.update("delete from comment_likes where comment_id = ?", commentId);
    }

    /** comment id → ids of the people who liked it, earliest first. */
    public Map<Long, List<Long>> likers(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        record Row(long commentId, long userId) {
        }
        return jdbc.query("select comment_id, user_id from comment_likes where comment_id = any (?) order by created_at, user_id",
                        (rs, i) -> new Row(rs.getLong(1), rs.getLong(2)), (Object) commentIds.toArray(Long[]::new))
                .stream()
                .collect(Collectors.groupingBy(Row::commentId, Collectors.mapping(Row::userId, Collectors.toList())));
    }
}
