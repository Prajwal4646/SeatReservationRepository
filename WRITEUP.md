# Seat Reservation Write-up

This project started as a straightforward seat-booking service and ended up being a small system with a few important engineering constraints: seat availability, per-user limits, idempotent retries, and a clean operational surface for health checks and metrics.

The key idea was to keep the flow simple while still making the reservation logic safe under retry and concurrency scenarios. The app exposes a small admin API for creating shows and a user path for reserving, fetching, and cancelling bookings.

## Reservation model

A show is created with a set of seat labels and a per-user booking limit. Once a user successfully reserves a seat, that seat is removed from the available inventory for that show. The app also tracks the user’s consumption for that show so it can reject requests that would exceed the limit.

The reservation object stores the show id, user id, selected seats, amount, and status. Cancellation restores the seat back to the available pool and reduces the user’s seat count for that show.

## Correctness and retries

The important part of the backend is that retries do not create duplicate bookings. Each user has an idempotency key map, and the same key can only be reused with the exact same request payload. If the key is reused with different seats or a different request hash, the app returns a conflict instead of creating a second booking.

This is important because user retries and network glitches are common in real APIs. Without this guard, the same client action could be repeated and create duplicate reservations.

## Concurrency handling

The reservation path was designed so the availability check, quota check, and booking update are handled as one critical flow, instead of as separate unsynchronized reads and writes.

That matters because hot seats are a classic race condition. Without a guarded flow, two requests can both observe the same seat as available and then both succeed. The service avoids that by validating the seat state and then finalizing the booking in a single controlled path.

For the final version, the app also uses transactional database checks so the seat allocation is tied to the database state instead of relying only on in-memory state.

## Operational side

The app exposes `/healthz`, `/readyz`, and `/metrics` so it is easy to check whether the service is alive, whether dependencies are okay, and whether the app is reporting traffic correctly. That makes it easy to verify deployment health without digging through logs.

## Outcome

The final version is a working public seat reservation service that covers the expected flow for the assignment: create show, reserve seats, validate limits, reject duplicate requests, cancel reservations, and operate cleanly in a deployed environment.

It is simple, readable, and close to the kind of service you would expect in a coding challenge or a small internal product prototype. The core trade-off was to keep the code straightforward while still making sure the fundamental booking rules hold up under normal retry and concurrency conditions.
