// Standalone Auth0 Action: deploy on the Pre User Registration trigger.
const duplicateMessage = 'An account with this email already exists. Please sign in using your original sign-in method.'
const unavailableMessage = 'We could not check your sign-in method. Please try again later.'

async function fetchJson(url, options = {})
{
    const response = await fetch(url, { ...options, signal: AbortSignal.timeout(3000) })

    if (!response.ok)
    {
        throw new Error(`Auth0 lookup returned HTTP ${response.status}`)
    }

    return response.json()
}

// Exact lookups cover recent writes; search also finds emails stored with different capitalization.
async function findMatchingUsers(event)
{
    const { AUTH0_DOMAIN, MANAGEMENT_CLIENT_ID, MANAGEMENT_CLIENT_SECRET } = event.secrets

    if (!/^[a-z0-9.-]+$/i.test(AUTH0_DOMAIN ?? '') || !MANAGEMENT_CLIENT_ID || !MANAGEMENT_CLIENT_SECRET)
    {
        throw new Error('Missing or invalid Action configuration')
    }

    // Obtain a server-side token with permission to read Auth0 users.
    const base = `https://${AUTH0_DOMAIN}`
    const token = await fetchJson(`${base}/oauth/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            grant_type: 'client_credentials',
            client_id: MANAGEMENT_CLIENT_ID,
            client_secret: MANAGEMENT_CLIENT_SECRET,
            audience: `${base}/api/v2/`,
        }),
    })

    if (typeof token.access_token !== 'string' || !token.access_token)
    {
        throw new Error('Missing Management API token')
    }

    const headers = { Authorization: `Bearer ${token.access_token}` }
    const email = event.user.email

    if (typeof email !== 'string' || !email.trim())
    {
        throw new Error('Email is required by this policy')
    }

    // Escape the normalized email for the search query, not just the URL.
    const normalized = email.trim().toLowerCase()
    const escaped = normalized.replace(/[\\"]/g, '\\$&')

    const queries = [
        ...[...new Set([email, normalized])].map(value => `${base}/api/v2/users-by-email?email=${encodeURIComponent(value)}`),
        `${base}/api/v2/users?${new URLSearchParams({
            q: `email:"${escaped}"`,
            search_engine: 'v3',
            per_page: '100',
        })}`,
    ]

    const results = await Promise.all(queries.map(url => fetchJson(url, { headers })))

    // Do not make an allow/deny decision from malformed or potentially truncated results.
    if (results.some(users => !Array.isArray(users) || users.length >= 100))
    {
        throw new Error('Incomplete or invalid user lookup')
    }

    // The lookups overlap; keep only exact normalized matches, once per identity.
    const unique = new Map()

    for (const user of results.flat())
    {
        if (typeof user?.user_id !== 'string' || typeof user.email !== 'string')
        {
            throw new Error('Invalid user profile in lookup')
        }

        if (user.email.trim().toLowerCase() === normalized)
        {
            unique.set(user.user_id, user)
        }
    }

    return [...unique.values()]
}

exports.onExecutePreUserRegistration = async (event, api) =>
{
    // Require a configured target SPA, then leave other applications unchanged.
    if (!event.secrets.APP_CLIENT_ID)
    {
        console.error('Duplicate registration check: missing APP_CLIENT_ID')
        api.access.deny('account_check_unavailable', unavailableMessage)
        return
    }

    if (event.client.client_id !== event.secrets.APP_CLIENT_ID)
    {
        return
    }

    try
    {
        // This trigger can reject database/passwordless signup before a user is created.
        if ((await findMatchingUsers(event)).length > 0)
        {
            api.access.deny('email_already_registered', duplicateMessage)
        }
    }
    catch (error)
    {
        // Fail closed on lookup errors, with diagnostic logs and a user-safe message.
        console.error('Duplicate registration check failed:', error instanceof Error ? error.message : 'Unknown error')
        api.access.deny('account_check_unavailable', unavailableMessage)
    }
}
