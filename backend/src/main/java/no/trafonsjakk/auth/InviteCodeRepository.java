package no.trafonsjakk.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    /** Row-locked so two people can't redeem the same code concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteCode i where i.codeHash = :hash")
    Optional<InviteCode> findByCodeHashForUpdate(@Param("hash") String hash);

    @Query("select i from InviteCode i left join fetch i.createdBy "
            + "where i.createdBy.id = :userId order by i.createdAt desc")
    List<InviteCode> findByCreator(@Param("userId") Long userId);

    @Query("select i from InviteCode i left join fetch i.createdBy order by i.createdAt desc")
    List<InviteCode> findAllWithUsers();

    @Query("select count(i) from InviteCode i where i.createdBy.id = :userId and i.useCount < i.maxUses "
            + "and i.revokedAt is null and i.expiresAt > :now")
    long countActiveByCreator(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update InviteCode i set i.revokedAt = :now where i.createdBy.id = :userId "
            + "and i.useCount < i.maxUses and i.revokedAt is null and i.expiresAt > :now")
    int revokeActiveByCreator(@Param("userId") Long userId, @Param("now") Instant now);
}
