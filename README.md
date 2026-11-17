# Облачное хранилище

Дипломный проект курса «Java-разработчик» (Нетология).
Это REST-сервис для загрузки, хранения, переименования, скачивания и удаления файлов.
Сервис реализует спецификацию `CloudServiceSpecification.yaml` и работает с готовым
фронтендом `netology-diplom-frontend` без его доработки.

Стек: Java 17, Spring Boot 3.5 (Web, Data JPA, Validation), PostgreSQL 16, Flyway,
BCrypt (`spring-security-crypto`), JUnit 5, Mockito, Testcontainers, Docker Compose.

## Схема приложения

```
 Браузер (Vue, :8081)
        │  HTTP + заголовок auth-token: Bearer <token>
        ▼
┌─────────────────────────── Spring Boot (:8080) ───────────────────────────┐
│  CORS (WebConfig) ─► AuthInterceptor ─► Controllers ─► Services ─► Repos  │
│                       (проверка        AuthController  AuthService  JPA   │
│                        токена для      FileController  FileService        │
│                        /file, /list)                                      │
│  GlobalExceptionHandler: любые ошибки -> {message, id} + 400/401/500      │
└───────────────────────────────────────┬───────────────────────────────────┘
                                        ▼
                        PostgreSQL: users, auth_tokens, files
```

## Архитектура

Пакеты в `src/main/java/ru/netology/cloud`:

| Пакет | Что внутри |
|-------|------------|
| `auth` | `AuthController` (`/login`, `/logout`), `AuthService` (проверка пароля BCrypt, выдача и отзыв токенов), `AuthInterceptor` (проверка `auth-token`), сущности `User`, `AuthToken` |
| `file` | `FileController` (`/file`, `/list`), `FileService` (бизнес-правила), `FileRepository`, сущность `StoredFile` |
| `error` | `GlobalExceptionHandler` и исключения `BadRequestException` (400), `UnauthorizedException` (401) |
| `config` | `WebConfig`: CORS, регистрация интерсептора, бин `BCryptPasswordEncoder` |

Авторизация:

1. `POST /login` проверяет логин и BCrypt-хеш пароля.
2. Затем создаёт случайный токен (UUID), сохраняет его в таблицу `auth_tokens` и возвращает `{"auth-token": "..."}`.
3. Для `/file` и `/list` интерсептор читает заголовок `auth-token`. Префикс `Bearer ` (его добавляет фронт) отбрасывается.
4. Интерсептор ищет токен в БД и кладёт `userId` в атрибут запроса. Нет токена — ответ 401.
5. `POST /logout` удаляет токен из БД. После этого любой запрос с ним получает 401.

Изоляция пользователей: каждый запрос к файлам фильтруется по `user_id` текущего пользователя.
Имена файлов уникальны только внутри одного пользователя.

## API

| Метод | Путь | Тело / параметры | Ответ |
|-------|------|------------------|-------|
| POST | `/login` | JSON `{login, password}` | `{"auth-token": "..."}`, при ошибке 400 |
| POST | `/logout` | заголовок `auth-token` | 200 |
| POST | `/file?filename=` | multipart: `file`, `hash` (необязателен) | 200 |
| GET | `/file?filename=` | — | байты файла (`application/octet-stream`) |
| PUT | `/file?filename=` | JSON `{"name": "новое имя"}` | 200 |
| DELETE | `/file?filename=` | — | 200 |
| GET | `/list?limit=` | — | `[{"filename": "...", "size": 123}]` |

Ошибки возвращаются в теле `{"message": "...", "id": N}`:
- 400 — неверные входные данные;
- 401 — нет токена или токен неверный;
- 500 — внутренняя ошибка.

Отличия от спецификации, сделанные ради совместимости с фронтом:
- **Скачивание.** `GET /file` отдаёт сами байты файла, а не multipart. Фронт читает ответ как blob и сохраняет как есть, поэтому multipart-обёртка испортила бы файл.
- **Переименование.** `PUT /file` принимает и `{"name": ...}` (как в спецификации), и `{"filename": ...}` (так шлёт фронт).
- **Хеш.** Поле `hash` необязательно, фронт его не шлёт. Если хеша нет, сервер считает MD5 сам.
- **Базовый путь.** В спецификации указан `/cloud`, но фронт обращается к корню сервера. Поэтому пути без префикса: `/login`, `/file`, `/list`.

## Структура таблиц

Миграции Flyway лежат в `src/main/resources/db/migration`:
- `V1__init.sql` — схема;
- `V2__seed_user.sql` — тестовый пользователь.

**users**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| login | VARCHAR(255) UNIQUE | логин (e-mail) |
| password_hash | VARCHAR(100) | BCrypt-хеш пароля |

**auth_tokens**

| Колонка | Тип | Описание |
|---------|-----|----------|
| token | VARCHAR(64) PK | выданный токен |
| user_id | BIGINT FK → users | владелец |
| created_at | TIMESTAMP WITH TIME ZONE | время входа |

**files**

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | владелец |
| filename | VARCHAR(255) | имя файла, `UNIQUE (user_id, filename)` |
| size | BIGINT | размер в байтах |
| hash | VARCHAR(255) | хеш от клиента или MD5 |
| content | BYTEA | содержимое файла |
| created_at | TIMESTAMP WITH TIME ZONE | время загрузки |

### Где хранятся файлы и почему

Содержимое файлов хранится в БД, в колонке `files.content` типа `bytea`. Причины:
- данные и метаданные меняются в одной транзакции, поэтому не бывает «осиротевших» файлов на диске или записей без файла;
- бэкап делается одним `pg_dump`, а в Docker нужен только один volume;
- переименование и удаление сводятся к одному SQL-запросу.

Минус: такой подход годится для файлов умеренного размера. Лимит задан в `application.yml` (50 МБ).
Для больших объёмов лучше хранить файлы на диске или в S3, а в БД оставить только метаданные.
Список файлов (`/list`) читает только имя и размер, без содержимого.

## Настройки

Все настройки лежат в `src/main/resources/application.yml`. Любую можно переопределить
переменной окружения:

| Переменная | По умолчанию | Назначение |
|------------|--------------|------------|
| `SERVER_PORT` | `8080` | порт сервиса |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cloud` | адрес БД |
| `DB_USERNAME` / `DB_PASSWORD` | `cloud` / `cloud` | учётка БД |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8081` | откуда разрешены запросы (через запятую) |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | `50MB` | лимит загрузки |

Тестовый пользователь (создаётся миграцией `V2`): логин **`user@example.com`**, пароль **`password`**.

## Запуск

### Через Docker Compose (приложение + PostgreSQL)

```bash
docker compose up --build
```

Сервис поднимется на `http://localhost:8080`, PostgreSQL — на `localhost:5432`.
Остановить и удалить данные:

```bash
docker compose down -v
```

### Локально (нужен запущенный PostgreSQL с БД `cloud`)

```bash
./mvnw spring-boot:run
```

На Windows вместо `./mvnw` используйте `mvnw.cmd`. Отдельно Maven ставить не нужно.

### Тесты

```bash
./mvnw test
```

- Unit-тесты (Mockito и `@WebMvcTest`) проверяют сервисы и контроллеры.
- Интеграционный тест `CloudScenarioIntegrationTest` поднимает PostgreSQL в Testcontainers и проходит полный сценарий: login → upload → list → rename → download → delete → logout → 401. Ещё он проверяет изоляцию файлов между пользователями. **Нужен запущенный Docker**, без него тест пропускается.

## Примеры curl

```bash
# вход
curl -X POST http://localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"login":"user@example.com","password":"password"}'
# -> {"auth-token":"<TOKEN>"}

# загрузка
curl -X POST 'http://localhost:8080/file?filename=hello.txt' -H 'auth-token: Bearer <TOKEN>' \
  -F 'file=@hello.txt' -F 'hash=any'

# список
curl 'http://localhost:8080/list?limit=3' -H 'auth-token: Bearer <TOKEN>'

# переименование
curl -X PUT 'http://localhost:8080/file?filename=hello.txt' -H 'auth-token: Bearer <TOKEN>' \
  -H 'Content-Type: application/json' -d '{"name":"renamed.txt"}'

# скачивание
curl -o out.txt 'http://localhost:8080/file?filename=renamed.txt' -H 'auth-token: Bearer <TOKEN>'

# удаление
curl -X DELETE 'http://localhost:8080/file?filename=renamed.txt' -H 'auth-token: Bearer <TOKEN>'

# выход: после него тот же токен получает 401
curl -X POST http://localhost:8080/logout -H 'auth-token: Bearer <TOKEN>'
```

Весь сценарий одной командой (нужны bash и curl; на Windows подойдёт Git Bash):

```bash
./scripts/curl-scenario.sh http://localhost:8080
```

## Подключение фронтенда (FRONT)

1. Установите Node.js и скачайте `netology-diplom-frontend` (папка `diploma` репозитория `netology-code/jd-homeworks`).
2. Проверьте, что в файле `.env` в корне фронта указан адрес бэкенда:
   ```
   VUE_APP_BASE_URL=http://localhost:8080
   ```
3. Установите зависимости и запустите фронт:
   ```bash
   npm install
   npm run serve
   ```
   На Node.js 20 и новее `npm install` падает на сборке `node-sass`: этот пакет поддерживает Node только до 19-й версии.
   Решение — заменить в `package.json` фронта `"node-sass": "^8.0.0"` на `"sass": "^1.69.0"`. Это только dev-зависимость, код фронта не меняется.
4. Фронт откроется на `http://localhost:8081`. Этот адрес уже разрешён в CORS (`app.cors.allowed-origins`).
5. Войдите под `user@example.com` / `password`.
