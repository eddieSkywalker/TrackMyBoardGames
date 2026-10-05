# TrackMyBoardGames

A web app for managing a personal board-game collection, recording plays, and reviewing play history. The longer-term plan includes printable QR labels and camera scanning so a play can be logged quickly.

**Status:** Early development. The React app currently has Auth0-hosted sign-in and registration flows plus a signed-in collection placeholder. The Spring Boot backend connects to PostgreSQL and runs Flyway, but game APIs, Spring-side Auth0 token validation, and application user records are not implemented yet.

See [BUILD_PLAN.md](BUILD_PLAN.md) for planned milestones and release goals.

## Technology

- **Frontend:** React 19, TypeScript, Vite 8, React Router, Auth0 React SDK, ESLint
- **Backend:** Java 25, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Hibernate, Spring Validation, Flyway
- **Database:** PostgreSQL
- **Authentication:** Auth0-hosted login and registration; securing backend API requests is still to be implemented

## Repository Layout

```text
backend/       Spring Boot API and Flyway migrations
frontend/      React single-page application
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
```

This local file is ignored by Git. These values identify the Auth0 tenant and SPA; never put an Auth0 client secret in frontend code or a `VITE_` variable.

For local development, configure the Auth0 SPA's allowed callback URLs and logout URLs as `http://localhost:5173/`, and its allowed web origins as `http://localhost:5173`. Vite is configured to use port `5173` strictly so it matches those URLs.

An Auth0 API resource server and Spring JWT validation are not configured yet. Auth0 sign-in currently does not secure backend endpoints or create a PostgreSQL user record.

## Run Locally

Run the backend and frontend in separate terminals from the repository root.

Backend:

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

The API server listens at `http://localhost:8080`. There are not yet any application API endpoints; the root URL may return Spring Boot's 404 page.

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Keep the terminal running while using the app. The current routes are `/login`, `/register`, and `/collection`.

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

Flyway runs migrations from `backend/src/main/resources/db/migration` at backend startup. Add schema changes as new versioned SQL files, for example `V1__create_users.sql`; do not edit migrations after they have been applied. Hibernate is configured to validate mappings, not to create or update the schema. The database currently has Flyway's schema-history table, but no application tables.

## Releases

There are no tagged releases yet. Record published versions here with their date and notable changes; use [BUILD_PLAN.md](BUILD_PLAN.md) for planned work that has not shipped.
