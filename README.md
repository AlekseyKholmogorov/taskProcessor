# Vert.x Background Tasks Processor

Полноценный backend-проект на базе Java 21 и Vert.x 5.1.6. 
Проект реализует паттерн фоновой обработки задач с асинхронным возвратом прогресса клиенту через WebSocket.

## Особенности архитектуры
- **Серверная часть**: Vert.x (HttpServer, Router, EventBus).
- **Асинхронность**: Изолированная обработка задач без блокировки основного потока.
- **Адресная доставка прогресса**: Уведомления публикуются во внутреннюю шину `task.progress`, а `HttpServerVerticle` перенаправляет их через `WebSocketRegistry` строго в активный WebSocket конкретного `userId`.
- **База данных**: PostgreSQL, используется неблокирующий реактивный драйвер `vertx-pg-client`.
- **Сборка**: Gradle Shadow Plugin для сборки исполняемого fat-jar.

---

## Требования к окружению

Для запуска приложения вам потребуется:
- **Java 21** (для локального запуска)
- **Docker** и **Docker Compose** (для контейнеризации)
- **PostgreSQL 18.6** (если запускаете БД отдельно от Docker Compose)

---

## Инструкция по запуску

### Вариант 1: Запуск через Docker Compose (Рекомендуемый)
Это самый простой способ, так как он автоматически поднимет и базу данных, и само приложение.

1. Убедитесь, что Docker запущен.
2. В корневой директории проекта выполните команду:
   ```bash
   docker-compose up --build -d
   ```
3. Приложение будет доступно по адресу `http://localhost:8080`.

### Вариант 2: Локальный запуск (для разработки)
Если вы хотите запустить приложение напрямую через Gradle, вам потребуется запущенный экземпляр PostgreSQL.

1. Убедитесь, что PostgreSQL запущен и создана база task_db с пользователем vertx.
2. Задайте переменные окружения для подключения к БД.

   Linux/macOS:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=task_db
   export DB_USER=vertx
   export DB_PASSWORD=vertx_password
   ```

   Windows (PowerShell):
   ```powershell
   $env:DB_HOST="localhost"
   $env:DB_PORT="5432"
   $env:DB_NAME="task_db"
   $env:DB_USER="vertx"
   $env:DB_PASSWORD="vertx_password"
   ```

   Альтернатива: скопируйте `config/app/config.example.json` в `config/app/config.json`, задайте `DB_PASSWORD` и запускайте через `./gradlew run`.
3. Выполните сборку и запуск приложения с помощью Gradle-обертки:
   ```bash
   ./gradlew shadowJar
   java -jar build/libs/vertx-background-tasks-1.0.0-fat.jar
   ```

---

## Тесты

Проект покрыт unit- и integration-тестами (JUnit 5, Vert.x JUnit5, Mockito, Testcontainers + PostgreSQL).
Для integration-тестов нужен Docker.

```bash
./gradlew test
```

На Windows: `gradlew.bat test`.

Отчёт JaCoCo: `build/reports/jacoco/test/html/index.html`.
Порог покрытия в сборке: 95% (`jacocoTestCoverageVerification`).

---

## Инструкция по использованию (API и WebSocket)

Сценарий работы: клиент открывает WebSocket-соединение для слушания событий своего userId, после чего отправляет REST-запрос на создание задачи.

### 1. Подключение к сокету
Подключитесь к WebSocket, передав userId прямо в пути URL:

* **URL**: `ws://localhost:8080/ws/tasks/{userId}`

**Пример на JavaScript (можно выполнить прямо в консоли браузера):**
```javascript
const socket = new WebSocket('ws://localhost:8080/ws/tasks/1');
socket.onopen = () => console.log('✅ WebSocket подключен!');
socket.onmessage = (event) => console.log('📩 Сообщение от сервера:', JSON.parse(event.data));
socket.onerror = (error) => console.error('❌ Ошибка WS:', error);
socket.onclose = () => console.log('🔌 Соединение закрыто');
```
Сразу после подключения сервер отправит подтверждение: {"message":"Connected"}.

### 2. Запуск фоновой задачи
Инициируйте создание задачи, передав JSON с userId.

* **Endpoint**: `POST /api/tasks`
* **Content-Type**: `application/json`

**Пример запроса через cURL:**
```bash
 curl -i -X POST http://localhost:8080/api/tasks -H "Content-Type: application/json" -d '{"userId": 1}'
```

**Ожидаемый ответ от REST API:**
```json
{
  "taskId": 1,
  "status": "IN_PROGRESS"
}
```

### 3. Получение уведомлений
В открытое WebSocket-соединение каждую секунду начинают поступать сообщения с шагом в 20%

```json
{"taskId": 1, "userId": 1, "progress": 20, "status": "IN_PROGRESS"}
{"taskId": 1, "userId": 1, "progress": 40, "status": "IN_PROGRESS"}
{"taskId": 1, "userId": 1, "progress": 60, "status": "IN_PROGRESS"}
{"taskId": 1, "userId": 1, "progress": 80, "status": "IN_PROGRESS"}
{"taskId": 1, "userId": 1, "progress": 100, "status": "COMPLETED"}
```
