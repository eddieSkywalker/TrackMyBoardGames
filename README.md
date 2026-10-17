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

Flyway runs migrations from `backend/src/main/resources/db/migration` at backend startup. Add schema changes as new versioned SQL files, for example `V1__create_users.sql`; do not edit migrations after they have been applied. Hibernate is configured to validate mappings, not to create or update the schema. The database currently has Flyway's schema-history table, but no application tables.

## Releases

There are no tagged releases yet. Record published versions here with their date and notable changes; use [BUILD_PLAN.md](BUILD_PLAN.md) for planned work that has not shipped.
