// Standalone Auth0 Action: deploy on the Post Login trigger.
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

// Kept self-contained because Auth0 Actions cannot import sibling repository files.
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

    // Exact lookups cover recent writes; search also finds different capitalization.
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

exports.onExecutePostLogin = async (event, api) =>
{
    // Require a configured target SPA, then leave other applications unchanged.
    if (!event.secrets.APP_CLIENT_ID)
    {
        console.error('Duplicate login check: missing APP_CLIENT_ID')
        api.access.deny(unavailableMessage)
        return
    }

    if (event.client.client_id !== event.secrets.APP_CLIENT_ID)
    {
        return
    }

    try
    {
        const users = await findMatchingUsers(event)

        // The authenticated user may not have reached the search index yet.
        const current = event.user

        if (typeof current.user_id !== 'string' || !current.user_id)
        {
            throw new Error('Missing current user ID')
        }

        const candidates = users.filter(user => user.user_id !== current.user_id)
        candidates.push(current)

        if (candidates.some(user => !Number.isFinite(Date.parse(user.created_at))))
        {
            throw new Error('Missing account creation date')
        }

        // Preserve the oldest identity; user ID ordering breaks creation-time ties.
        candidates.sort((left, right) =>
            Date.parse(left.created_at) - Date.parse(right.created_at)
            || left.user_id.localeCompare(right.user_id))

        if (candidates[0].user_id !== current.user_id)
        {
            api.access.deny(duplicateMessage)
        }
    }
    catch (error)
    {
        // Fail closed on lookup errors, with diagnostic logs and a user-safe message.
        console.error('Duplicate login check failed:', error instanceof Error ? error.message : 'Unknown error')
        api.access.deny(unavailableMessage)
    }
}
