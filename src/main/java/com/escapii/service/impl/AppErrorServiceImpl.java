package com.escapii.service.impl;

import com.escapii.model.AppError;
import com.escapii.repository.AppErrorRepository;
import com.escapii.service.AppErrorService;
import com.escapii.service.email.core.EmailSender;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppErrorServiceImpl implements AppErrorService {

    private final AppErrorRepository repo;
    private final EmailSender emailSender;

    @Value("${app.ops-email}")
    private String opsEmail;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @Async
    @Transactional
    @Override
    public void record(HttpServletRequest request, int statusCode, Exception ex) {
        try {
            String endpoint   = request.getMethod() + " " + request.getRequestURI();
            String exType     = ex.getClass().getSimpleName();
            String exMessage  = ex.getMessage() != null
                    ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 500))
                    : "(no message)";

            Optional<AppError> existing =
                    repo.findByEndpointAndExceptionTypeAndResolvedFalse(endpoint, exType);

            if (existing.isPresent()) {
                // Ista greška — samo povećaj brojač
                AppError err = existing.get();
                err.setCount(err.getCount() + 1);
                err.setLastSeenAt(LocalDateTime.now());
                repo.save(err);
                log.debug("[AppError] Ponavljanje #{} — {} {}", err.getCount(), exType, endpoint);
            } else {
                // Nova greška — sačuvaj i pošalji email
                AppError err = new AppError();
                err.setEndpoint(endpoint);
                err.setExceptionType(exType);
                err.setMessage(exMessage);
                err.setStackTrace(extractStackTrace(ex));
                err.setStatusCode(statusCode);
                err.setCount(1);
                err.setFirstSeenAt(LocalDateTime.now());
                err.setLastSeenAt(LocalDateTime.now());
                err.setResolved(false);
                repo.save(err);

                sendAlertEmail(err);
                log.info("[AppError] Nova greška zabeležena i email poslat: {} {}", exType, endpoint);
            }
        } catch (Exception recordEx) {
            // Nikad ne sme da padne sam error handler
            log.error("[AppError] Greška pri belezenju greške: {}", recordEx.getMessage());
        }
    }

    @Override
    public List<AppError> getAll() {
        return repo.findAllByOrderByLastSeenAtDesc();
    }

    @Override
    public long countUnresolved() {
        return repo.countByResolvedFalse();
    }

    @Transactional
    @Override
    public void resolve(Long id) {
        repo.findById(id).ifPresent(e -> {
            e.setResolved(true);
            repo.save(e);
        });
    }

    @Transactional
    @Override
    public void deleteResolved() {
        repo.deleteAllResolved();
    }

    @Transactional
    @Override
    public void deleteAll() {
        repo.deleteAll();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String extractStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) + "\n... (skraćeno)" : trace;
    }

    private void sendAlertEmail(AppError err) {
        String subject = "🚨 Escapii greška: " + err.getExceptionType();
        String html = """
            <div style="font-family:monospace;background:#0d1117;color:#e6edf3;padding:24px;border-radius:8px;max-width:700px;">
              <h2 style="color:#ff7b72;margin:0 0 20px;font-family:sans-serif;">🚨 Nova greška u Escapii aplikaciji</h2>
              <table style="width:100%;border-collapse:collapse;margin-bottom:20px;">
                <tr>
                  <td style="color:#8b949e;padding:6px 0;width:130px;font-family:sans-serif;font-size:13px;">Endpoint</td>
                  <td style="color:#e6edf3;font-weight:bold;font-size:13px;">%s</td>
                </tr>
                <tr>
                  <td style="color:#8b949e;padding:6px 0;font-family:sans-serif;font-size:13px;">Tip greške</td>
                  <td style="color:#ff7b72;font-size:13px;">%s</td>
                </tr>
                <tr>
                  <td style="color:#8b949e;padding:6px 0;font-family:sans-serif;font-size:13px;">Poruka</td>
                  <td style="color:#ffa657;font-size:13px;">%s</td>
                </tr>
                <tr>
                  <td style="color:#8b949e;padding:6px 0;font-family:sans-serif;font-size:13px;">HTTP status</td>
                  <td style="color:#e6edf3;font-size:13px;">%d</td>
                </tr>
                <tr>
                  <td style="color:#8b949e;padding:6px 0;font-family:sans-serif;font-size:13px;">Vreme</td>
                  <td style="color:#e6edf3;font-size:13px;">%s</td>
                </tr>
              </table>
              <div style="background:#161b22;border:1px solid #30363d;border-radius:6px;padding:16px;overflow-x:auto;">
                <pre style="margin:0;font-size:11px;color:#a5d6ff;white-space:pre-wrap;word-break:break-word;">%s</pre>
              </div>
              <p style="margin-top:16px;color:#8b949e;font-size:12px;font-family:sans-serif;">
                Pogledaj sve greške u <strong>Admin panelu → tab Greške</strong>. Ista greška se grupiše — nećeš dobiti duplikate.
              </p>
            </div>
            """.formatted(
                err.getEndpoint(),
                err.getExceptionType(),
                err.getMessage(),
                err.getStatusCode(),
                err.getFirstSeenAt().format(FMT),
                escapeHtml(err.getStackTrace())
        );
        emailSender.send(opsEmail, subject, html);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
