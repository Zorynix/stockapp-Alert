package ru.tuganov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.tuganov.dto.NotificationResponse;
import ru.tuganov.entity.Notification;
import ru.tuganov.repository.NotificationRepository;
import ru.tuganov.security.AppUserDetails;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal AppUserDetails userDetails) {
        List<NotificationResponse> notifications = notificationRepository
                .findTop50ByAppUserOrderByCreatedAtDesc(userDetails.getAppUser())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(notifications);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getInstrumentName(),
                n.getFigi(),
                n.getAlertType().name(),
                n.getCurrentPrice(),
                n.getThreshold(),
                n.getMessage(),
                n.isSentToTelegram(),
                n.getCreatedAt()
        );
    }
}
