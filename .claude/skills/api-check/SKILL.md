---
name: api-check
description: Validate ApiClient endpoints against expected API contract — request shapes, response parsing, error handling
disable-model-invocation: true
---

Verify the Vizoguard ApiClient implementation against the expected API contract.

## Steps

1. **Read `api/ApiClient.kt`** and catalog all endpoint calls:
   - HTTP method and path
   - Request body shape (serialized class)
   - Response type and parsing
   - Error handling

2. **Cross-reference with CLAUDE.md** expected endpoints:
   - `POST /license` — license activation
   - `POST /vpn/create` — provision VPN server
   - `GET /vpn/get` — get access URL
   - `GET /vpn/status` — check server status
   - `GET /health` — health check

3. **Check each endpoint for**:
   - Base URL is `https://vizoguard.com/api` (not hardcoded elsewhere)
   - Correct HTTP method used
   - Request body matches expected fields
   - Response deserialization handles all fields
   - HTTP error codes handled (4xx, 5xx)
   - Network exceptions caught (timeout, connectivity)
   - No sensitive data in URL query params (keys, passwords)

4. **Review ApiClientTest.kt** for test coverage:
   - Each endpoint has at least one success and one failure test
   - Mock responses match actual API shapes

5. **Output a contract report**:

| Endpoint | Method | Request OK | Response OK | Error Handling | Test Coverage |
|----------|--------|------------|-------------|----------------|---------------|
| `/license` | POST | Y/N | Y/N | Y/N | Y/N |

Flag any mismatches or missing coverage.
