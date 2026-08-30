package com.escapii.repository;

import com.escapii.model.BookingFinancialItem;
import com.escapii.model.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingFinancialItemRepository extends JpaRepository<BookingFinancialItem, Long> {

    List<BookingFinancialItem> findByBookingId(Long bookingId);

    Optional<BookingFinancialItem> findByBookingIdAndItemType(Long bookingId, ItemType itemType);

    void deleteByBookingId(Long bookingId);
}
