package ru.tuganov.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Пользователь, которому принадлежит уведомление.
     * Может быть null для старых записей до введения AppUser (ON DELETE SET NULL).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    private AppUser appUser;

    /**
     * Telegram user ID (legacy поле, nullable).
     * Сохраняется для backward-compat с записями до миграции на AppUser.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Telegram chat ID (legacy поле, nullable).
     * Сохраняется для backward-compat с записями до миграции на AppUser.
     */
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "instrument_name", nullable = false)
    private String instrumentName;

    @Column(nullable = false)
    private String figi;

    @Column(name = "alert_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false, length = 1024)
    private String message;

    @Column(name = "sent_to_telegram", nullable = false)
    private boolean sentToTelegram;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum AlertType {
        BUY, SELL
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
