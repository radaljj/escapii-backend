package com.escapii.repository;

import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GiftVoucherRepository extends JpaRepository<GiftVoucher, Long> {

    Optional<GiftVoucher> findByCode(String code);

    boolean existsByCode(String code);

    List<GiftVoucher> findAllByOrderByCreatedAtDesc();

    List<GiftVoucher> findByStatusOrderByCreatedAtDesc(VoucherStatus status);
}
