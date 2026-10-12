// Settings and helpers shared by frontend requests to the Spring backend.
export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
export const apiAudience = import.meta.env.VITE_AUTH0_AUDIENCE || 'https://trackmyboardgames-api'

// Turn a failed response into a readable message, preferring the one Spring sent.
export async function responseError(response: Response)
{
    const body = await response.json().catch(() => null) as { detail?: string; message?: string } | null
    return body?.detail ?? body?.message ?? `The backend returned HTTP ${response.status}.`
}

// Auth0 can hand back no token; stop with a clear message instead of sending a broken request.
export function requireToken(accessToken: string | undefined): string
{
    if (!accessToken)
    {
        throw new Error('You are not signed in.')
    }

    return accessToken
}
