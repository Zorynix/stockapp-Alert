package ru.tuganov.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.tuganov.dto.TrackedInstrumentRequest;
import ru.tuganov.dto.TrackedInstrumentResponse;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.service.TrackedInstrumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracked-instruments")
@RequiredArgsConstructor
public class TrackedInstrumentController {

    private final TrackedInstrumentService trackedInstrumentService;

    @PostMapping
    public ResponseEntity<TrackedInstrumentResponse> create(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody TrackedInstrumentRequest request) {
        TrackedInstrumentResponse response =
                trackedInstrumentService.createForUser(userDetails.getAppUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TrackedInstrumentResponse>> getByUser(
            @AuthenticationPrincipal AppUserDetails userDetails) {
        return ResponseEntity.ok(trackedInstrumentService.getByAppUser(userDetails.getAppUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackedInstrumentResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails) {
        return ResponseEntity.ok(
                trackedInstrumentService.getById(id, userDetails.getUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrackedInstrumentResponse> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody TrackedInstrumentRequest request) {
        return ResponseEntity.ok(trackedInstrumentService.update(id, request, userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails) {
        trackedInstrumentService.delete(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
