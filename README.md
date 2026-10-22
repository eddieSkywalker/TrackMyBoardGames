# TrackMyBoardGames

A web app for managing a personal board-game collection, recording plays, and reviewing play history. The longer-term plan includes printable QR labels and camera scanning so a play can be logged quickly.

**Status:** Early development. Users can sign in with Auth0, and the React collection page can list and add board games through the Spring API. The backend creates a local profile linked to the Auth0 identity and restricts game queries to that profile. Play history, QR codes, scanning, and library insights are not implemented yet.

See [BUILD_PLAN.md](BUILD_PLAN.md) for planned milestones and release goals.

## Technology

- **Frontend:** React 19, TypeScript, Vite 8, Tailwind CSS, React Router, Auth0 React SDK, ESLint
- **Backend:** Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security OAuth2 Resource Server, Spring Data JPA, Hibernate, Spring Validation, Flyway
- **Database:** PostgreSQL
- **Authentication:** Auth0-hosted login and registration; securing backend API requests is still to be implemented

## Repository Layout

```text
backend/       Spring Boot API and Flyway migrations
frontend/      React single-page application
auth0/         Auth0 Actions, account-policy tests, and deployment instructions
BUILD_PLAN.md  Milestones, build plan, and roadmap
```

## Prerequisites

- Java 25 JDK
- Node.js 22.12+ (or 20.19+) and npm
- PostgreSQL running locally
- An Auth0 tenant with a Single Page Application for the frontend

The Maven wrapper (`mvnw.cmd`) downloads and uses the project's Maven version; a separate Maven installation is not required.

## Local Setup

### PostgreSQL

Create a database named `trackmyboardgames` in PostgreSQL (for example, using pgAdmin). The backend defaults to `localhost:5432`, database `trackmyboardgames`, and username `postgres`.

Set the database password in the environment of the terminal that will run Spring Boot. In PowerShell, for the current terminal session:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/trackmyboardgames"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-postgres-password>"
```

Replace the password placeholder with the password configured for your PostgreSQL user. Do not commit real credentials. The same settings can be configured as user environment variables if preferred.

### Auth0

The React app reads its public Auth0 settings from `frontend/.env.local`:

```dotenv
VITE_AUTH0_DOMAIN=<your-auth0-domain>
VITE_AUTH0_CLIENT_ID=<your-auth0-spa-client-id>
VITE_AUTH0_AUDIENCE=https://trackmyboardgames-api
VITE_API_BASE_URL=http://localhost:8080
```

This local file is ignored by Git. These values identify the Auth0 tenant/API and backend URL; never put an Auth0 client secret in frontend code or a `VITE_` variable. The React app uses the localhost API and audience values as defaults when the optional variables are unset.

For local development, configure the Auth0 SPA's allowed callback URLs and logout URLs as `http://localhost:5173/`, and its allowed web origins as `http://localhost:5173`. Vite is configured to use port `5173` strictly so it matches those URLs.

Create an API in the Auth0 Dashboard under **Applications → APIs** with identifier `https://trackmyboardgames-api` and signing algorithm `RS256`. The backend expects that identifier as the JWT audience and the Auth0 tenant as the issuer. Override the defaults using `AUTH0_AUDIENCE` and `AUTH0_ISSUER_URI` environment variables if they differ. Do not add an Auth0 client secret to the frontend.

The React app requests API tokens after sign-in. In the Auth0 API's **Permissions** tab, define `read:profile`, `read:games`, and `write:games`, then grant the SPA `read:profile` and the game permissions under **Application Access → User-Delegated Access**. Do not configure a client secret in the SPA or use Client Access (M2M) for browser calls.

Spring exposes public `GET /api/health`, protected `GET /api/me`, and owner-scoped game routes: `GET /api/games`, `GET /api/games/{id}`, `POST /api/games`, `PUT /api/games/{id}`, and `DELETE /api/games/{id}`. The local profile stores the Auth0 issuer and subject; it does not store passwords.

## Run Locally

For the optional original-sign-in-method policy and its required Auth0 Dashboard setup, see [auth0/README.md](auth0/README.md). Saving the Action scripts locally does not enable the policy.

Run the backend and frontend in separate terminals from the repository root.

Backend:

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

The API server listens at `http://localhost:8080`. The root URL may return Spring Boot's 404 page; the current API endpoints are `GET /api/health` and `GET /api/me`.

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Keep the terminal running while using the app. The current routes are `/login`, `/register`, and `/collection`. From the collection page, you can add a game and see your saved games.

## Mobile forms

Game and play forms automatically focus their first field only for fine-pointer, hover-capable devices. Touch users tap a field to begin editing instead of opening the keyboard as soon as a form appears. While a field is focused on a narrow touch screen, the dock uses a top-based small-viewport anchor so the keyboard can cover it rather than lifting it above the keyboard. Native keyboard/browser behavior must be checked on real iPhones.

## Deletion confirmation

Deleting a game or a play opens an in-app confirmation dialog. Type the exact game name or played-on date (`YYYY-MM-DD`) shown in bold to enable Delete. Game deletion also removes its associated play history and QR label.

Cancel is focused initially; Escape cancels, and Enter in the confirmation field does not delete. During a delete request, dismissal and duplicate submissions are disabled. A failed request leaves the dialog open with an error so you can retry or cancel.

## Checks

Backend tests:

```powershell
cd backend
./mvnw.cmd test
```

Frontend lint and production build:

```powershell
cd frontend
npm run lint
npm run build
```

## Database Migrations

Flyway runs migrations from `backend/src/main/resources/db/migration` at backend startup. Add schema changes as new versioned SQL files, for example `V9__create_outbox_events.sql`; do not edit migrations after they have been applied. Hibernate is configured to validate mappings, not to create or update the schema.

### EDA foundation - first slice

Migration V9 adds `outbox_events`, a shared holding table for multiple event types. Each row records an event's type/version, owner and source identifiers, JSON object payload, occurrence time, and publication/retry/claim metadata. Source identifiers are intentionally not cascading foreign keys so deletion events can outlive deleted records.

An empty `published_at` means not yet confirmed published to the message service; it does not mean "not processed by the receiver." Receiver duplicate protection uses the separate table described below.

### Play history events - third slice

Creating, editing, and deleting a play now save a corresponding version-1 outbox event (`PlayRecorded`, `PlayUpdated`, or `PlayDeleted`) in the same database transaction as the source change. Either both records commit or neither does, including when outbox persistence fails. The existing API responses are unchanged.

Each event's `owner_id` is the local user UUID, and `aggregate_id` is the play UUID. The shared `PlaySnapshot` JSON payload contains `playId`, `gameId`, `playedOn`, `durationMinutes`, and `playerCount`; optional values remain null. Update and delete events snapshot the play's values at the time of that operation. Private notes and Auth0 tokens are not included. For `PlayRecorded`, `occurred_at` is the play's creation time, not its played-on date.

Existing-data backfill is not wired yet.

### Collection events - fourth slice

Adding, editing, or deleting a game now saves a corresponding version-1 outbox event (`GameAdded`, `GameUpdated`, or `GameDeleted`) in the same transaction as the game change. The payload snapshots the game's ID, name, and edition, which lets a future statistics consumer respond to collection changes and game renames. Deleting a game still cascades to its play history, while its outbox event remains independent of the deleted row.

We are proceeding with hosted RabbitMQ (CloudAMQP) rather than SQS. This choice does not change the outbox or event creation. The RabbitMQ publisher adapter and outbox polling worker are implemented; the message consumer is still future work.

### RabbitMQ connection and routing - fifth slice

The backend now includes the RabbitMQ Java client and, when explicitly enabled, connects using `RABBITMQ_URI`. The URI is supplied through environment configuration and must not be committed. RabbitMQ stays disabled by default so local development and tests do not require a CloudAMQP account.

When enabled, the application declares a durable topic exchange (`trackmyboardgames.events`), a durable statistics queue bound with `#`, and a durable dead-letter exchange/queue route. The publisher sends persistent JSON messages with the outbox event ID, event type/version, owner ID, aggregate ID, and occurrence time. It waits for a publisher confirmation before returning successfully.

When RabbitMQ is enabled, the backend checks for unsent events about once a minute and sends them to RabbitMQ. If a send fails, the backend waits before trying again. If the backend stops while working on an event, it can safely be picked up later.

An event may occasionally be sent more than once, so the future statistics receiver will need to recognize repeats. Building that receiver and its failure handling is still future work. The polling interval defaults to one minute and can be changed with `RABBITMQ_POLL_INTERVAL_MS`.

For local development, `backend/config/application-local.properties` is a Git-ignored profile-specific settings file. Add the AMQP URI from a separate development CloudAMQP instance there, then start the backend with the `local` Spring profile active. Spring loads these settings in addition to `application.properties`; local-profile values override matching base values, while environment variables can override either file.

The file enables RabbitMQ but starts with an empty URI. Add the URI before starting with the `local` profile, or the backend will report a clear startup configuration error. Never put broker credentials in tracked files or use a production broker for local development.

### Statistics duplicate protection

Migration V10 adds `statistics_processed_events`, a receipt book for events the statistics receiver has handled. Each event ID can appear only once, even if two receivers try to record it at the same time. Receipts do not depend on game, play, user, or outbox rows, so deleting those records will not make a repeated event look new.

The repository requires an existing transaction when recording a receipt. The future receiver must record the receipt and update statistics together: a new receipt means continue processing; an existing receipt means skip the duplicate. If processing fails, both changes must roll back so a later delivery can try again. RabbitMQ must only be acknowledged after the transaction succeeds.

This slice provides and tests duplicate protection only. It does not start a RabbitMQ consumer, acknowledge queued messages, or calculate statistics yet. The publisher's one-event-per-check behavior is unchanged; batch delivery remains a separate improvement.

Run the focused persistence tests against a disposable PostgreSQL database/schema with the usual database environment settings:

```powershell
cd backend
.\mvnw.cmd clean test '-Dtest=OutboxEventPersistenceTests,GamePlayOutboxTests,BoardGameOutboxTests,RabbitMqPublisherTests,OutboxDispatcherTests,StatisticsProcessedEventTests,GamePlayApiTests,BoardGameEditionTests'
```

Flyway applies migrations before the tests and JPA validates the schema. Persistence/API test rows roll back; the outbox service tests exercise actual commits and clean up their own records. Migrations remain; do not point this command at production.

## Releases

There are no tagged releases yet. Record published versions here with their date and notable changes; use [BUILD_PLAN.md](BUILD_PLAN.md) for planned work that has not shipped.
