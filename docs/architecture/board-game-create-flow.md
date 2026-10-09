# Board Game Creation Flow

This diagram describes the backend flow for creating a board game. The React collection page does not yet have an Add Game form wired to this endpoint; that is frontend work still to come.

```mermaid
%%{init: {
    "theme": "base",
    "themeVariables": {
        "background": "#1b1b1b",
        "primaryColor": "#242424",
        "primaryTextColor": "#dedede",
        "primaryBorderColor": "#087eae",
        "lineColor": "#1686b3",
        "actorBkg": "#242424",
        "actorBorder": "#087eae",
        "actorTextColor": "#dedede",
        "actorLineColor": "#1686b3",
        "signalColor": "#c9c9c9",
        "signalTextColor": "#dedede",
        "labelBoxBkgColor": "#242424",
        "labelBoxBorderColor": "#087eae",
        "labelTextColor": "#dedede",
        "loopTextColor": "#dedede",
        "activationBkgColor": "#303030",
        "activationBorderColor": "#1686b3",
        "noteBkgColor": "#292929",
        "noteBorderColor": "#087eae",
        "noteTextColor": "#dedede"
    }
}}%%
sequenceDiagram
    actor User
    participant React
    participant Auth0
    participant Security as Spring Security
    participant Controller as BoardGameController
    participant Service as BoardGameService
    participant ProfileService as UserProfileService
    participant GameRepo as BoardGameRepository
    participant UserRepo as AppUserRepository
    participant DB as PostgreSQL

    User->>React: Submit new game details
    React->>Auth0: Request API access token with write:games
    Auth0-->>React: Return access token
    React->>Security: POST /api/games with JSON and Bearer token
    Security->>Security: Validate token issuer, signature, audience, and write:games scope
    Security->>Controller: Allow request and provide validated JWT
    Controller->>Service: Pass issuer, subject, and validated game request
    Service->>Service: Validate player count range
    Service->>ProfileService: Find or create local profile
    ProfileService->>UserRepo: Look up profile by Auth0 issuer and subject
    alt First request for this Auth0 identity
        UserRepo->>DB: Insert app_users row
        DB-->>UserRepo: Return saved profile
    else Profile already exists
        UserRepo->>DB: Select existing app_users row
        DB-->>UserRepo: Return existing profile
    end
    ProfileService-->>Service: Return local profile
    Service->>GameRepo: Save game with profile as owner
    GameRepo->>DB: Insert board_games row with owner_id foreign key
    DB-->>GameRepo: Confirm insert
    GameRepo-->>Service: Return saved game
    Service-->>Controller: Return game response data
    Controller-->>React: HTTP 201 Created with game JSON and Location header
    React-->>User: Show the new game in the collection
```

## High-Level View: Who Calls What

Solid arrows are calls. Dotted arrows are what comes back. Compare this with the read flow in [request-layers-flow.md](./request-layers-flow.md): here data also travels in, as a request, and is turned into an entity before it is saved.

```mermaid
flowchart LR
    React["🌐 React app"]
    Request["📥 Request<br/>BoardGameRequest"]
    Controller["🚪 Controller<br/>BoardGameController"]
    Service["🧠 Service<br/>BoardGameService"]
    UserRepo["📚 Repository<br/>AppUserRepository"]
    GameRepo["📚 Repository<br/>BoardGameRepository"]
    DB[("🗄️ PostgreSQL")]
    Entity["📦 Entity<br/>BoardGame"]
    Response["📄 Response<br/>BoardGameResponse"]

    React -->|"1. POST /api/games + JSON"| Controller
    Controller -.->|"2. JSON is read into"| Request
    Controller -->|"3. create(request)"| Service
    Service -->|"4. find or create the owner"| UserRepo
    UserRepo -->|"SQL"| DB
    Service -->|"5. build a new"| Entity
    Service -->|"6. save(entity)"| GameRepo
    GameRepo -->|"INSERT"| DB
    GameRepo -.->|"7. saved entity"| Service
    Service -->|"8. copy fields into"| Response
    Response -.->|"9. returned up the chain"| Controller
    Controller -.->|"10. 201 Created + JSON"| React

    NoteRequest["🔒 The Request is an allow-list.<br/>It has only name, players, notes, cover.<br/>Extra JSON such as id or owner has nowhere to go and is dropped."]
    NoteService["🔒 The Service decides what is saved.<br/>Owner comes from the JWT, id from the database,<br/>timestamps from the entity."]
    NoteResponse["🔒 The Response chooses what goes out.<br/>It hides the owner and adds playCount and the cover data URL."]
    Request --- NoteRequest
    Service --- NoteService
    Response --- NoteResponse
```

## Why Request and Response Classes Exist

- **Request:** React could send any JSON, not only what the form shows. If the server bound it straight to the `BoardGame` entity, a client could set fields such as `owner` or `id` (called overposting or mass assignment). The request class lists only the fields a client may set, so everything else is ignored.
- **Service:** It builds the entity itself from the allowed fields. Protected values come from trusted places: the owner from the validated JWT, the id from the database, and timestamps from the entity.
- **Response:** The entity is the database model. The response is the public JSON contract. It leaves out private data such as the owner, and adds data the table doesn't hold, such as `playCount`.
- **Validation:** The request's annotations (`@NotBlank`, `@Size`, `@Min`) reject bad input with a 400 before the controller code runs.

## Return Path

The response does **not** return immediately after the controller calls the service. The controller waits while the service validates the request, finds or creates the local profile, and saves the game. Once the service returns, the controller sends the created-game JSON with HTTP `201 Created`. If any step fails, the request returns an error instead of a successful game response.

The service method is transactional: its database operations commit together if successful, or roll back together if a runtime error occurs.

## Identity and Ownership

Auth0 authenticates the person and supplies a signed token. Spring Security validates that token before the controller runs. The service uses the token issuer and subject to find the corresponding `app_users` row; it does not store the user's Auth0 password. The new game's `owner_id` references that local profile's primary key, and every later game query filters by that owner.
