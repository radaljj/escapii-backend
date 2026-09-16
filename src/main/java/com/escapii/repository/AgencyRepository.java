package com.escapii.repository;

import com.escapii.model.Agency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgencyRepository extends JpaRepository<Agency, Long> {
    List<Agency> findAllByOrderByNameAsc();
    List<Agency> findByActiveTrueOrderByNameAsc();

    /**
     * Zaključava agenciju dok se pravi zbirna faktura: dva istovremena klika na „Fakturiši"
     * se serijalizuju, pa drugi zatekne rezervacije već fakturisane i ne pravi duplu fakturu.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Agency a WHERE a.id = :id")
    Optional<Agency> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
