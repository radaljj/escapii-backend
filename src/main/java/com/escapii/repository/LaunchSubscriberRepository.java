package com.escapii.repository;

import com.escapii.model.LaunchSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LaunchSubscriberRepository extends JpaRepository<LaunchSubscriber, Long> {

    boolean existsByEmail(String email);

    /** Nove prijave od prethodnog slanja digest-a - za dnevni izveštaj timu. */
    List<LaunchSubscriber> findByCreatedAtBetween(@Param("from") LocalDateTime from,
                                                   @Param("until") LocalDateTime until);
}
