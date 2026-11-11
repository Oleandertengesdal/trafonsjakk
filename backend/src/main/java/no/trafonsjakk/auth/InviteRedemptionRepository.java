package no.trafonsjakk.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteRedemptionRepository extends JpaRepository<InviteRedemption, Long> {

    @Query("select r from InviteRedemption r join fetch r.user where r.invite.id in :ids order by r.usedAt, r.id")
    List<InviteRedemption> findByInviteIds(@Param("ids") Collection<Long> ids);
}
