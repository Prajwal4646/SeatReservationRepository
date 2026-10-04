# Seat Reservation App

This is a small Spring Boot service for managing seat availability, user reservations, idempotent retries, and basic operational health checks.

## Live app

Public URL:

```text
https://seat-reservation-app-production.up.railway.app
```

## What it does

The app supports the core flow for the assignment:

- create a show as an admin
- reserve seats as a user
- enforce per-user booking limits
- reject duplicate idempotency keys for the same user
- fetch reservation details
- cancel reservations
- expose health and readiness endpoints
- publish metrics for the app

## Local setup

### Run with Spring Boot

```bash
export DATABASE_URL='postgresql://postgres:postgres@localhost:5432/seats'
export JWT_SECRET='dev-secret-change-me'
export ADMIN_TOKEN='admin-dev-token'
./mvnw spring-boot:run
```

### Run with Docker Compose

```bash
docker compose up --build
```

The service listens on port `8080` by default.

## API examples

### Create a show (admin only)

```bash
curl -X POST http://localhost:8080/shows \
  -H 'Authorization: Bearer admin-dev-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "friday-night",
    "seats": ["A1","A2","A3","A4","A5"],
    "price_paise": 25000,
    "per_user_limit": 4
  }'
```

### Reserve seats

```bash
curl -X POST http://localhost:8080/shows/{showId}/reserve \
  -H 'Authorization: Bearer <valid-user-jwt>' \
  -H 'Idempotency-Key: key-123' \
  -H 'Content-Type: application/json' \
  -d '{
    "seats": ["A1"],
    "idempotency_key": "key-123"
  }'
```

### Fetch a reservation

```bash
curl -X GET http://localhost:8080/reservations/{reservationId} \
  -H 'Authorization: Bearer <valid-user-jwt>'
```

### Cancel a reservation

```bash
curl -X POST http://localhost:8080/reservations/{reservationId}/cancel \
  -H 'Authorization: Bearer <valid-user-jwt>'
```

### Health and metrics

```bash
curl http://localhost:8080/healthz
curl http://localhost:8080/readyz
curl http://localhost:8080/metrics
```

## Load test helper

There is a small burst script in the project root for quick concurrency checks.

```bash
chmod +x ./burst.sh
./burst.sh http://localhost:8080
```

It creates a show and then sends a burst of concurrent reservation attempts against the same seat.

## Quick Postman flow

1. Create a show using the admin token.
2. Generate a valid user JWT.
3. Reserve a seat with the same header and idempotency key flow.
4. Fetch the reservation and then cancel it.
5. Check that the available seat list updates correctly.

## Observability

- `/healthz` — basic app health
- `/readyz` — readiness check for dependencies
- `/metrics` — Prometheus-style metrics

## Notes

A few contract details matter in practice:

- create-show uses snake_case fields like `price_paise`, `per_user_limit`, and `seats`
- responses use camelCase names like `pricePaise` and `perUserLimit`
- user requests need a valid JWT
- admin requests need to match the configured admin token value exactly
