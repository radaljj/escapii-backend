package com.escapii.controller;

import com.escapii.service.NotificationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint za admin notifikacije.
 * Zaštićen AdminKeyFilter-om — key ide u X-Admin-Key header, nikad u URL.
 *
 * X-Accel-Buffering: no — govori Nginxu da ne baferuje ovaj odgovor,
 * što je neophodno da bi SSE streaming funkcionisao iza reverse proxija.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        return notificationService.subscribe();
    }
}
