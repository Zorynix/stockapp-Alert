package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Сервис отправки email уведомлений.
 * Отправляет OTP-код для подтверждения адреса электронной почты.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@stockapp.ru}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Отправляет письмо с OTP-кодом подтверждения email.
     *
     * @param toEmail адрес получателя
     * @param code    6-значный OTP-код
     */
    public void sendVerificationCode(String toEmail, String code) {
        String subject = "StockApp — подтверждение email";
        String text = """
                Добро пожаловать в StockApp!

                Ваш код подтверждения: %s

                Код действителен 15 минут.

                Если вы не регистрировались в StockApp — просто проигнорируйте это письмо.
                """.formatted(code);

        sendEmail(toEmail, subject, text);
    }

    /**
     * Отправляет письмо с новым OTP-кодом (по запросу пользователя).
     *
     * @param toEmail адрес получателя
     * @param code    6-значный OTP-код
     */
    public void sendNewVerificationCode(String toEmail, String code) {
        String subject = "StockApp — новый код подтверждения";
        String text = """
                Вы запросили новый код подтверждения email.

                Ваш новый код: %s

                Код действителен 15 минут.

                Если вы не запрашивали код — просто проигнорируйте это письмо.
                """.formatted(code);

        sendEmail(toEmail, subject, text);
    }

    /**
     * Отправляет email-уведомление о срабатывании ценового алерта.
     *
     * @param toEmail       адрес получателя
     * @param instrumentName название инструмента
     * @param alertType     тип алерта ("BUY" или "SELL")
     * @param currentPrice  текущая цена
     * @param threshold     пороговая цена
     */
    public void sendPriceAlert(String toEmail, String instrumentName, String figi,
                               String alertType, String currentPrice, String threshold) {
        boolean isBuy = "BUY".equals(alertType);
        String emoji = isBuy ? "📉" : "📈";
        String action = isBuy ? "ПОКУПКА" : "ПРОДАЖА";
        String direction = isBuy ? "упала ниже порога" : "поднялась выше порога";

        String subject = "%s Ценовой алерт: %s — %s".formatted(emoji, action, instrumentName);
        String text = """
                %s Ценовой алерт: %s

                %s (%s)

                Текущая цена: %s ₽
                Порог: %s ₽

                Цена %s.

                StockApp Monitoring
                """.formatted(emoji, action, instrumentName, figi,
                currentPrice, threshold, direction);

        sendEmail(toEmail, subject, text);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Не удалось отправить email. Попробуйте позже.");
        }
    }
}
