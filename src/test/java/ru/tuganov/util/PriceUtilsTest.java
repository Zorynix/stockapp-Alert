package ru.tuganov.util;

import org.junit.jupiter.api.Test;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PriceUtilsTest {

    @Test
    void toBigDecimal_unitsAndNano_convertsCorrectly() {
        Quotation q = Quotation.newBuilder().setUnits(300).setNano(500_000_000).build();
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(new BigDecimal("300.5"));
    }

    @Test
    void toBigDecimal_zeroQuotation_returnsZero() {
        Quotation q = Quotation.newBuilder().setUnits(0).setNano(0).build();
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void toBigDecimal_nanoOnly_convertsCorrectly() {
        Quotation q = Quotation.newBuilder().setUnits(0).setNano(250_000_000).build();
        // 0 units + 0.25 nano → 0.25
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void toBigDecimal_unitsOnly_convertsCorrectly() {
        Quotation q = Quotation.newBuilder().setUnits(1000).setNano(0).build();
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void toBigDecimal_largeValue_convertsCorrectly() {
        Quotation q = Quotation.newBuilder().setUnits(12345).setNano(678_000_000).build();
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(new BigDecimal("12345.678"));
    }

    @Test
    void toBigDecimal_defaultInstance_returnsZero() {
        Quotation q = Quotation.getDefaultInstance();
        assertThat(PriceUtils.toBigDecimal(q)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
