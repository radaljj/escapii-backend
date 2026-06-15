package com.escapii.repository;

import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GiftVoucherRepository extends JpaRepository<GiftVoucher, Long> {

    Optional<GiftVoucher> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM GiftVoucher v WHERE v.code = :code")
    Optional<GiftVoucher> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);

    List<GiftVoucher> findAllByOrderByCreatedAtDesc();

    List<GiftVoucher> findByStatusOrderByCreatedAtDesc(VoucherStatus status);
}
