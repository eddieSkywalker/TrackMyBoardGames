import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import MonthlyPlayHeatmap from '../MonthlyPlayHeatmap'
import { fetchMonthlyHeatmap, type MonthlyHeatmap } from '../../api/statisticsApi'
import { apiAudience } from '../../api/apiHelpers'

type HeatmapState =
    | { status: 'loading' }
    | { status: 'loaded'; heatmap: MonthlyHeatmap }
    | { status: 'error'; message: string }

/** A new scope starts with a blank loading state so a late answer cannot show another shelf's heatmap. */
function TrackHeatmap({ collectionId }: { collectionId?: string })
{
    // Track loading, a saved answer, or an error so each has its own display.
    const { getAccessTokenSilently } = useAuth0()
    const [state, setState] = useState<HeatmapState>({ status: 'loading' })
    const [retryCount, setRetryCount] = useState(0)

    // Fetch saved daily counts when this heatmap opens or the user retries; this does not recalculate statistics.
    useEffect(() =>
    {
        let isCurrent = true
        const controller = new AbortController()

        async function loadHeatmap()
        {
            try
            {
                // Prepare the login token, then stop if the user has already left this view.
                const token = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                if (!isCurrent)
                {
                    return
                }

                // Read the chosen collection's counts and display them only if this request is still current.
                const heatmap = await fetchMonthlyHeatmap(token, controller.signal, collectionId)
                if (isCurrent)
                {
                    setState({ status: 'loaded', heatmap })
                }
            }
            catch (error)
            {
                // Report failures for this view without showing errors from a request we have already left.
                if (isCurrent)
                {
                    setState({ status: 'error', message: error instanceof Error ? error.message : 'Could not load your heatmap.' })
                }
            }
        }

        void loadHeatmap()
        return () =>
        {
            // Switching collections or leaving Track must not let an old answer replace the new display.
            isCurrent = false
            controller.abort()
        }
    }, [getAccessTokenSilently, collectionId, retryCount])

    /** Restore the loading message and ask the heatmap-loading effect to try again. */
    function retryHeatmap()
    {
        setState({ status: 'loading' })
        setRetryCount(count => count + 1)
    }

    // Show progress or a retryable error before handing a successful answer to the chart component.
    if (state.status === 'loading')
    {
        return <p className="mt-6" role="status">Loading this month's plays...</p>
    }

    if (state.status === 'error')
    {
        return (
            <div className="alert alert-error mt-6" role="alert">
                <span>{state.message}</span>
                <button type="button" className="btn btn-sm" onClick={retryHeatmap}>Retry</button>
            </div>
        )
    }

    return <MonthlyPlayHeatmap heatmap={state.heatmap} />
}

export default TrackHeatmap
