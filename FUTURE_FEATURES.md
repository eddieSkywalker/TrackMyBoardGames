# Future Features

Ideas and deferred features to revisit. These are not commitments for the current milestone.

## Linked sign-in methods

**Status:** Deferred. Revisit when ready to add user-initiated account linking.

### Important distinction: Google login doesn't create an app password

If someone initially registers through Google:

- Google authenticates them; the app never receives their Google password.
- They cannot immediately use that password in the app's email/password login.
- To use both methods, they need to create a separate email/password credential and securely link the two identities.
- Those passwords do not need to match, and users should not reuse their Google password.

Once linked, either method can access the same app account.

### My recommendation: explicit account linking

Start with a **Linked sign-in methods** section in Profile:

1. A user signs into their existing account.
2. They choose **Link Google** or **Link email/password**.
3. They authenticate again to prove control of the current account and authenticate the additional identity.
4. Auth0 links them, keeping the original account as the primary identity.
5. Future logins through either method lead to the same collection.

Auth0 recommends authenticating both accounts before linking. A matching, even verified, email should not be the sole proof of ownership. See [Auth0 account linking](https://auth0.com/docs/manage-users/user-accounts/user-account-linking).

### How much work is involved?

This is a moderate feature, not just an Auth0 checkbox, but it does not require replacing the authentication system.

| Area | Work needed |
| --- | --- |
| Profile UI | Show linked methods and offer a linking action. |
| Auth0 | Secure linking flow and appropriate Management API access. |
| Backend | Validate linking requests and keep privileged credentials server-side. |
| App data | Preserve the existing app-user identity and collection ownership. |
| Sessions | Refresh authentication after linking and account for previously issued tokens. |

The backend identifies users by Auth0 issuer and subject, not email, in [UserProfileService.java](backend/src/main/java/com/SwampDragonInn/trackmyboardgames/service/UserProfileService.java). Keeping the existing collection owner as the primary Auth0 identity is therefore important.

Linking Auth0 identities does not automatically merge two existing Neon users' collections. For the first version, do not merge separate collections automatically; that needs its own deliberate rules.

### Compared with rejecting duplicate emails

Rejecting a second sign-in method is also a valid product choice, but it has drawbacks:

- Users must remember their original method.
- Enforcing this across Google and email/password still requires Auth0 customization.
- Explicit "this email already exists" messages can disclose whether someone has an account.
- It prevents users from accessing one collection through multiple sign-in methods.

Before implementing linking, verify that the Auth0 plan supports the intended flow. Availability depends on the plan and implementation.
