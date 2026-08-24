package com.escapii.repository;

import com.escapii.model.Agency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyRepository extends JpaRepository<Agency, Long> {
    List<Agency> findAllByOrderByNameAsc();
    List<Agency> findByActiveTrueOrderByNameAsc();
}
