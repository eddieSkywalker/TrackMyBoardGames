import { apiBaseUrl, requireToken, responseError } from './apiHelpers'

export type GameCollectionSummary = {
    id: string
    name: string
    defaultCollection: boolean
}

/** Reads the owned collections; the saved default goes first when a browsing session starts. */
export async function fetchCollections(accessToken: string | undefined, signal: AbortSignal): Promise<GameCollectionSummary[]>
{
    const response = await fetch(`${apiBaseUrl}/api/collections`, {
        headers: { Authorization: `Bearer ${requireToken(accessToken)}` },
        signal,
        cache: 'no-store',
    })

    if (!response.ok) throw new Error(await responseError(response))
    const collections = await response.json() as GameCollectionSummary[]

    if (collections.filter(collection => collection.defaultCollection).length !== 1)
    {
        throw new Error('Your collections must have one saved default. Please reload the page.')
    }
    return [
        ...collections.filter(collection => collection.defaultCollection),
        ...collections.filter(collection => !collection.defaultCollection),
    ]
}

/** Saves the preference only; choosing another collection to view does not call this. */
export async function saveDefaultCollection(accessToken: string | undefined, collectionId: string): Promise<GameCollectionSummary>
{
    const response = await fetch(`${apiBaseUrl}/api/collections/${encodeURIComponent(collectionId)}/default`, {
        method: 'PUT',
        headers: { Authorization: `Bearer ${requireToken(accessToken)}` },
    })
    
    if (!response.ok) throw new Error(await responseError(response))
    return await response.json() as GameCollectionSummary
}
