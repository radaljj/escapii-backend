package com.escapii.config;

import com.escapii.service.AppErrorService;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Globalni exception handler.
 * Pretvara sve greske u konzistentan JSON format.
 * Neočekivane 5xx greške se čuvaju u bazi i šalju email alert (samo prva pojava).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    @Lazy
    private AppErrorService appErrorService;

    /** 404 - nepostojeći endpoint (uglavnom bot skenovi). Nema email alerta. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("[API] 404 - {}", request.getMethod() + " " + request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Endpoint ne postoji"));
    }

    /** Pogrešan HTTP metod (npr. GET na /api/booking). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("[API] Pogrešan metod: {} - dozvoljeno: {}", ex.getMethod(), ex.getSupportedMethods());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "HTTP metod nije podržan: " + ex.getMethod()));
    }

    /**
     * Neispravni query parametri - nedostaje obavezan, ili je pogrešnog tipa
     * (npr. /vouchers/reveal bez ?code=, ili ?id=abc gde se očekuje broj).
     *
     * Ovo je greška pozivaoca, ne servera. Bez ove obrade padale su na
     * Exception fallback koji vraća 500 i šalje email alert - pa je svaki bot
     * koji naleti na endpoint bez parametra pravio lažnu uzbunu.
     */
    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequestParam(Exception ex, HttpServletRequest request) {
        String param = (ex instanceof MissingServletRequestParameterException m)
                ? m.getParameterName()
                : ((MethodArgumentTypeMismatchException) ex).getName();
        log.warn("[API] Neispravan parametar '{}' na {}", param, request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Neispravan ili nedostajući parametar: " + param));
    }

    /** Validacione greške - @Valid na BookingRequest. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        String summary = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        log.warn("[Validacija] {} grešaka: {}", fieldErrors.size(), summary);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Validaciona greška", "fields", fieldErrors));
    }

    /**
     * Race condition - dva korisnika istovremeno menjaju isti termin.
     * Hibernate baca ovo kada @Version kolona ne odgovara.
     */
    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        OptimisticLockException.class,
        StaleObjectStateException.class
    })
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception ex) {
        log.warn("[Concurrency] Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Termin je upravo izmenjen od strane drugog korisnika. Molimo osvežite stranicu i pokušajte ponovo."));
    }

    /** Poslovne greške - termin ne postoji, destinacija ne postoji, itd. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(ResponseStatusException ex) {
        log.warn("[Poslovna greška] {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Greška"));
    }

    /**
     * Constraint violation na @RequestParam/@PathVariable (npr. @Min/@Max u BookingController).
     * Vraća 400 umesto da pada u 500 catch-all handler.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("[Validacija] ConstraintViolation: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Neispravan parametar: " + message));
    }

    /**
     * Fajl prekoračuje spring.servlet.multipart.max-file-size/max-request-size.
     * Napomena: ako Tomcat connector-level limit (server.tomcat.max-http-form-post-size)
     * odbije zahtev PRE ovoga, ovaj handler se nikad neće izvršiti - taj limit mora
     * biti usklađen sa ovim iznad, inače odgovor stiže bez CORS headera.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("[Upload] Fajl prevelik: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "Fajl je prevelik."));
    }

    /** Nepredviđene greške - 500. Beleži u bazu i šalje email (samo nova pojava). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Endpoint izvlačimo SINHRONO - request se reciklira pre nego što async thread počne
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        log.error("[GREŠKA] Neočekivana greška na {}: {}", endpoint, ex.getMessage(), ex);
        if (appErrorService != null) {
            appErrorService.record(endpoint, 500, ex);
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Došlo je do greške na serveru. Pokušajte ponovo."));
    }
}
