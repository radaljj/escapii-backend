package com.escapii.repository;

import com.escapii.model.AppError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppErrorRepository extends JpaRepository<AppError, Long> {

    /**
     * Aktivne (nerešene) greške sa istim endpointom i tipom - za grupisanje.
     *
     * Namerno vraća listu, ne Optional. Tabela nema unique constraint, a upis ide iz
     * @Async niti: dva istovremena ista pada mogla su da naprave dva reda. Optional bi
     * od tog trenutka bacao IncorrectResultSizeDataAccessException na SVAKI sledeći
     * poziv, a taj izuzetak guta catch u record() - beleženje grešaka bi se tiho
     * ugasilo baš za endpoint koji najviše puca. Lista taj scenario preživi.
     */
    List<AppError> findByEndpointAndExceptionTypeAndResolvedFalseOrderByIdAsc(String endpoint, String exceptionType);

    /**
     * Ciljani inkrement umesto čitaj-izmeni-upiši. Dva istovremena ponavljanja iste
     * greške bi kroz merge() prebrisala jedno drugo i count bi zaostajao za stvarnošću.
     * Poruka se osvežava jer nosi konkretan podatak (npr. broj rezervacije) i korisnije
     * je videti poslednju pojavu nego prvu.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE AppError e
              SET e.count = e.count + 1,
                  e.lastSeenAt = :kad,
                  e.message = :poruka
            WHERE e.id = :id
           """)
    int zabeleziPonavljanje(@Param("id") Long id,
                            @Param("kad") LocalDateTime kad,
                            @Param("poruka") String poruka);

    /** Sve greške, najnovije prve. */
    List<AppError> findAllByOrderByLastSeenAtDesc();

    /** Broj nerešenih grešaka - za badge u admin panelu. */
    long countByResolvedFalse();

    @Modifying
    @Query("DELETE FROM AppError e WHERE e.resolved = true")
    void deleteAllResolved();
}
