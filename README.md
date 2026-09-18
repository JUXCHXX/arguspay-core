<div align="center">

<img src="assets/logo.png" alt="ArgusPay" width="340" />

### API REST de pagos con idempotencia, atomicidad y concurrencia segura

<br/>

![Java](https://img.shields.io/badge/Java_21-000000?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-000000?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-000000?style=for-the-badge&logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Tests](https://img.shields.io/badge/tests-8_passing-000000?style=for-the-badge&logo=junit5&logoColor=white)

<br/>

[![Ver Dashboard](https://img.shields.io/badge/Ver_Dashboard-arguspay--dashboard-000000?style=for-the-badge&logo=react&logoColor=61DAFB)](https://github.com/JUXCHXX/arguspay-dashboard)

<br/>

[Características](#características) · [Stack](#stack) · [Arquitectura](#arquitectura) · [Cómo correrlo](#cómo-correrlo) · [Endpoints](#endpoints) · [Tests](#tests) · [Dashboard](#dashboard-web)

</div>

---

ArgusPay Core administra **cuentas y saldos** con depósitos, retiros y transferencias atómicas entre cuentas. Es un servicio pensado para ser consumido por un frontend, una app móvil o una integración externa.

El foco del proyecto es lo que separa un CRUD de un sistema de pagos: **idempotencia, atomicidad, control de concurrencia y seguridad**.

## Características

| | Característica | Detalle |
|---|---|---|
| 💰 | **Cuentas y saldos** | Precisión decimal con `NUMERIC(19,4)` |
| 🔁 | **Depósitos, retiros y transferencias** | Historial auditable de cada movimiento |
| 🛡️ | **Idempotencia** | `idempotencyKey` en el body o header `Idempotency-Key`: un reintento de red no cobra dos veces |
| ⚛️ | **Transferencias atómicas** | Las dos piernas (`TRANSFER_OUT` / `TRANSFER_IN`) se guardan en una sola transacción |
| 🔒 | **Concurrencia segura** | Locking optimista (`@Version`): dos operaciones simultáneas sobre el mismo saldo no lo dejan inconsistente |
| 🔑 | **Autenticación JWT** | HS256, con autorización por dueño de cuenta |
| 🚦 | **Rate limiting** | Por IP en autenticación y por usuario en el resto de la API |
| 📋 | **Errores unificados** | JSON con `timestamp`, `status`, `error` y `message` |
| 📖 | **Documentación interactiva** | Swagger UI con botón *Authorize* |
| 🧪 | **Tests de integración** | PostgreSQL real vía Testcontainers |

## Stack

| Tecnología | Uso |
|---|---|
| ![Java](https://img.shields.io/badge/Java_21-000000?style=flat-square&logo=openjdk&logoColor=white) | Lenguaje y runtime |
| ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1.1-000000?style=flat-square&logo=springboot&logoColor=white) | Framework principal y Spring Web MVC |
| ![Spring Security](https://img.shields.io/badge/Spring_Security-000000?style=flat-square&logo=springsecurity&logoColor=white) ![JWT](https://img.shields.io/badge/JWT_Nimbus-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) | Autenticación y autorización |
| ![Hibernate](https://img.shields.io/badge/Spring_Data_JPA-000000?style=flat-square&logo=hibernate&logoColor=white) | Persistencia con Hibernate |
| ![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-000000?style=flat-square&logo=postgresql&logoColor=white) | Base de datos |
| ![Flyway](https://img.shields.io/badge/Flyway-000000?style=flat-square&logo=flyway&logoColor=white) | Migraciones de esquema |
| ![Jakarta](https://img.shields.io/badge/Jakarta_Validation-000000?style=flat-square) | Validación de requests |
| ![Swagger](https://img.shields.io/badge/springdoc--openapi-000000?style=flat-square&logo=swagger&logoColor=white) | Swagger UI y OpenAPI |
| ![JUnit](https://img.shields.io/badge/JUnit_5-000000?style=flat-square&logo=junit5&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-000000?style=flat-square&logo=testcontainers&logoColor=white) | Tests de integración |
| ![Docker](https://img.shields.io/badge/Docker_Compose-000000?style=flat-square&logo=docker&logoColor=white) | PostgreSQL local |
| ![Maven](https://img.shields.io/badge/Maven_Wrapper-000000?style=flat-square&logo=apachemaven&logoColor=white) | Build |

## Arquitectura

Arquitectura en capas clásica: `controller → service → repository → entity`.

```mermaid
flowchart LR
    Client([Cliente]) --> RL[RateLimitFilter]
    RL --> JWT[Validación JWT]
    JWT --> AC[AuthController]
    JWT --> ACC[AccountController]
    AC --> AS[AuthService]
    ACC --> SV[AccountService]
    AS --> UR[(AppUserRepository)]
    SV --> AR[(AccountRepository)]
    SV --> TR[(TransactionRepository)]
    UR --> DB[(PostgreSQL)]
    AR --> DB
    TR --> DB
    EH[GlobalExceptionHandler] -. errores JSON .-> Client
```

### Modelo de datos

```mermaid
erDiagram
    APP_USER ||--o{ ACCOUNT : "es dueño de"
    ACCOUNT ||--o{ TRANSACTION : "registra"
    APP_USER {
        uuid id PK
        string email UK
        string password_hash
        timestamp created_at
    }
    ACCOUNT {
        uuid id PK
        numeric balance
        uuid owner_id FK
        bigint version
        timestamp created_at
    }
    TRANSACTION {
        uuid id PK
        uuid account_id FK
        numeric amount
        string type
        string status
        string idempotency_key UK
        uuid related_account_id
        timestamp created_at
    }
```

### Flujo de una transferencia

```mermaid
sequenceDiagram
    participant C as Cliente
    participant S as AccountService
    participant DB as PostgreSQL
    C->>S: POST /accounts/{id}/transfer
    S->>S: Verifica dueño de la cuenta origen
    S->>S: Verifica idempotencyKey
    S->>S: Verifica fondos suficientes
    Note over S,DB: Una sola transacción de base de datos
    S->>DB: TRANSFER_OUT (origen) y saldo -X
    S->>DB: TRANSFER_IN (destino) y saldo +X
    DB-->>S: commit (o rollback si hay conflicto de versión)
    S-->>C: 200 OK
```

### Estructura del proyecto

```
arguspay-core/
├── src/main/java/com/arguspay/core/
│   ├── config/        SecurityConfig, RateLimitFilter, OpenApiConfig
│   ├── controller/    AuthController, AccountController
│   ├── service/       AuthService, AccountService, JwtService
│   ├── repository/    AppUser, Account y Transaction repositories
│   ├── entity/        AppUser, Account, Transaction + enums
│   ├── dto/           Requests y responses
│   └── exception/     GlobalExceptionHandler y excepciones de dominio
├── src/main/resources/db/migration/   V1, V2 y V3 (Flyway)
└── src/test/java/                     Tests de integración (Testcontainers)
```

## Cómo correrlo

Requisitos: **Java 21** y **Docker**.

```bash
docker compose up -d
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080` y Swagger UI en `http://localhost:8080/swagger-ui.html`.

### Configuración

Se define en `src/main/resources/application.properties`:

| Propiedad | Descripción |
|---|---|
| `arguspay.jwt.secret` | Secreto HS256 (mínimo 32 caracteres). Se lee de la variable de entorno `ARGUSPAY_JWT_SECRET`; en desarrollo tiene un valor por defecto |
| `arguspay.jwt.expiration-minutes` | Duración del token (60 por defecto) |
| `arguspay.rate-limit.auth-per-minute` | Límite por IP en `/api/auth/**` |
| `arguspay.rate-limit.api-per-minute` | Límite por usuario en el resto de la API |

En producción, define un secreto propio:

```bash
export ARGUSPAY_JWT_SECRET="$(openssl rand -base64 48)"
```

## Endpoints

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Registra un usuario y devuelve un token | No |
| `POST` | `/api/auth/login` | Inicia sesión y devuelve un token | No |
| `GET` | `/api/accounts` | Lista las cuentas del usuario | Sí |
| `POST` | `/api/accounts` | Crea una cuenta con saldo 0 | Sí |
| `GET` | `/api/accounts/{id}/balance` | Consulta el saldo | Sí (dueño) |
| `POST` | `/api/accounts/{id}/deposit` | Deposita dinero | Sí (dueño) |
| `POST` | `/api/accounts/{id}/withdraw` | Retira dinero | Sí (dueño) |
| `POST` | `/api/accounts/{id}/transfer` | Transfiere a otra cuenta | Sí (dueño del origen) |
| `GET` | `/api/accounts/{id}/transactions` | Historial, más reciente primero | Sí (dueño) |

<details>
<summary><b>Ver ejemplos con curl</b></summary>

<br/>

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@test.com", "password": "clave12345"}'
```

```bash
curl -X POST http://localhost:8080/api/accounts/{ID}/deposit \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "idempotencyKey": "dep-001"}'
```

</details>

### Códigos de error

| Código | Cuándo |
|---|---|
| `400` | Request inválido (monto negativo, transferencia a la misma cuenta) |
| `401` | Sin token, token inválido o credenciales incorrectas |
| `403` | La cuenta pertenece a otro usuario |
| `404` | Cuenta inexistente |
| `409` | `idempotencyKey` repetida, email ya registrado o conflicto de concurrencia |
| `422` | Fondos insuficientes |
| `429` | Límite de peticiones superado (incluye header `Retry-After`) |

## Tests

```bash
./mvnw test
```

Requiere Docker: Testcontainers levanta un PostgreSQL 16 propio para los tests. Son **8 pruebas** que cubren autenticación, depósito, idempotencia, fondos insuficientes, transferencia con historial, aislamiento entre usuarios y **dos retiros simultáneos** (verifican que el saldo nunca queda negativo).

## Decisiones de diseño

- **Idempotencia antes que nada:** cada operación verifica su clave antes de tocar el saldo, y cada movimiento queda registrado en `transaction`.
- **Locking optimista en vez de locks de base de datos:** el campo `@Version` de `Account` detecta escrituras concurrentes y responde `409` sin bloquear filas.
- **Sin filtrar entidades JPA:** los controladores responden con DTOs.
- **Rate limiting en memoria:** simple y sin dependencias, válido para una sola instancia.

## Dashboard web

Interfaz en **React + Vite** (blanco y negro, estilo flat) que consume esta API. Vive en un repositorio aparte.

[![Ver Dashboard](https://img.shields.io/badge/Ver_Dashboard-arguspay--dashboard-000000?style=for-the-badge&logo=react&logoColor=61DAFB)](https://github.com/JUXCHXX/arguspay-dashboard)

- Login y registro con JWT
- **Resumen:** saldo total, cuentas y saldos, crear cuenta
- **Movimientos:** depositar, retirar, transferir e historial por cuenta

Para correrlo, con esta API en `localhost:8080`:

```bash
pnpm install
pnpm dev
```

El servidor de Vite usa un proxy de `/api` hacia `http://localhost:8080`, así que no hace falta configurar CORS.

## Roadmap

- [x] Dashboard web desacoplado (React + Vite)
- [ ] Alias por cuenta y transferencias por correo
- [ ] Rate limiting distribuido (Redis) para varias instancias
- [ ] Refresh tokens y revocación
- [ ] Paginación en el historial de movimientos
- [ ] Despliegue

---

<div align="center">

Hecho por [JUXCHXX](https://github.com/JUXCHXX)

</div>