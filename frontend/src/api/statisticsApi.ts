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

export type MonthlyHeatmap = {
    month: string
    timezone: string
    days: { date: string; playCount: number }[] | null
    refreshedAt: string | null
}

/** Reads saved daily counts for the current month; omitting the collection means all collections. */
export async function fetchMonthlyHeatmap(accessToken: string | undefined, signal: AbortSignal,
    collectionId?: string): Promise<MonthlyHeatmap>
{
    const token = requireToken(accessToken)
    const collectionQuery = collectionId ? `?collectionId=${encodeURIComponent(collectionId)}` : ''
    const response = await fetch(`${apiBaseUrl}/api/statistics/heatmap${collectionQuery}`, {
        headers: { Authorization: `Bearer ${token}` },
        signal,
        cache: 'no-store',
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    return await response.json() as MonthlyHeatmap
}

// Ask for the signed-in user's saved statistics, without recalculating their collection.
// accessToken is the sign-in token sent to the backend. If it is undefined (missing),
// requireToken stops the request with a sign-in error rather than sending it without a token.
// signal lets the component cancel its request when the user leaves the page.
export async function fetchStatistics(accessToken: string | undefined, signal: AbortSignal, collectionId?: string): Promise<Statistics>
{
    const token = requireToken(accessToken)
    const collectionQuery = collectionId ? `?collectionId=${encodeURIComponent(collectionId)}` : ''
    const response = await fetch(`${apiBaseUrl}/api/statistics${collectionQuery}`, {
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

/** Loads only the account's highlighted game covers, rather than downloading every collection's games. */
export async function fetchStatisticsCovers(accessToken: string | undefined, signal: AbortSignal,
    statistics: Statistics): Promise<Record<string, string | null>>
{
    const token = requireToken(accessToken)
    const gameIds = [...new Set([statistics.mostPlayed?.gameId, statistics.mostRecentlyPlayed?.gameId]
        .filter((gameId): gameId is string => Boolean(gameId)))]
    const covers = await Promise.all(gameIds.map(async gameId =>
    {
        const response = await fetch(`${apiBaseUrl}/api/games/${encodeURIComponent(gameId)}`, {
            headers: { Authorization: `Bearer ${token}` },
            signal,
        })

        // A saved highlight can briefly name a deleted game until its background refresh finishes.
        if (response.status === 404)
        {
            return [gameId, null] as const
        }

        if (!response.ok)
        {
            throw new Error(await responseError(response))
        }

        const game = await response.json() as { coverImage: string | null }
        return [gameId, game.coverImage] as const
    }))

    return Object.fromEntries(covers)
}
