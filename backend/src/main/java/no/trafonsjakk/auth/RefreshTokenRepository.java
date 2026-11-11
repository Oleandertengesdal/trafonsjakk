package no.trafonsjakk.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /**
     * The role of an enabled user whose login session is still live (has an unrevoked, unexpired
     * refresh token). Empty if the account is disabled or the session was ended.
     */
    @Query("select u.role from User u where u.id = :userId and u.enabled = true and exists ("
            + "select t.id from RefreshToken t where t.userId = :userId and t.familyId = :sid "
            + "and t.revokedAt is null and t.expiresAt > :now)")
    Optional<no.trafonsjakk.user.Role> findLiveSessionRole(@Param("userId") Long userId, @Param("sid") UUID sid,
                                                           @Param("now") Instant now);
}
