package ru.tuganov.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.tuganov.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/stock")
    public ResponseEntity<byte[]> generateStockReport(
            @RequestParam @NotBlank String figi,
            @RequestParam @NotBlank String name,
            @RequestParam(defaultValue = "1m")
            @Pattern(regexp = "1m|3m|6m|1y", message = "Допустимые периоды: 1m, 3m, 6m, 1y")
            String period,
            @RequestParam(defaultValue = "pdf")
            @Pattern(regexp = "pdf|md", message = "Допустимые форматы: pdf, md")
            String format) {

        byte[] content = reportService.generateStockReport(figi, name, period, format);

        boolean isPdf = "pdf".equals(format);
        String extension = isPdf ? "pdf" : "md";
        String filename = "report_%s_%s.%s".formatted(
                figi.replaceAll("[^a-zA-Z0-9]", "_"), period, extension);

        MediaType contentType = isPdf ? MediaType.APPLICATION_PDF : MediaType.TEXT_PLAIN;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);
        headers.setCacheControl(CacheControl.noStore());

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }
}
