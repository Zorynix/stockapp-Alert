package ru.tuganov.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tuganov.util.PriceUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final Color PAGE_BG = new Color(30, 30, 34);
    private static final Color CARD_BG = new Color(44, 44, 48);
    private static final Color HEADER_BG = new Color(55, 55, 60);
    private static final Color ROW_EVEN = new Color(38, 38, 42);
    private static final Color ROW_ODD = new Color(48, 48, 52);
    private static final Color ACCENT = new Color(168, 85, 247);
    private static final Color WHITE = Color.WHITE;
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color FOOTER_GRAY = new Color(100, 100, 100);
    private static final Color GREEN = new Color(52, 199, 89);
    private static final Color RED = new Color(255, 69, 58);
    private static final Color SEPARATOR = new Color(60, 60, 65);

    private static final int MAX_TABLE_ROWS = 200;
    private static final String FONT_REGULAR = "fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD = "fonts/DejaVuSans-Bold.ttf";

    private static final ThreadLocal<DecimalFormat> PRICE_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        return new DecimalFormat("#,##0.00", symbols);
    });

    private static final ThreadLocal<DecimalFormat> VOLUME_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols);
    });

    private final PriceHistoryService priceHistoryService;

    // ── Public API ────────────────────────────────────────────

    public byte[] generateStockReport(String figi, String instrumentName, String period, String format) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = resolveFromDate(to, period);
            List<HistoricCandle> candles = priceHistoryService.getCandlesForPeriod(figi, from, to);

            if ("md".equals(format)) {
                String md = buildMarkdownReport(figi, instrumentName, period, from, to, candles);
                log.info("MD report generated: {} ({}) period={}", instrumentName, figi, period);
                return md.getBytes(StandardCharsets.UTF_8);
            }
            return buildPdfReport(figi, instrumentName, period, from, to, candles);
        } finally {
            PRICE_FORMAT.remove();
            VOLUME_FORMAT.remove();
        }
    }

    // ── PDF Generation ────────────────────────────────────────

    private byte[] buildPdfReport(String figi, String instrumentName, String period,
                                  LocalDate from, LocalDate to, List<HistoricCandle> candles) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 36);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new DarkPageBackground());
            document.open();

            BaseFont regular = loadFont(FONT_REGULAR);
            BaseFont bold = loadFont(FONT_BOLD);

            addHeader(document, bold, regular, instrumentName, figi, from, to, period);

            if (!candles.isEmpty()) {
                // Page 1: header + analytics (portrait)
                addPriceSummary(document, bold, regular, candles);
                addSeparator(document);
                addTradingStats(document, bold, regular, candles);

                // Page 2: chart only, landscape
                document.setPageSize(PageSize.A4.rotate());
                document.newPage();
                // Set portrait immediately so any auto-created overflow page is portrait
                document.setPageSize(PageSize.A4);
                addPriceChart(document, candles);

                // No explicit newPage() - candle table title won't fit in remaining ~2pt and
                // naturally triggers a new portrait page
                addCandleTable(document, bold, regular, candles);
            } else {
                Font noData = createFont(regular, 13, WHITE);
                Paragraph p = new Paragraph("Нет данных за выбранный период.", noData);
                p.setSpacingBefore(20);
                document.add(p);
            }

            addFooter(document, regular);

            document.close();
            writer.close();

            log.info("PDF report generated: {} ({}) period={}", instrumentName, figi, period);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate PDF report: {} ({})", instrumentName, figi, e);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    // ── Markdown Generation ───────────────────────────────────

    private String buildMarkdownReport(String figi, String instrumentName, String period,
                                       LocalDate from, LocalDate to, List<HistoricCandle> candles) {
        StringBuilder sb = new StringBuilder();

        sb.append("# StockApp · Аналитический отчёт\n\n");
        sb.append("## ").append(instrumentName).append("\n\n");
        sb.append("**FIGI:** ").append(figi).append("  \n");
        sb.append("**Период:** ").append(from.format(DATE_FMT)).append(" — ")
                .append(to.format(DATE_FMT)).append(" (").append(periodLabel(period)).append(")  \n");
        sb.append("**Дата отчёта:** ").append(LocalDate.now().format(DATE_FMT)).append("\n\n");
        sb.append("---\n\n");

        if (candles.isEmpty()) {
            sb.append("*Нет данных за выбранный период.*\n");
        } else {
            appendPriceSummaryMd(sb, candles);
            appendTradingStatsMd(sb, candles);
            appendCandleTableMd(sb, candles);
        }

        sb.append("---\n\n");
        sb.append("*Отчёт сгенерирован автоматически сервисом StockApp. ")
                .append("Данные предоставлены T-Invest API. ")
                .append("Не является индивидуальной инвестиционной рекомендацией.*\n");

        return sb.toString();
    }

    private void appendPriceSummaryMd(StringBuilder sb, List<HistoricCandle> candles) {
        BigDecimal openPrice = PriceUtils.toBigDecimal(candles.getFirst().getOpen());
        BigDecimal closePrice = PriceUtils.toBigDecimal(candles.getLast().getClose());
        BigDecimal highPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getHigh()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getLow()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal change = closePrice.subtract(openPrice);
        BigDecimal changePercent = openPrice.compareTo(BigDecimal.ZERO) > 0
                ? change.divide(openPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        String sign = change.signum() >= 0 ? "+" : "";

        sb.append("## Ценовая динамика\n\n");
        sb.append("| Показатель | Значение |\n|---|---|\n");
        sb.append("| Цена открытия | ").append(formatPrice(openPrice)).append(" ₽ |\n");
        sb.append("| Цена закрытия | ").append(formatPrice(closePrice)).append(" ₽ |\n");
        sb.append("| Максимум за период | ").append(formatPrice(highPrice)).append(" ₽ |\n");
        sb.append("| Минимум за период | ").append(formatPrice(lowPrice)).append(" ₽ |\n");
        sb.append("| Изменение | ").append(sign).append(formatPrice(change)).append(" ₽ |\n");
        sb.append("| Изменение, % | ").append(sign)
                .append(changePercent.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("% |\n\n---\n\n");
    }

    private void appendTradingStatsMd(StringBuilder sb, List<HistoricCandle> candles) {
        BigDecimal avgClose = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getClose()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 2, RoundingMode.HALF_UP);
        long totalVolume = candles.stream().mapToLong(HistoricCandle::getVolume).sum();
        long avgVolume = totalVolume / candles.size();
        long maxVolume = candles.stream().mapToLong(HistoricCandle::getVolume).max().orElse(0);
        BigDecimal highPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getHigh()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getLow()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal volatility = avgClose.compareTo(BigDecimal.ZERO) > 0
                ? highPrice.subtract(lowPrice)
                .divide(avgClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        sb.append("## Статистика торгов\n\n");
        sb.append("| Показатель | Значение |\n|---|---|\n");
        sb.append("| Средняя цена закрытия | ").append(formatPrice(avgClose)).append(" ₽ |\n");
        sb.append("| Количество свечей | ").append(candles.size()).append(" |\n");
        sb.append("| Общий объём торгов | ").append(formatVolume(totalVolume)).append(" |\n");
        sb.append("| Средний объём | ").append(formatVolume(avgVolume)).append(" |\n");
        sb.append("| Максимальный объём | ").append(formatVolume(maxVolume)).append(" |\n");
        sb.append("| Волатильность (High–Low)/Avg | ")
                .append(volatility.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("% |\n\n---\n\n");
    }

    private void appendCandleTableMd(StringBuilder sb, List<HistoricCandle> candles) {
        sb.append("## Исторические данные\n\n");
        sb.append("| Дата | Открытие | Максимум | Минимум | Закрытие | Объём | Изм. % |\n");
        sb.append("|---|---|---|---|---|---|---|\n");

        int maxRows = Math.min(candles.size(), MAX_TABLE_ROWS);
        for (int i = 0; i < maxRows; i++) {
            HistoricCandle candle = candles.get(i);
            BigDecimal open = PriceUtils.toBigDecimal(candle.getOpen());
            BigDecimal close = PriceUtils.toBigDecimal(candle.getClose());
            BigDecimal candleChange = open.compareTo(BigDecimal.ZERO) > 0
                    ? close.subtract(open).divide(open, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            String sign = candleChange.signum() >= 0 ? "+" : "";

            sb.append("| ").append(formatCandleDate(candle))
                    .append(" | ").append(formatPrice(open))
                    .append(" | ").append(formatPrice(PriceUtils.toBigDecimal(candle.getHigh())))
                    .append(" | ").append(formatPrice(PriceUtils.toBigDecimal(candle.getLow())))
                    .append(" | ").append(formatPrice(close))
                    .append(" | ").append(formatVolume(candle.getVolume()))
                    .append(" | ").append(sign)
                    .append(candleChange.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append("% |\n");
        }

        if (candles.size() > MAX_TABLE_ROWS) {
            sb.append("\n*…ещё ").append(candles.size() - MAX_TABLE_ROWS)
                    .append(" записей (показаны первые ").append(MAX_TABLE_ROWS).append(")*\n");
        }
        sb.append("\n");
    }

    // ── PDF Report Sections ───────────────────────────────────

    private void addHeader(Document doc, BaseFont bold, BaseFont regular,
                           String name, String figi, LocalDate from, LocalDate to,
                           String period) throws DocumentException {
        Font titleFont = createFont(bold, 20, ACCENT);
        Paragraph title = new Paragraph("StockApp \u00b7 Аналитический отчёт", titleFont);
        title.setSpacingAfter(6);
        doc.add(title);

        Font nameFont = createFont(bold, 16, WHITE);
        doc.add(new Paragraph(name, nameFont));

        Font metaFont = createFont(regular, 9, GRAY);
        doc.add(new Paragraph("FIGI: " + figi, metaFont));
        doc.add(new Paragraph(
                "Период: " + from.format(DATE_FMT) + " \u2014 " + to.format(DATE_FMT)
                        + " (" + periodLabel(period) + ")", metaFont));
        doc.add(new Paragraph("Дата отчёта: " + LocalDate.now().format(DATE_FMT), metaFont));

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(4);
        doc.add(spacer);
    }

    private void addPriceChart(Document doc, List<HistoricCandle> candles) throws DocumentException {
        try {
            Rectangle landscape = PageSize.A4.rotate();
            float availableWidth  = landscape.getWidth()  - doc.leftMargin() - doc.rightMargin();
            float availableHeight = landscape.getHeight() - doc.topMargin()  - doc.bottomMargin() - 2f;

            Image chartImage = buildCandlestickChart(candles, availableWidth, availableHeight);
            doc.add(chartImage);
        } catch (Exception e) {
            log.warn("Chart generation skipped: {}", e.getMessage());
            doc.add(new Paragraph("График недоступен: " + e.getMessage()));
        }
    }

    private Image buildCandlestickChart(List<HistoricCandle> candles,
                                        float availableWidthPt, float availableHeightPt)
            throws Exception {
        OHLCDataItem[] items = candles.stream()
                .map(c -> new OHLCDataItem(
                        java.util.Date.from(extractCandleDate(c).atStartOfDay(MOSCOW_ZONE).toInstant()),
                        PriceUtils.toBigDecimal(c.getOpen()).doubleValue(),
                        PriceUtils.toBigDecimal(c.getHigh()).doubleValue(),
                        PriceUtils.toBigDecimal(c.getLow()).doubleValue(),
                        PriceUtils.toBigDecimal(c.getClose()).doubleValue(),
                        (double) c.getVolume()))
                .toArray(OHLCDataItem[]::new);

        DefaultOHLCDataset dataset = new DefaultOHLCDataset("", items);
        JFreeChart chart = ChartFactory.createCandlestickChart(null, null, null, dataset, false);

        float dpi = 150f;
        float scale = dpi / 72f;
        styleChart(chart, scale);

        int pxWidth  = Math.round(availableWidthPt  * scale);
        int pxHeight = Math.round(availableHeightPt * scale);

        BufferedImage img = new BufferedImage(pxWidth, pxHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        chart.draw(g2, new Rectangle2D.Double(0, 0, pxWidth, pxHeight));
        g2.dispose();

        ByteArrayOutputStream chartBaos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", chartBaos);

        Image image = Image.getInstance(chartBaos.toByteArray());
        image.setDpi(Math.round(dpi), Math.round(dpi));
        image.scaleToFit(availableWidthPt, availableHeightPt);
        return image;
    }

    private void styleChart(JFreeChart chart, float scale) {
        java.awt.Color bg = new java.awt.Color(38, 38, 42);
        java.awt.Color gridColor = new java.awt.Color(60, 60, 65);
        java.awt.Color axisColor = new java.awt.Color(155, 155, 155);
        // Шрифт масштабируем: 9pt в PDF = 9 * scale px при рендере
        int axisFontSize = Math.round(9 * scale);
        java.awt.Font axisFont = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, axisFontSize);

        chart.setBackgroundPaint(CARD_BG);
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(bg);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(gridColor);
        plot.setOutlineVisible(false);

        CandlestickRenderer renderer = (CandlestickRenderer) plot.getRenderer();
        renderer.setUpPaint(GREEN);
        renderer.setDownPaint(RED);
        renderer.setUseOutlinePaint(false);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_AVERAGE);
        renderer.setAutoWidthGap(0.5);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setTickLabelPaint(axisColor);
        domainAxis.setAxisLinePaint(gridColor);
        domainAxis.setTickMarkPaint(gridColor);
        domainAxis.setTickLabelFont(axisFont);
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd.MM"));
        domainAxis.setLabel(null);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelPaint(axisColor);
        rangeAxis.setAxisLinePaint(gridColor);
        rangeAxis.setTickMarkPaint(gridColor);
        rangeAxis.setTickLabelFont(axisFont);
        rangeAxis.setLabel(null);
        rangeAxis.setAutoRangeIncludesZero(false);
    }

    private void addPriceSummary(Document doc, BaseFont bold, BaseFont regular,
                                 List<HistoricCandle> candles) throws DocumentException {
        Font sectionFont = createFont(bold, 13, ACCENT);
        Paragraph sectionTitle = new Paragraph("Ценовая динамика", sectionFont);
        sectionTitle.setSpacingAfter(8);
        doc.add(sectionTitle);

        BigDecimal openPrice = PriceUtils.toBigDecimal(candles.getFirst().getOpen());
        BigDecimal closePrice = PriceUtils.toBigDecimal(candles.getLast().getClose());

        BigDecimal highPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getHigh()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getLow()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal change = closePrice.subtract(openPrice);
        BigDecimal changePercent = openPrice.compareTo(BigDecimal.ZERO) > 0
                ? change.divide(openPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1f, 1f});

        addStatCard(grid, bold, regular, "Цена открытия",
                formatPrice(openPrice) + " \u20bd", WHITE);
        addStatCard(grid, bold, regular, "Цена закрытия",
                formatPrice(closePrice) + " \u20bd", WHITE);
        addStatCard(grid, bold, regular, "Максимум за период",
                formatPrice(highPrice) + " \u20bd", WHITE);
        addStatCard(grid, bold, regular, "Минимум за период",
                formatPrice(lowPrice) + " \u20bd", WHITE);

        String sign = change.signum() >= 0 ? "+" : "";
        Color changeColor = change.signum() >= 0 ? GREEN : RED;
        addStatCard(grid, bold, regular, "Изменение",
                sign + formatPrice(change) + " \u20bd", changeColor);
        addStatCard(grid, bold, regular, "Изменение, %",
                sign + changePercent.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%",
                changeColor);

        doc.add(grid);
    }

    private void addTradingStats(Document doc, BaseFont bold, BaseFont regular,
                                 List<HistoricCandle> candles) throws DocumentException {
        Font sectionFont = createFont(bold, 13, ACCENT);
        Paragraph sectionTitle = new Paragraph("Статистика торгов", sectionFont);
        sectionTitle.setSpacingAfter(8);
        doc.add(sectionTitle);

        BigDecimal avgClosePrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getClose()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 2, RoundingMode.HALF_UP);

        long totalVolume = candles.stream().mapToLong(HistoricCandle::getVolume).sum();
        long avgVolume = candles.isEmpty() ? 0 : totalVolume / candles.size();
        long maxVolume = candles.stream().mapToLong(HistoricCandle::getVolume).max().orElse(0);

        BigDecimal highPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getHigh()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowPrice = candles.stream()
                .map(c -> PriceUtils.toBigDecimal(c.getLow()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal volatility = avgClosePrice.compareTo(BigDecimal.ZERO) > 0
                ? highPrice.subtract(lowPrice)
                .divide(avgClosePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.3f, 1f});

        addTableHeaderCell(table, bold, "Показатель");
        addTableHeaderCell(table, bold, "Значение");

        int row = 0;
        addKeyValueRow(table, regular, "Средняя цена закрытия",
                formatPrice(avgClosePrice) + " \u20bd", row++);
        addKeyValueRow(table, regular, "Количество свечей",
                String.valueOf(candles.size()), row++);
        addKeyValueRow(table, regular, "Общий объём торгов",
                formatVolume(totalVolume), row++);
        addKeyValueRow(table, regular, "Средний объём",
                formatVolume(avgVolume), row++);
        addKeyValueRow(table, regular, "Максимальный объём",
                formatVolume(maxVolume), row++);
        addKeyValueRow(table, regular, "Волатильность (High\u2013Low)/Avg",
                volatility.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%", row);

        doc.add(table);
    }

    private void addCandleTable(Document doc, BaseFont bold, BaseFont regular,
                                List<HistoricCandle> candles) throws DocumentException {
        Font sectionFont = createFont(bold, 13, ACCENT);
        Paragraph sectionTitle = new Paragraph("Исторические данные", sectionFont);
        sectionTitle.setSpacingAfter(8);
        doc.add(sectionTitle);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.6f, 1.1f, 1.1f, 1.1f, 1.1f, 1.2f, 0.9f});

        String[] headers = {"Дата", "Открытие", "Максимум", "Минимум", "Закрытие", "Объём", "Изм. %"};
        for (String header : headers) {
            addTableHeaderCell(table, bold, header);
        }

        int maxRows = Math.min(candles.size(), MAX_TABLE_ROWS);
        Font cellFont = createFont(regular, 8, WHITE);
        Font greenFont = createFont(regular, 8, GREEN);
        Font redFont = createFont(regular, 8, RED);

        for (int i = 0; i < maxRows; i++) {
            HistoricCandle candle = candles.get(i);
            Color bgColor = (i % 2 == 0) ? ROW_EVEN : ROW_ODD;

            BigDecimal open = PriceUtils.toBigDecimal(candle.getOpen());
            BigDecimal close = PriceUtils.toBigDecimal(candle.getClose());
            BigDecimal candleChange = open.compareTo(BigDecimal.ZERO) > 0
                    ? close.subtract(open)
                    .divide(open, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            String changeSign = candleChange.signum() >= 0 ? "+" : "";
            Font changeFont = candleChange.signum() >= 0 ? greenFont : redFont;

            addDataCell(table, formatCandleDate(candle), cellFont, bgColor);
            addDataCell(table, formatPrice(open), cellFont, bgColor);
            addDataCell(table, formatPrice(PriceUtils.toBigDecimal(candle.getHigh())),
                    cellFont, bgColor);
            addDataCell(table, formatPrice(PriceUtils.toBigDecimal(candle.getLow())),
                    cellFont, bgColor);
            addDataCell(table, formatPrice(close), cellFont, bgColor);
            addDataCell(table, formatVolume(candle.getVolume()), cellFont, bgColor);
            addDataCell(table,
                    changeSign + candleChange.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%",
                    changeFont, bgColor);
        }

        if (candles.size() > MAX_TABLE_ROWS) {
            Font noteFont = createFont(regular, 8, GRAY);
            PdfPCell noteCell = new PdfPCell(
                    new Phrase("\u2026ещё %d записей (показаны первые %d)".formatted(
                            candles.size() - MAX_TABLE_ROWS, MAX_TABLE_ROWS), noteFont));
            noteCell.setColspan(7);
            noteCell.setBorderWidth(0);
            noteCell.setPadding(8);
            noteCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            noteCell.setBackgroundColor(HEADER_BG);
            table.addCell(noteCell);
        }

        doc.add(table);
    }

    private void addFooter(Document doc, BaseFont regular) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(12);
        doc.add(spacer);

        Font footerFont = createFont(regular, 7, FOOTER_GRAY);
        Paragraph footer = new Paragraph(
                "Отчёт сгенерирован автоматически сервисом StockApp. "
                        + "Данные предоставлены T-Invest API. "
                        + "Не является индивидуальной инвестиционной рекомендацией.",
                footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    // ── Cell Helpers ──────────────────────────────────────────

    private void addStatCard(PdfPTable table, BaseFont bold, BaseFont regular,
                             String label, String value, Color valueColor) {
        Font labelFont = createFont(regular, 8, GRAY);
        Font valueFont = createFont(bold, 12, valueColor);

        PdfPCell cell = new PdfPCell();
        cell.setBorderWidth(2);
        cell.setBorderColor(PAGE_BG);
        cell.setPadding(10);
        cell.setPaddingBottom(12);
        cell.setBackgroundColor(CARD_BG);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", labelFont));
        p.add(new Chunk(value, valueFont));
        cell.addElement(p);

        table.addCell(cell);
    }

    private void addTableHeaderCell(PdfPTable table, BaseFont bold, String text) {
        Font headerFont = createFont(bold, 9, ACCENT);
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderWidth(0);
        cell.setBorderWidthBottom(1f);
        cell.setBorderColorBottom(ACCENT);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void addKeyValueRow(PdfPTable table, BaseFont regular,
                                String key, String value, int rowIdx) {
        Color bgColor = (rowIdx % 2 == 0) ? ROW_EVEN : ROW_ODD;
        Font keyFont = createFont(regular, 9, GRAY);
        Font valueFont = createFont(regular, 9, WHITE);

        PdfPCell keyCell = new PdfPCell(new Phrase(key, keyFont));
        keyCell.setBackgroundColor(bgColor);
        keyCell.setBorderWidth(0);
        keyCell.setPadding(7);
        table.addCell(keyCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBackgroundColor(bgColor);
        valueCell.setBorderWidth(0);
        valueCell.setPadding(7);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addDataCell(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderWidth(0);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private void addSeparator(Document doc) throws DocumentException {
        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        sep.setSpacingBefore(8);
        sep.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidth(0);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(SEPARATOR);
        cell.setFixedHeight(1);
        cell.setPadding(0);
        sep.addCell(cell);
        doc.add(sep);
    }

    // ── Utility Methods ───────────────────────────────────────

    private Font createFont(BaseFont baseFont, float size, Color color) {
        Font font = new Font(baseFont, size);
        font.setColor(color);
        return font;
    }

    private BaseFont loadFont(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Font not found in classpath: " + resourcePath);
            }
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont(resourcePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, fontBytes, null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load font: " + resourcePath, e);
        }
    }

    private LocalDate extractCandleDate(HistoricCandle candle) {
        var ts = candle.getTime();
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())
                .atZone(MOSCOW_ZONE)
                .toLocalDate();
    }

    private String formatCandleDate(HistoricCandle candle) {
        return extractCandleDate(candle).format(DATE_FMT);
    }

    private String formatPrice(BigDecimal price) {
        return PRICE_FORMAT.get().format(price);
    }

    private String formatVolume(long volume) {
        return VOLUME_FORMAT.get().format(volume);
    }

    private String periodLabel(String period) {
        return switch (period) {
            case "1m" -> "1 месяц";
            case "3m" -> "3 месяца";
            case "6m" -> "6 месяцев";
            case "1y" -> "1 год";
            default -> period;
        };
    }

    private LocalDate resolveFromDate(LocalDate to, String period) {
        return switch (period) {
            case "1m" -> to.minusMonths(1);
            case "3m" -> to.minusMonths(3);
            case "6m" -> to.minusMonths(6);
            case "1y" -> to.minusYears(1);
            default -> throw new IllegalArgumentException(
                    "Invalid period: %s. Allowed: 1m, 3m, 6m, 1y".formatted(period));
        };
    }

    // ── Page Event Handler ────────────────────────────────────

    private static class DarkPageBackground extends PdfPageEventHelper {
        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            canvas.setColorFill(PAGE_BG);
            canvas.rectangle(0, 0,
                    document.getPageSize().getWidth(),
                    document.getPageSize().getHeight());
            canvas.fill();
            canvas.restoreState();
        }
    }
}
