import { useEffect, useId, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import AppLayout from '../AppLayout'
import { fetchCollections, type GameCollectionSummary } from '../../api/collectionApi'
import { apiAudience } from '../../api/apiHelpers'
import TrackHeatmap from './TrackHeatmap'

/** Loads the available collections and manages Track's own collection menu and selection. */
function TrackView({ initialCollectionId }: { initialCollectionId: string | null })
{
    // Keep the collection list, current Track selection, and menu state together.
    const { getAccessTokenSilently } = useAuth0()
    const [collections, setCollections] = useState<GameCollectionSummary[]>([])
    const [selectedScope, setSelectedScope] = useState<string | null>(null)
    const [collectionError, setCollectionError] = useState<string | null>(null)
    const [retryCount, setRetryCount] = useState(0)
    const [menuOpen, setMenuOpen] = useState(false)
    const menuId = useId()
    const menuButton = useRef<HTMLButtonElement>(null)

    // Load the collection choices when this view opens, or when the user retries a failed request.
    useEffect(() =>
    {
        let isCurrent = true
        const controller = new AbortController()

        /** Load the owned shelves before deciding which one Track should display. */
        async function loadCollections()
        {
            try
            {
                // Get permission to read this account's collections, then request the list.
                const token = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                const loaded = await fetchCollections(token, controller.signal)
                if (!isCurrent)
                {
                    return
                }

                // A direct visit uses the saved default; a removed shelf also returns to that valid default.
                const startingCollection = loaded.find(collection => collection.id === initialCollectionId) ?? loaded[0]
                setCollections(loaded)
                setSelectedScope(startingCollection.id)
                setCollectionError(null)
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setCollectionError(error instanceof Error ? error.message : 'Could not load your collections.')
                }
            }
        }

        void loadCollections()
        return () =>
        {
            // Cancel work when the user leaves and ignore any answer that still arrives afterward.
            isCurrent = false
            controller.abort()
        }
    }, [getAccessTokenSilently, initialCollectionId, retryCount])

    /** Clear the displayed error and ask the collection-loading effect to run again. */
    function retryCollections()
    {
        setCollectionError(null)
        setRetryCount(count => count + 1)
    }

    // Turn the chosen ID into the collection name displayed below the page heading.
    const selectedName = selectedScope === 'all'
        ? 'All Collections'
        : collections.find(collection => collection.id === selectedScope)?.name

    return (
        <AppLayout>
            <section className="mx-auto max-w-4xl py-8" aria-labelledby="stats-summary-title">
                <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                        <h1 id="stats-summary-title" className="font-serif text-3xl font-bold">Track</h1>
                        {selectedName && <p className="mt-2 break-words text-base-content/70" role="status">{selectedName}</p>}
                    </div>
                    {/* Close the menu when focus leaves it; Escape also returns focus to its button. */}
                    <div className="relative shrink-0" onBlur={event =>
                    {
                        if (!event.currentTarget.contains(event.relatedTarget))
                        {
                            setMenuOpen(false)
                        }
                    }} onKeyDown={event =>
                    {
                        if (event.key === 'Escape')
                        {
                            setMenuOpen(false)
                            menuButton.current?.focus()
                        }
                    }}>
                        <button ref={menuButton} type="button" className="btn btn-square border-base-300"
                            aria-label="Choose Track collection" aria-expanded={menuOpen} aria-controls={menuId}
                            disabled={collections.length === 0} onClick={() => setMenuOpen(open => !open)}>
                            <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden="true">
                                <circle cx="5" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="19" cy="12" r="2" />
                            </svg>
                        </button>
                        {menuOpen && (
                            <div id={menuId} className="absolute right-0 z-30 mt-2 w-64 max-w-[80vw] rounded-box border border-base-300 bg-base-100 p-3 shadow-xl">
                                <p className="mb-2 text-sm font-semibold">View statistics for</p>
                                <ul className="max-h-64 space-y-1 overflow-y-auto" aria-label="View statistics for">
                                    {[{ id: 'all', name: 'All Collections' }, ...collections].map(collection => (
                                        <li key={collection.id}>
                                            <button type="button" className="btn btn-ghost h-auto min-h-10 w-full justify-start whitespace-normal break-words py-2 text-left"
                                                aria-pressed={selectedScope === collection.id} onClick={() =>
                                                {
                                                    // Track's filter does not change the viewed shelf or the saved default.
                                                    setSelectedScope(collection.id)
                                                    setMenuOpen(false)
                                                    menuButton.current?.focus()
                                                }}>
                                                {collection.name}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </div>
                </div>

                {/* Show a collection-loading problem first; only load the heatmap once a collection is chosen. */}
                {collectionError ? (
                    <div className="alert alert-error mt-6" role="alert">
                        <span>{collectionError}</span>
                        <button type="button" className="btn btn-sm" onClick={retryCollections}>Retry</button>
                    </div>
                ) : selectedScope === null ? (
                    <p className="mt-6" role="status">Loading your collections...</p>
                ) : (
                    // A different key starts a fresh heatmap; no collection ID means account-wide totals.
                    <TrackHeatmap key={selectedScope} collectionId={selectedScope === 'all' ? undefined : selectedScope} />
                )}
            </section>
        </AppLayout>
    )
}

export default TrackView
