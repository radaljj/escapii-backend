package com.escapii.repository;

import com.escapii.model.AppError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppErrorRepository extends JpaRepository<AppError, Long> {

    /** Pronalazi aktivnu (nerešenu) grešku sa istim endpointom i tipom - za grupovanje. */
    Optional<AppError> findByEndpointAndExceptionTypeAndResolvedFalse(String endpoint, String exceptionType);

    /** Sve greške, najnovije prve. */
    List<AppError> findAllByOrderByLastSeenAtDesc();

    /** Broj nerešenih grešaka - za badge u admin panelu. */
    long countByResolvedFalse();

    @Modifying
    @Query("DELETE FROM AppError e WHERE e.resolved = true")
    void deleteAllResolved();
}
