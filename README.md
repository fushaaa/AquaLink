AQUAlink is a prototype water-access coordination platform built for the Xylem Global Student Innovation Challenge (Water Access). In many informal settlements, households buy water from carts, tuktuks and kiosks with no coordination and unreliable supply. AQUAlink lets residents on basic phones dial a USSD code to request water, join a shared order for their plot, and report when supply starts or stops. Vendors accept pooled orders and record deliveries, and the collected supply history feeds a rule-based risk score that can later support infrastructure planning.

## SMS ordering and assistance

The Twilio inbound webhook is `POST /api/v1/sms/inbound`. Configure the Twilio phone number to send incoming messages to the public HTTPS URL for this endpoint. Set `AQUALINK_TWILIO_AUTH_TOKEN` so the app verifies Twilio's `X-Twilio-Signature`; set `AQUALINK_TWILIO_WEBHOOK_URL` to the exact public webhook URL configured in Twilio, especially when the app is behind a proxy.

For outbound order-created notifications, also configure `AQUALINK_TWILIO_ACCOUNT_SID` and `AQUALINK_TWILIO_FROM_NUMBER`. Orders placed through the REST API are notified after the database transaction commits. Orders placed by SMS are confirmed in the Twilio webhook reply.

SMS users must already exist in `users` with their phone in E.164 format and be linked to a household through `household_users`. If a user has several households, the one marked `is_primary` is used.

Supported commands:

- `ORDER <litres> <start-UTC> <end-UTC>`, for example `ORDER 1000 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z`
- `STATUS` to check the latest order for the primary household
- `HELP` for instructions

Other messages receive AI assistance when `AQUALINK_AI_API_KEY` is configured. The optional `AQUALINK_AI_BASE_URL` and `AQUALINK_AI_MODEL` settings select an OpenAI-compatible chat-completions endpoint and model. Without an API key, the app returns built-in ordering instructions. When enabled, the incoming help message is sent to the configured AI provider; do not include passwords or payment details in SMS.

## Run locally

Install Docker Desktop, copy `.env.example` to `.env`, set a local `MYSQL_ROOT_PASSWORD`, then start the app and database from the project root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Open `http://localhost:8080`. The demo login is `+15555550123` with password `password`. This account is seeded only for local development; change or remove it before using any shared or deployed environment. The database schema and seed are run when the MySQL data volume is created for the first time. To reinitialize a local database, remove the `aqualink-mysql` Compose volume; this deletes its data.

The resident UI uses HTTP Basic authentication over the same origin. The authenticated APIs are `GET /api/v1/me`, `GET /api/v1/households`, `GET /api/v1/water-orders`, `GET /api/v1/water-orders/{publicId}`, and `POST /api/v1/water-orders`. Pooling runs require a community-manager or admin role scoped to the service point. Production deployments must terminate HTTPS, use unique strong passwords, and replace the demo seed with a proper account-registration or provisioning flow.
