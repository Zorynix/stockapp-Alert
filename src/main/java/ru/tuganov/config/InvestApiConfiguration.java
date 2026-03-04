package ru.tuganov.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tinkoff.piapi.core.InstrumentsService;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.MarketDataService;

/**
 * Конфигурация подключения к Tinkoff Invest API.
 * Создает единый экземпляр InvestApi и предоставляет сервисы для работы
 * с инструментами и рыночными данными как Spring-бины.
 */
@Configuration
public class InvestApiConfiguration {

    /** Создает клиент Tinkoff Invest API. Токен берется из настроек или переменной окружения INVEST_TOKEN. */
    @Bean
    public InvestApi investApi(@Value("${invest.connector.token}") String apiToken,
                               @Value("${invest.sandbox:true}") boolean sandbox) {
        return sandbox ? InvestApi.createSandbox(apiToken) : InvestApi.create(apiToken);
    }

    /** Сервис для работы с инструментами (поиск акций, получение информации). */
    @Bean
    public InstrumentsService instrumentsService(InvestApi investApi) {
        return investApi.getInstrumentsService();
    }

    /** Сервис для работы с рыночными данными (цены, свечи, стаканы). */
    @Bean
    public MarketDataService marketDataService(InvestApi investApi) {
        return investApi.getMarketDataService();
    }
}
