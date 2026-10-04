# Seat Reservation App

A Spring Boot seat reservation service built to handle concurrent seat holds and reservations with idempotency, auth, health checks, and Prometheus metrics.

## Features implemented

- Show creation via admin token
- Seat reservation with authenticated user identity
- Per-user seat quota enforcement
- Idempotency key handling for retries
- Owner-only cancel flow
- Health and readiness checks
- Prometheus metrics exposure
- Dockerized deployment config

## Local run

### Option 1: Spring Boot

```bash
export DATABASE_URL='postgresql://postgres:postgres@localhost:5432/seats'
export JWT_SECRET='dev-secret-change-me'
export ADMIN_TOKEN='admin-dev-token'
./mvnw spring-boot:run
```

### Option 2: Docker Compose

```bash
docker compose up --build
```

The app listens on port `8080` by default.

## API summary

### Create show (admin)

```bash
curl -X POST http://localhost:8080/shows \
  -H 'Authorization: Bearer <admin-token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "friday-night",
    "seats": ["A1","A2","A3","A4"],
    "price_paise": 25000,
    "per_user_limit": 4
  }'
```

### Reserve seat

```bash
curl -X POST http://localhost:8080/shows/{showId}/reserve \
  -H 'Authorization: Bearer <user-token>' \
  -H 'Idempotency-Key: key-123' \
  -H 'Content-Type: application/json' \
  -d '{
    "seats": ["A1"],
    "idempotency_key": "key-123"
  }'
```

### Cancel reservation

```bash
curl -X POST http://localhost:8080/reservations/{reservationId}/cancel \
  -H 'Authorization: Bearer <user-token>'
```

### Health and metrics

```bash
curl http://localhost:8080/healthz
curl http://localhost:8080/readyz
curl http://localhost:8080/metrics
```

## Burst test

A ready-to-run concurrency burst script is included at the project root:

```bash
chmod +x ./burst.sh
./burst.sh http://localhost:8080
```

It creates a show, fires concurrent seat requests against the same hot seat, and prints the confirmed/declined/error outcome distribution.

## Observability

- `/healthz` — lightweight liveness check
- `/readyz` — dependency-aware readiness check
- `/metrics` — Prometheus scrape format

## Notes

The app uses JWT-style bearer tokens for identity, not request-body user fields. This prevents spoofing and keeps the auth model aligned with the assignment.
