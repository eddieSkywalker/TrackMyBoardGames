import { apiBaseUrl, requireToken, responseError } from './apiHelpers'

// "export" lets other files import a type or function from this file.
// "export type" shares a description of the data, not a function that runs.
// GameSummary describes the game shown as most played or most recently played.
export type GameSummary = {
    gameId: string
    name: string
    edition: string
    playCount: number
    lastPlayedOn: string
}

// Missing results are shown as blank values, while a count of zero still means
// there really are no games or plays.
export type Statistics = {
    month: string
    timezone: string
    collectionSize: number | null
    mostPlayed: GameSummary | null
    playsThisMonth: number | null
    mostRecentlyPlayed: GameSummary | null
    refreshedAt: string | null
}

// Ask for the signed-in user's saved statistics, without recalculating their collection.
// accessToken is the sign-in token sent to the backend. If it is undefined (missing),
// requireToken stops the request with a sign-in error rather than sending it without a token.
// signal lets the component cancel its request when the user leaves the page.
export async function fetchStatistics(accessToken: string | undefined, signal: AbortSignal): Promise<Statistics>
{
    const token = requireToken(accessToken)
    const response = await fetch(`${apiBaseUrl}/api/statistics`, {
        headers: { Authorization: `Bearer ${token}` },
        cache: 'no-store',
        signal,
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    // Read the backend's answer.
    return await response.json() as Statistics
}
