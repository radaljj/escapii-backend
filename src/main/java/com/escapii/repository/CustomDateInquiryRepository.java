package com.escapii.repository;

import com.escapii.model.CustomDateInquiry;
import com.escapii.model.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustomDateInquiryRepository extends JpaRepository<CustomDateInquiry, Long> {

    /** Svi upiti sortirani po datumu kreiranja (najnoviji prvi). */
    List<CustomDateInquiry> findAllByOrderByCreatedAtDesc();

    /** Upiti po statusu, sortirani po datumu (najnoviji prvi). */
    List<CustomDateInquiry> findByStatusOrderByCreatedAtDesc(InquiryStatus status);

    /** Briše zatvorene upite čiji je closedAt stariji od zadatog trenutka. */
    @Modifying
    @Query("DELETE FROM CustomDateInquiry i WHERE i.status = com.escapii.model.InquiryStatus.CLOSED " +
           "AND i.closedAt IS NOT NULL AND i.closedAt < :cutoff")
    int deleteClosedBefore(@Param("cutoff") LocalDateTime cutoff);
}
