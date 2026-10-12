# How an Authenticated Request Is Handled

This document explains how Auth0, React and Spring Boot work together to authenticate a request. Auth0 issues a signed token (a JWT), React stores it and sends it with each API call, and Spring only verifies it. Spring never contacts Auth0 for each request.

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
    participant Controller as Controller
    participant Service as Service
    participant DB as PostgreSQL

    User->>React: Open the app and log in
    React->>Auth0: Redirect to the Auth0 login page
    Auth0-->>React: Return to the app after a successful login
    React->>Auth0: Request an access token for the API audience and scopes (for example read:games)
    Auth0-->>React: Return a signed JWT
    Note over React: The Auth0 SDK keeps the token and gets a new one when it expires

    User->>React: Use a feature, such as viewing the collection
    React->>Security: GET /api/games with the header Authorization: Bearer JWT
    Note over Security: Signature is checked with Auth0's public keys,<br/>downloaded once from the issuer URL and cached
    Security->>Security: Check signature, expiry, issuer and audience
    alt ❌ Token is missing or invalid
        Security-->>React: 401 Unauthorized
    else ❌ Token is valid but lacks the scope
        Security-->>React: 403 Forbidden
    else ✅ Token is valid and has SCOPE_read:games
        Security->>Controller: Call the method with the decoded Jwt
        Controller->>Service: Pass issuer and subject from the Jwt
        Service->>DB: Find or create the app user, then query only that owner's rows
        DB-->>Service: Return the owner's data
        Service-->>Controller: Return response data
        Controller-->>React: 200 OK with JSON
    end
    React-->>User: Show the result
```

## What Is Inside the JWT

| Claim | Meaning | Example |
|---|---|---|
| `sub` | The user's id | `auth0\|abc123` |
| `iss` | Who issued the token (the Auth0 tenant) | `https://your-tenant.us.auth0.com/` |
| `aud` | Which API the token is for | the API audience |
| `exp` | When the token expires | a timestamp |
| `scope` | The permissions granted | `read:games write:games` |

The token is signed, so React cannot change the `sub` or the scopes without breaking the signature.

## How This Shows Up in the Code

- **Scopes:** Auth0 decides which scopes go into the token. Spring enforces them. In `SecurityConfig`, `hasAuthority("SCOPE_read:games")` means "reject the request unless the token lists `read:games`". Spring adds the `SCOPE_` prefix itself.
- **`@AuthenticationPrincipal Jwt jwt`:** After Spring validates the token, it passes the decoded token to the controller method. The code reads `jwt.getSubject()` and `jwt.getIssuer()` to find or create the matching `AppUser`.
- **Owner filtering:** Each query is filtered by that owner's id, so another user's records look like 404 (not found).
- **Tests:** Tests use `jwt()` from Spring Security Test to fake an already validated token, so no real Auth0 call happens.
- **Frontend:** `apiHelpers.ts` holds the shared API audience and base URL, and each request attaches the token as a `Bearer` header.

## Security Notes

- The security lives in the backend checks: signature, scopes and the owner filter. Hiding a button in React is not security.
- Access tokens expire (often after 24 hours). The Auth0 SDK requests a new one when needed.
- See [board-game-create-flow.md](./board-game-create-flow.md) for a full example that continues past authentication into the service and database.
