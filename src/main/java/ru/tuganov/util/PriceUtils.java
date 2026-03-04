package ru.tuganov.util;

import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;

/**
 * Утилита для конвертации типов цен Tinkoff API (Quotation) в BigDecimal.
 */
public final class PriceUtils {

    private PriceUtils() {
    }

    /**
     * Конвертирует Quotation из Tinkoff API в BigDecimal.
     * Quotation хранит цену как units (целая часть) + nano (миллиардные доли).
     * Например: units=300, nano=500000000 -> 300.50
     */
    public static BigDecimal toBigDecimal(Quotation quotation) {
        if (quotation.getUnits() == 0 && quotation.getNano() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano(), 9));
    }
}
