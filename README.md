# AlertService

Микросервис ценовых алертов, мониторинга, уведомлений и отчётов.
Является частью микросервисной архитектуры StockApp.

---

## Роль в архитектуре

| Сервис | Порт | Зона ответственности |
|--------|------|----------------------|
| MarketDataService | 8080 | Поиск инструментов, свечи |
| AuthService | 8081 | Аутентификация, профили, JWT |
| **AlertService** (этот) | 8082 | Ценовые алерты, мониторинг, уведомления, отчёты |

Все сервисы используют одну PostgreSQL БД (`market_service`).
AlertService **владеет** таблицами `tracked_instruments`, `notifications`, `shedlock`.
Таблица `app_users` принадлежит AuthService — AlertService только читает из неё.

JWT-токены выданные AuthService принимаются AlertService — оба сервиса используют один `JWT_SECRET`.

---

## Функциональность

- **CRUD ценовых алертов** — создание, просмотр, редактирование и удаление отслеживаемых инструментов
- **Мониторинг цен** — каждые 5 секунд проверяет текущие цены через Tinkoff Invest API (ShedLock защищает от дублирования в кластере)
- **Генерация алертов** — при выходе цены за `buyPrice` или `sellPrice` создаётся событие
- **Уведомления** — отправка через Telegram Bot и/или email, сохранение истории
- **Отчёты** — генерация PDF и Markdown отчётов по портфельной аналитике (JFreeChart + OpenPDF)

### Логика мониторинга

1. Цена <= `buyPrice` → **BUY-алерт** (цена упала — можно покупать)
2. Цена >= `sellPrice` → **SELL-алерт** (цена выросла — можно продавать)
3. Алерт срабатывает **однократно**. После возврата цены в коридор `[buyPrice, sellPrice]` флаг сбрасывается.

---

## REST API

Все эндпоинты требуют JWT-токен в заголовке `Authorization: Bearer <token>`.

### Ценовые алерты (`/api/tracked-instruments/**`)

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/tracked-instruments` | Создать алерт |
| `GET` | `/api/tracked-instruments` | Все алерты текущего пользователя |
| `GET` | `/api/tracked-instruments/{id}` | Конкретный алерт (проверка владельца) |
| `PUT` | `/api/tracked-instruments/{id}` | Обновить ценовые границы (сбрасывает флаги) |
| `DELETE` | `/api/tracked-instruments/{id}` | Удалить |

Пример запроса создания:
```json
{
  "figi": "BBG004730N88",
  "instrumentName": "Сбербанк",
  "buyPrice": 300.00,
  "sellPrice": 350.00
}
```

### Уведомления (`/api/notifications/**`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/notifications` | История срабатывания алертов (последние 50) |

### Отчёты (`/api/reports/**`)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/reports/download?period=3m&format=pdf` | Скачать отчёт |

Доступные значения `period`: `1m`, `3m`, `6m`, `1y`. Формат: `pdf`, `md`.

---

## Структура проекта

```
src/main/java/ru/tuganov/
├── AlertServiceApplication.java        — @EnableScheduling, @EnableAsync, alertExecutor bean
├── config/
│   ├── InvestApiConfiguration.java     — singleton-бин InvestApi
│   ├── SchedulerConfig.java            — LockProvider для ShedLock
│   └── SecurityConfig.java             — все эндпоинты требуют JWT
├── controller/
│   ├── NotificationController.java
│   ├── ReportController.java
│   └── TrackedInstrumentController.java
├── dto/
│   ├── NotificationResponse.java
│   ├── Period.java
│   ├── PriceAlertEvent.java
│   ├── TrackedInstrumentRequest.java
│   └── TrackedInstrumentResponse.java
├── entity/
│   ├── AppUser.java                    — read-only view (таблица принадлежит AuthService)
│   ├── Notification.java
│   └── TrackedInstrument.java
├── exception/
│   ├── GlobalExceptionHandler.java     — RFC 7807
│   └── ResourceNotFoundException.java
├── repository/
│   ├── AppUserRepository.java          — только findById, findByTelegramId
│   ├── NotificationRepository.java
│   └── TrackedInstrumentRepository.java — + findAllWithUsers() JOIN FETCH
├── security/                           — 5 классов JWT (общий секрет с AuthService)
│   ├── AppUserDetails.java
│   ├── AppUserDetailsService.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenService.java
│   └── TelegramInitDataValidator.java
├── service/
│   ├── AlertNotificationService.java   — @TransactionalEventListener(AFTER_COMMIT) + REQUIRES_NEW
│   ├── EmailService.java               — email-уведомления об алертах
│   ├── PriceHistoryService.java        — получение свечей (для отчётов)
│   ├── PriceMonitoringService.java     — @Scheduled + @DistributedLock (ShedLock)
│   ├── ReportService.java              — PDF/MD генерация (ThreadLocal.remove() в finally)
│   ├── TelegramBotService.java         — отправка сообщений в Telegram
│   └── TrackedInstrumentService.java   — CRUD с проверкой владельца
└── util/
    └── PriceUtils.java
```

---

## Миграции Flyway

| Файл | Содержание |
|------|------------|
| `V1__create_tracked_and_notifications.sql` | Таблицы `tracked_instruments` и `notifications` (FK → app_users) |
| `V2__create_shedlock.sql` | Таблица `shedlock` для распределённых блокировок |

> Таблица `app_users` создаётся AuthService. AlertService должен стартовать **после** AuthService.

---

## Настройки

```yaml
# application.yml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/market_service
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  mail:
    host: smtp.yandex.ru
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}

invest:
  connector:
    token: ${INVEST_TOKEN}
  sandbox: ${INVEST_SANDBOX:true}

monitoring:
  enabled: true
  check-interval-ms: 5000

jwt:
  secret: ${JWT_SECRET}          # Тот же, что в AuthService!

telegram:
  bot:
    token: ${TELEGRAM_BOT_TOKEN}

app:
  frontend-url: ${FRONTEND_URL:http://localhost:3000}
```

| Переменная | Описание |
|-----------|----------|
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL |
| `INVEST_TOKEN` | Токен Tinkoff Invest API |
| `INVEST_SANDBOX` | `true` — sandbox, `false` — прод |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP (Yandex Mail) |
| `JWT_SECRET` | Должен совпадать с AuthService |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота для отправки сообщений |
| `FRONTEND_URL` | Для CORS |

---

## Запуск

```bash
# Создать .env
cat > .env << EOF
DB_USERNAME=postgres
DB_PASSWORD=postgres
INVEST_TOKEN=t.ваш_токен
MAIL_USERNAME=your@yandex.ru
MAIL_PASSWORD=your_app_password
JWT_SECRET=your_256_bit_secret
TELEGRAM_BOT_TOKEN=123456:ABC...
FRONTEND_URL=http://localhost:3000
EOF

# Запустить (AuthService должен стартовать первым — создаёт app_users)
./gradlew bootRun
```

---

## Тестирование (xh)

```bash
# Получить JWT (через AuthService)
TOKEN=$(xh POST localhost:8081/api/auth/login \
  email=test@example.com password=secret | jq -r .token)

# Создать алерт
xh POST localhost:8082/api/tracked-instruments \
  "Authorization:Bearer $TOKEN" \
  figi=BBG004730N88 \
  instrumentName=Сбербанк \
  buyPrice:=300.00 \
  sellPrice:=350.00

# Список алертов
xh GET localhost:8082/api/tracked-instruments \
  "Authorization:Bearer $TOKEN"

# История уведомлений
xh GET localhost:8082/api/notifications \
  "Authorization:Bearer $TOKEN"

# Скачать отчёт за 3 месяца в PDF
xh GET localhost:8082/api/reports/download \
  period==3m format==pdf \
  "Authorization:Bearer $TOKEN" \
  --download
```
