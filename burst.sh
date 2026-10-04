#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
SHOW_NAME="friday-night"
SEAT_LIST="A1 A2 A3 A4 A5 A6 A7 A8 A9 A10"
TOTAL_USERS="20"
HOT_SEAT="A1"
ADMIN_TOKEN="admin-dev-token"
JWT_SECRET="dev-secret-change-me"

python3 - "$BASE_URL" "$SHOW_NAME" "$SEAT_LIST" "$TOTAL_USERS" "$HOT_SEAT" "$ADMIN_TOKEN" "$JWT_SECRET" <<'PY'
import base64, hashlib, hmac, json, os, sys, threading, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

base_url, show_name, seat_list_raw, total_users, hot_seat, admin_token, jwt_secret = sys.argv[1:8]
seats = [s.strip() for s in seat_list_raw.split() if s.strip()]


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def sign(payload: dict) -> str:
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    p = b64url(json.dumps(payload, separators=(",", ":")).encode())
    sig = hmac.new(jwt_secret.encode(), f"{header}.{p}".encode(), hashlib.sha256).digest()
    return f"{header}.{p}.{b64url(sig)}"


def request(path, method="GET", token=None, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req_headers = {"Content-Type": "application/json"}
    if token:
        req_headers["Authorization"] = f"Bearer {token}"
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(f"{base_url}{path}", data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode(), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(), e.headers
    except Exception as e:
        return 599, str(e), {}


def main():
    admin_auth = sign({"sub": "admin", "exp": int(time.time()) + 3600})
    show_payload = {"name": show_name, "seats": seats, "price_paise": 25000, "per_user_limit": 4}
    status, body, _ = request("/shows", method="POST", token=admin_auth, body=show_payload)
    print(f"Create show -> status={status}, body={body[:200]}")
    if status >= 300:
        raise SystemExit(1)
    created = json.loads(body)
    show_id = created["id"]

    results = {"confirmed": 0, "declined": 0, "errors": 0, "replayed": 0}
    lock = threading.Lock()

    def worker(i):
        token = sign({"sub": f"user-{i}", "exp": int(time.time()) + 3600})
        payload = {"seats": [hot_seat], "idempotency_key": f"burst-{i}"}
        status_code, body_text, _ = request(f"/shows/{show_id}/reserve", method="POST", token=token, body=payload)
        with lock:
            if status_code == 201:
                results["confirmed"] += 1
            elif status_code == 200:
                results["replayed"] += 1
            elif status_code >= 400 and status_code < 500:
                results["declined"] += 1
            elif status_code >= 500 or status_code == 599:
                results["errors"] += 1

    with ThreadPoolExecutor(max_workers=20) as pool:
        futures = [pool.submit(worker, i) for i in range(int(total_users))]
        for _ in as_completed(futures):
            pass

    print(json.dumps(results, indent=2, sort_keys=True))
    status, body, _ = request(f"/shows/{show_id}?include_seats=true", method="GET")
    print(f"Final show state -> {body[:500]}")


if __name__ == "__main__":
    main()
PY
