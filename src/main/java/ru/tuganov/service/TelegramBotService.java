package ru.tuganov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.tuganov.dto.PriceAlertEvent;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class TelegramBotService {

    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final String botToken;
    private final RestTemplate restTemplate;
    private final boolean enabled;

    public TelegramBotService(
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.bot.enabled:false}") boolean enabled,
            @Value("${telegram.bot.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${telegram.bot.read-timeout-ms:5000}") int readTimeoutMs) {
        this.botToken = botToken;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.enabled = enabled && !botToken.isBlank();

        if (this.enabled) {
            log.info("Telegram Bot notifications ENABLED");
        } else {
            log.warn("Telegram Bot notifications DISABLED — set telegram.bot.token and telegram.bot.enabled=true");
        }
    }

    public boolean sendPriceAlert(PriceAlertEvent alert) {
        if (!enabled) {
            log.debug("Telegram notifications disabled, skipping alert for user {}", alert.appUserId());
            return false;
        }
        if (alert.chatId() == null) {
            log.debug("No chatId for user {}, skipping Telegram alert", alert.appUserId());
            return false;
        }

        String message = formatAlertMessage(alert);
        return sendMessage(alert.chatId(), message);
    }

    public boolean sendMessage(long chatId, String text) {
        if (!enabled) {
            return false;
        }

        try {
            String url = TELEGRAM_API_URL.formatted(botToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "HTML"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Telegram message sent to chat {}", chatId);
                return true;
            } else {
                log.error("Telegram API returned status {}: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    private String formatAlertMessage(PriceAlertEvent alert) {
        String emoji = alert.alertType() == PriceAlertEvent.AlertType.BUY ? "📉" : "📈";
        String action = alert.alertType() == PriceAlertEvent.AlertType.BUY ? "ПОКУПКА" : "ПРОДАЖА";
        String direction = alert.alertType() == PriceAlertEvent.AlertType.BUY
                ? "упала ниже порога" : "поднялась выше порога";

        return """
                %s <b>Ценовой алерт: %s</b>
                
                📊 <b>%s</b> (<code>%s</code>)
                
                💰 Текущая цена: <b>%s ₽</b>
                🎯 Порог: <b>%s ₽</b>
                
                Цена %s.
                
                ⏰ <i>StockApp Monitoring</i>
                """.formatted(
                emoji, action,
                alert.instrumentName(), alert.figi(),
                formatPrice(alert.currentPrice()),
                formatPrice(alert.threshold()),
                direction
        );
    }

    private String formatPrice(BigDecimal price) {
        return price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
