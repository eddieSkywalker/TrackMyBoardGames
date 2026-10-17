# Auth0 account policy

These standalone Actions enforce "use your original sign-in method" for the configured SPA. They do not link accounts, merge collections, delete users, or change passwords. Repository files are not automatically deployed.

## Policy and limitations

- Match emails ignoring capitalization and surrounding whitespace. Do not collapse Gmail dots or `+` aliases.
- Reject database/passwordless registration if the email already exists in the tenant.
- At login and refresh-token exchange, allow the earliest-created Auth0 user for that email. Ties use user ID ordering. A later separate identity is denied, including repeated attempts.
- The lookup covers the tenant, not just users who have used this SPA. Only enforce this policy in a dedicated app tenant, or explicitly review the implications for other applications sharing users.
- Google/social identities can be created before Post Login runs. Denial prevents token issuance for this app, not creation of the Auth0 identity itself.
- Exact-email lookups are immediately consistent but case-sensitive. The additional case-insensitive user search is eventually consistent. Recent capitalization-only duplicates and simultaneous registrations can temporarily escape detection. This is not a database uniqueness constraint.
- Existing access tokens remain valid until expiry. These Actions do not revoke sessions or tokens already issued.
- Original means earliest-created, not verified ownership. Review existing duplicates and abandoned/unverified accounts before enabling. Someone registering another person's email first can block that person's later social login; resolution requires an ownership-verified support process, not automatic linking.
- Lookup errors, missing email/configuration, invalid dates, or potentially truncated results deny the attempt with a temporary error. Management API outages/rate limits therefore affect sign-in and refresh. Each check obtains a token and performs two or three lookups; monitor API and M2M quotas for your Auth0 plan.
- Admin-created/imported accounts and other provisioning paths are not guaranteed to run the registration Action. Post Login still applies when those users authenticate to the configured app.

The common lookup code is repeated deliberately: each Action must be independently pasteable into Auth0 without importing sibling repository files. Keep lookup changes and tests in sync.

## Dashboard setup

First deploy the React error-handling changes so a denied callback can display its message and offer a fresh login instead of redirecting into a protected screen.

### 1. Review users and permissions

1. Confirm the original account for each existing duplicate email under **User Management > Users**. Confirm its Auth0 subject owns the intended app data. Do not delete or change identities just to satisfy the ordering rule.
2. Under **Applications > Applications**, create a dedicated **Machine to Machine** application, such as `Account Policy Actions`.
3. Authorize it for the **Auth0 Management API**, granting only **read:users**. No user-write/delete permissions are needed.
4. Note its client ID and secret privately. This is separate from the React SPA and the application's own API audience.
5. Confirm your plan permits the necessary Actions, Management API access, and client-credentials quota.

### 2. Create and configure both Actions

Under **Actions > Library**, create custom Actions using a currently supported Node.js runtime with built-in `fetch` and `AbortSignal.timeout` (Node.js 22 or newer).

| Action | Trigger | Source |
| --- | --- | --- |
| Prevent duplicate registration | Pre User Registration | [prevent-duplicate-registration.js](actions/prevent-duplicate-registration.js) |
| Restrict duplicate login | Post Login (Login flow) | [restrict-duplicate-login.js](actions/restrict-duplicate-login.js) |

Paste the entire corresponding source file into each Action. No npm dependencies are required.

Add these four secrets to **each** Action:

| Secret | Value |
| --- | --- |
| `APP_CLIENT_ID` | The existing React SPA's client ID, not the M2M client ID. |
| `AUTH0_DOMAIN` | Canonical Auth0 tenant hostname only, e.g. `your-tenant.us.auth0.com`; no scheme or trailing slash. Use the Management API's canonical tenant domain/audience, not a custom login domain. |
| `MANAGEMENT_CLIENT_ID` | The new M2M application's client ID. |
| `MANAGEMENT_CLIENT_SECRET` | The new M2M application's secret. |

Never put the M2M secret in `VITE_` variables, Git, logs, or the browser. Missing `APP_CLIENT_ID` denies attempts rather than silently disabling the policy; other client IDs are skipped when it is configured.

### 3. Test, deploy, and attach

1. Use the Action editor's test facilities with realistic user/client events where available. A Post Login test needs `user_id`, `email`, and `created_at`. Fake users alone do not exercise the real tenant lookup; use disposable accounts for end-to-end checks.
2. **Deploy** each Action.
3. Open **Actions > Flows/Triggers > Pre User Registration**, add the registration Action between Start and Complete, and **Apply**.
4. Open **Actions > Flows/Triggers > Login**, add the login Action, and **Apply**. Dashboard labels may vary.
5. Keep an existing administrator session available and a rollback path before testing fresh app logins.

No Spring deployment or database migration is required.

## Verification

Run local mocked Action tests from the repository root:

```powershell
node --test auth0\tests\account-policy.test.cjs
```

These tests do not call Auth0 or prove the deployed configuration. Then verify in the real tenant with disposable accounts:

- Password first, Google second: later Google identity denied; original password login still works.
- Google first, password signup second: signup denied; Google still works.
- Repeat with differently capitalized emails after search indexing has caught up.
- Repeat a rejected login: the original identity must remain allowed.
- Sign in with a new, unused email: allowed.
- Verify the readable rejection and "Return to sign in" in React, including Safari. Retry must present login rather than silently reusing the rejected session.
- Verify normal logout, reload, refresh-token rotation, and access to the original collection.
- Check Auth0 Action logs on a failed lookup; users should see a temporary error, not bypass the check.
- A rejected fresh login should not create a new app user through that attempt. Old valid access tokens are an exception and are not revoked here.

Auth0 can display registration errors directly in Universal Login. Post Login denials normally return through the SPA callback. Exact error presentation must be verified with the tenant's login experience.

## Rollback

Remove the Actions from both flows and **Apply** to disable enforcement. Editing or saving source without deploying does not update a deployed Action. Keep the scripts and deployed versions aligned. Remove M2M authorization/rotate credentials if retiring the policy, after detaching Actions.

## References

- [Pre User Registration trigger](https://auth0.com/docs/customize/actions/explore-triggers/signup-and-login-triggers/pre-user-registration-trigger)
- [Post Login trigger](https://auth0.com/docs/customize/actions/explore-triggers/signup-and-login-triggers/login-trigger)
- [User lookup by email](https://auth0.com/docs/manage-users/user-search/retrieve-users-with-get-users-by-email-endpoint)
- [Case-insensitive normalized-field search](https://auth0.com/docs/manage-users/user-search/user-search-query-syntax)
