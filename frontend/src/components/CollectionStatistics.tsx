import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { apiAudience } from '../api/apiHelpers'
import { fetchStatistics, type GameSummary, type Statistics } from '../api/statisticsApi'
import type { BoardGame } from '../types/boardGame'

// The request can be waiting, finished, or failed. Each option carries only what it needs:
// finished requests have statistics; failed requests have a message to show the user.
type LoadState =
    | { status: 'loading' }
    | { status: 'loaded'; statistics: Statistics }
    | { status: 'error'; message: string }

function gameName(game: GameSummary | null | undefined): string
{
    if (!game) return ''
    return game.edition ? `${game.name} (${game.edition})` : game.name
}

// Shows saved statistics when the collection page opens. The backend calculates changes separately.
function CollectionStatistics({ games }: { games: BoardGame[] })
{
    const { getAccessTokenSilently } = useAuth0()
    // useState gives us a pair: the value React remembers and a function for changing it.
    // Start with "loading"; setState tells React to update the display when the request finishes.
    // <LoadState> limits the value to the three options described above.
    const [state, setState] = useState<LoadState>({ status: 'loading' })

    // Read statistics when the component opens. Refreshing the browser or returning to this page
    // opens it again and reads the latest saved results.
    useEffect(() =>
    {
        let isCurrent = true
        const controller = new AbortController()

        async function loadStatistics()
        {
            try
            {
                // Get proof that the user is signed in before asking for their saved statistics.
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                // The user may have left while we were waiting for their token.
                if (!isCurrent) return
                const statistics = await fetchStatistics(accessToken, controller.signal)
                if (isCurrent) setState({ status: 'loaded', statistics })
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setState({
                        status: 'error',
                        message: error instanceof Error ? error.message : 'Could not load your statistics.',
                    })
                }
            }
        }

        // Start the request without making React wait for it. Errors are handled inside the function.
        void loadStatistics()
        return () =>
        {
            // Before leaving or starting a replacement request, stop this one from changing the display.
            isCurrent = false
            controller.abort()
        }
    }, [getAccessTokenSilently])

    // Show the values the backend supplied. Missing counts stay blank; actual zeros display as zero.
    const statistics = state.status === 'loaded' ? state.statistics : null
    // Reuse covers already loaded for the collection, matching each selected game by its ID.
    const mostPlayedCover = games.find(game => game.id === statistics?.mostPlayed?.gameId)?.coverImage
    const mostRecentlyPlayedCover = games.find(game => game.id === statistics?.mostRecentlyPlayed?.gameId)?.coverImage

    const cards = [
        {
            title: 'Collection',
            value: statistics?.collectionSize?.toLocaleString() ?? '',
            detail: '',
            isCount: true,
            coverImage: null,
        },
        {
            title: 'Most Popular!',
            value: gameName(statistics?.mostPlayed),
            detail: '',
            isCount: false,
            coverImage: mostPlayedCover,
        },
        {
            title: 'Plays This Month!',
            value: statistics?.playsThisMonth?.toLocaleString() ?? '',
            detail: '',
            isCount: true,
            coverImage: null,
        },
        {
            title: 'Latest To The Table!',
            value: gameName(statistics?.mostRecentlyPlayed),
            detail: '',
            isCount: false,
            coverImage: mostRecentlyPlayedCover,
        },
    ]

    return (
        <section className="mt-6" aria-labelledby="collection-statistics-title" aria-busy={state.status === 'loading'}>
            <h2 id="collection-statistics-title" className="mb-3 text-base font-bold">Statistics</h2>

            {state.status === 'loading' && <p className="mb-3 text-sm text-base-content/70" role="status">Loading your statistics...</p>}
            {state.status === 'error' && (
                <p className="alert alert-error mb-3 text-sm" role="alert">
                    Could not load your statistics. {state.message} Refresh the page to try again.
                </p>
            )}
            <div className="mx-auto grid min-w-0 max-w-3xl auto-rows-fr grid-cols-2 gap-3 lg:grid-cols-4">
                {cards.map(card => (
                    <div key={card.title} className="flex min-w-0 cursor-default flex-col rounded-box border border-base-300 bg-base-100 p-5 text-center shadow-sm">
                        <h3 className="text-sm font-semibold text-base-content/70">{card.title}</h3>
                        <div className={card.isCount
                            ? 'mt-2 flex min-w-0 flex-1 items-center justify-center'
                            : 'mt-2 flex min-w-0 flex-col items-center gap-2'}>
                            <p className={card.isCount
                                ? 'min-w-0 break-words text-[5.25rem] leading-none font-bold tabular-nums tracking-tight'
                                : 'min-w-0 break-words text-xl font-bold'}>
                                {card.value}
                            </p>
                            {card.coverImage && (
                                <img className="h-auto max-h-24 w-auto max-w-full rounded-lg object-contain" src={card.coverImage} alt={`${card.value} cover`} />
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </section>
    )
}

export default CollectionStatistics
