package no.trafonsjakk.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHash(@Param("hash") String hash);

    /** Row-locked, so a link can't be used twice by two requests at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Cancels every open link for a user (a new link replaces the old; disabling cancels all). */
    @Modifying
    @Query("update PasswordResetToken t set t.revokedAt = :now where t.user.id = :userId "
            + "and t.usedAt is null and t.revokedAt is null")
    int revokeOpenForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
