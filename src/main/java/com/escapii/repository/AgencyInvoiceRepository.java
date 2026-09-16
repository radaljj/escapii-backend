package com.escapii.repository;

import com.escapii.model.AgencyInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgencyInvoiceRepository extends JpaRepository<AgencyInvoice, Long> {

    List<AgencyInvoice> findByAgencyIdOrderByIssuedAtDescIdDesc(Long agencyId);

    List<AgencyInvoice> findAllByOrderByIssuedAtDescIdDesc();

    /** Zaključava red fakture - dva istovremena klika (plaćeno/storno) se serijalizuju. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AgencyInvoice i WHERE i.id = :id")
    Optional<AgencyInvoice> findByIdForUpdate(@Param("id") Long id);
}
