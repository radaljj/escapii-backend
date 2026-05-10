package com.escapii.repository;

import com.escapii.model.CustomDateInquiry;
import com.escapii.model.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomDateInquiryRepository extends JpaRepository<CustomDateInquiry, Long> {

    /** Svi upiti sortirani po datumu kreiranja (najnoviji prvi). */
    List<CustomDateInquiry> findAllByOrderByCreatedAtDesc();

    /** Upiti po statusu, sortirani po datumu (najnoviji prvi). */
    List<CustomDateInquiry> findByStatusOrderByCreatedAtDesc(InquiryStatus status);
}
