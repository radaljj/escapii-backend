package com.escapii.repository;

import com.escapii.model.GiftTripInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GiftTripInquiryRepository extends JpaRepository<GiftTripInquiry, Long> {

    List<GiftTripInquiry> findAllByOrderByCreatedAtDesc();
}
