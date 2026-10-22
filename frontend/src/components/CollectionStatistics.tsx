import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { apiAudience } from '../api/apiHelpers'
import { fetchStatistics, type GameSummary, type Statistics } from '../api/statisticsApi'

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
function CollectionStatistics()
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

    const cards = [
        {
            title: 'Collection size',
            value: statistics?.collectionSize?.toLocaleString() ?? '',
            detail: 'Games in your collection',
        },
        {
            title: 'Most played game',
            value: gameName(statistics?.mostPlayed),
            detail: statistics?.mostPlayed
                ? `${statistics.mostPlayed.playCount.toLocaleString()} recorded plays`
                : 'Across your recorded play history',
        },
        {
            title: 'Plays this month',
            value: statistics?.playsThisMonth?.toLocaleString() ?? '',
            detail: statistics ? `${statistics.month} (${statistics.timezone})` : 'Current calendar month',
        },
        {
            title: 'Most recently played',
            value: gameName(statistics?.mostRecentlyPlayed),
            detail: statistics?.mostRecentlyPlayed
                ? `Played on ${statistics.mostRecentlyPlayed.lastPlayedOn}`
                : 'Based on the date you played',
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
            <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {cards.map(card => (
                    <div key={card.title} className="min-w-0 rounded-box border border-base-300 bg-base-100 p-4">
                        <h3 className="text-sm text-base-content/70">{card.title}</h3>
                        <p className="mt-2 break-words text-xl font-bold">{card.value}</p>
                        <p className="mt-1 break-words text-xs text-base-content/70">{card.detail}</p>
                    </div>
                ))}
            </div>
            {statistics && (
                <p className="mt-3 text-xs text-base-content/70">
                    {statistics.refreshedAt && (
                        <>Last calculated: <time dateTime={statistics.refreshedAt}>{new Date(statistics.refreshedAt).toLocaleString()}</time>.{' '}</>
                    )}
                    Recent changes may take a little while to be calculated. These cards show the results saved when you opened the page.
                </p>
            )}
        </section>
    )
}

export default CollectionStatistics
