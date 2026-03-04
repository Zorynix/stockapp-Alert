package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.TrackedInstrumentRequest;
import ru.tuganov.dto.TrackedInstrumentResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.exception.ResourceNotFoundException;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.util.List;
import java.util.UUID;

/**
 * Сервис управления отслеживаемыми инструментами.
 * Позволяет создавать, обновлять, удалять и получать инструменты,
 * за ценами которых пользователь хочет следить.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TrackedInstrumentService {

    private final TrackedInstrumentRepository trackedInstrumentRepository;

    /**
     * Создаёт инструмент для аутентифицированного пользователя (JWT).
     */
    @Transactional
    public TrackedInstrumentResponse createForUser(AppUser appUser, TrackedInstrumentRequest request) {
        if (request.buyPrice().compareTo(request.sellPrice()) >= 0) {
            throw new IllegalArgumentException("Цена покупки должна быть меньше цены продажи");
        }

        var instrument = new TrackedInstrument();
        instrument.setFigi(request.figi());
        instrument.setInstrumentName(request.instrumentName());
        instrument.setBuyPrice(request.buyPrice());
        instrument.setSellPrice(request.sellPrice());
        instrument.setUser(appUser);

        TrackedInstrument saved = trackedInstrumentRepository.save(instrument);
        log.info("Created tracked instrument: {} ({}) for user {}",
                saved.getInstrumentName(), saved.getFigi(), appUser.getId());

        return toResponse(saved);
    }

    /** Получает инструменты по объекту AppUser (JWT-путь). */
    public List<TrackedInstrumentResponse> getByAppUser(AppUser appUser) {
        return trackedInstrumentRepository.findAllByUser(appUser)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Получает отслеживаемый инструмент по его ID с проверкой владельца. */
    public TrackedInstrumentResponse getById(UUID id, UUID requesterId) {
        TrackedInstrument instrument = findOrThrow(id);
        checkOwnership(instrument, requesterId);
        return toResponse(instrument);
    }

    /**
     * Обновляет ценовые границы отслеживаемого инструмента.
     * При обновлении сбрасываются флаги отправленных алертов, чтобы
     * новые границы начали отслеживаться заново.
     */
    @Transactional
    public TrackedInstrumentResponse update(UUID id, TrackedInstrumentRequest request, UUID requesterId) {
        if (request.buyPrice().compareTo(request.sellPrice()) >= 0) {
            throw new IllegalArgumentException("Цена покупки должна быть меньше цены продажи");
        }

        TrackedInstrument instrument = findOrThrow(id);
        checkOwnership(instrument, requesterId);
        instrument.setBuyPrice(request.buyPrice());
        instrument.setSellPrice(request.sellPrice());
        instrument.setBuyAlertSent(false);
        instrument.setSellAlertSent(false);

        TrackedInstrument saved = trackedInstrumentRepository.save(instrument);
        log.info("Updated tracked instrument: {} new bounds [{}, {}]",
                id, request.buyPrice(), request.sellPrice());

        return toResponse(saved);
    }

    /** Удаляет отслеживаемый инструмент. Бросает исключение если не найден. */
    @Transactional
    public void delete(UUID id, UUID requesterId) {
        TrackedInstrument instrument = findOrThrow(id);
        checkOwnership(instrument, requesterId);
        trackedInstrumentRepository.delete(instrument);
        log.info("Deleted tracked instrument: {}", id);
    }

    private void checkOwnership(TrackedInstrument instrument, UUID requesterId) {
        if (!instrument.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к данному инструменту");
        }
    }

    /** Ищет инструмент по ID или бросает ResourceNotFoundException. */
    private TrackedInstrument findOrThrow(UUID id) {
        return trackedInstrumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrackedInstrument", id));
    }

    /** Конвертирует JPA-сущность в DTO для ответа клиенту. */
    private TrackedInstrumentResponse toResponse(TrackedInstrument entity) {
        return new TrackedInstrumentResponse(
                entity.getId(),
                entity.getFigi(),
                entity.getInstrumentName(),
                entity.getBuyPrice(),
                entity.getSellPrice(),
                entity.isBuyAlertSent(),
                entity.isSellAlertSent(),
                entity.getCreatedAt(),
                entity.getUser().getId()
        );
    }
}
