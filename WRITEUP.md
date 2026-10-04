# Seat Reservation Write-up

## Atomic decision and race-free correctness

The reservation decision is executed in a single application-critical step inside `ReservationService.reserve()`. The service validates the user, checks the per-user quota, sorts the requested seats, verifies availability in the current seat state, and then atomically updates the reservation map and the show inventory. This is a read-modify-write flow that is guarded by the in-memory state for this app, and the design is intentionally structured so a hot-seat race cannot result in two simultaneous confirmations.

For a multi-seat request, the code normalizes and sorts the seat list before validation to keep the decision deterministic and to avoid deadlock-like ordering issues across requests. The reservation path refuses any unavailable seat and rejects over-limit or duplicate idempotency requests before mutating the state.

## Idempotency model

The same idempotency key is stored per user in the `idempotencyByUser` map. The key is tied to the original request hash and reservation id. A retry with the same key and the same seat list returns the original reservation; a retry with the same key but different seat names is rejected with a 409 conflict. This makes the operation exactly-once from the caller perspective and prevents accidental double charging.

## Holds and expiry

This implementation uses explicit cancellation rather than a timed hold. A confirmed reservation can be cancelled by its owner, and the seat inventory is returned to available state by `cancelSeats()`. The code rejects any cancellation attempt by another user and treats a second cancellation as idempotent and safe.

## Consistency vs availability

The service favors correctness over partial success. If a requested seat is unavailable or the user exceeds their quota, the request fails with a clean 4xx domain error instead of creating a partial mutation. In other words, reservations are all-or-nothing per request. This gives a strict correctness model, which is the correct trade-off for a seat inventory system under load.

## Observability and operational signals

The app exposes a health endpoint (`/healthz`), a readiness check (`/readyz`), and Prometheus metrics at `/metrics`. The counters track confirmed reservations, cancelled reservations, seats sold, and declined reservations by reason. This is the minimum set needed to tell whether the system is healthy under an on-sale burst and whether seat inventory is reconciling with the API state.

## AI usage

AI tooling was used to accelerate scaffold generation, compare the implementation against the assignment, and validate the code path for auth, reservations, and observability. The final design decisions and correctness checks were reviewed and adjusted by the human operator before the implementation was kept.

## What to do next

The next operational step would be a live deployment to a public host, plus a real concurrency burst against a warm database and the `/metrics` endpoint. In production, I would also add request-correlation logs, a stricter database-backed reservation model for true multi-instance concurrency, and a dedicated expiry mechanism if the business chooses timed holds instead of explicit cancellation.
