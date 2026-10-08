import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link, useParams } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import type { BoardGame, GamePlay } from '../types/boardGame'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const apiAudience = import.meta.env.VITE_AUTH0_AUDIENCE || 'https://trackmyboardgames-api'

// Turn a failed response into a readable message; fall back to the HTTP status if the body is not JSON.
async function responseError(response: Response)
{
    const body = await response.json().catch(() => null) as { detail?: string; message?: string } | null
    return body?.detail ?? body?.message ?? `The backend returned HTTP ${response.status}.`
}

// Small helper: GET a URL with the access token and return the parsed JSON, or throw if the backend refused.
// Keeping this separate stops the page's load function from repeating the same fetch/check code twice.
async function getJson<T>(url: string, accessToken: string | undefined): Promise<T>
{
    if (!accessToken)
    {
        throw new Error('You are not signed in.')
    }

    const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    return await response.json() as T
}

// Shows one game's play history. The game comes from the URL (/collection/:gameId).
function PlayHistoryPage()
{
    const { getAccessTokenSilently } = useAuth0()

    // Read the :gameId part of the URL, e.g. /collection/abc-123 gives gameId = 'abc-123'.
    const { gameId = '' } = useParams<{ gameId: string }>()

    // State is data React remembers; changing it re-renders the page.
    const [game, setGame] = useState<BoardGame | null>(null)
    const [plays, setPlays] = useState<GamePlay[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

    // Runs when the page opens (and again if gameId changes): loads the game and its plays from the backend.
    useEffect(() =>
    {
        // Lets us ignore a response that arrives after the user has already left the page.
        let isCurrent = true

        async function loadHistory()
        {
            setIsLoading(true)
            setErrorMessage(null)

            try
            {
                // Ask Auth0 for a token that only allows reading games.
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })

                // Fetch the game's details and its plays at the same time rather than one after the other.
                const [gameResult, playsResult] = await Promise.all([
                    getJson<BoardGame>(`${apiBaseUrl}/api/games/${gameId}`, accessToken),
                    getJson<GamePlay[]>(`${apiBaseUrl}/api/games/${gameId}/plays`, accessToken),
                ])

                if (isCurrent)
                {
                    setGame(gameResult)
                    setPlays(playsResult)
                }
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not load this play history.')
                }
            }
            finally
            {
                if (isCurrent)
                {
                    setIsLoading(false)
                }
            }
        }

        void loadHistory()

        // Cleanup: runs when the page closes, so late responses stop updating state.
        return () =>
        {
            isCurrent = false
        }
    }, [gameId, getAccessTokenSilently])

    // What follows is the screen itself. {condition && ...} and {condition ? a : b} choose what to show.
    return (
        <AppLayout>
            <section className="mx-auto max-w-6xl py-10 sm:py-14">
                {/* Heading: the game's name once loaded, plus navigation back to the collection. */}
                <div className="mb-6 border-b border-base-300 pb-6">
                    <p className="mb-3 text-xs font-bold tracking-[0.16em] text-secondary">PLAY HISTORY</p>
                    <h1 className="font-serif text-4xl leading-tight text-base-content sm:text-5xl">
                        {game?.name ?? 'Game history'}
                    </h1>
                    <p className="mt-3 text-sm text-base-content/70">
                        Record of times this game was brought to the table.
                    </p>
                    <div className="mt-5 flex items-center gap-3">
                        <Link className="btn btn-ghost btn-sm" to="/collection">Back to collection</Link>
                        <button className="btn btn-primary btn-sm" type="button" disabled>
                            Add play (coming next)
                        </button>
                    </div>
                </div>

                {/* Error banner, shown only if loading failed. */}
                {errorMessage && (
                    <p className="alert alert-soft alert-error mt-5 text-sm in-data-[theme=boardgame-light]:border-[#e2b6a9] in-data-[theme=boardgame-light]:bg-[#f7e9e5] in-data-[theme=boardgame-light]:text-[#792c23]" role="alert">
                        {errorMessage}
                    </p>
                )}

                {/* Body: loading message, then the empty state, then the table of plays. */}
                {isLoading ? (
                    <p className="rounded-box border border-base-300 bg-base-100 px-4 py-6 text-sm text-base-content/70" role="status">
                        Loading play history...
                    </p>
                ) : !errorMessage && plays.length === 0 ? (
                    <div className="rounded-box border border-dashed border-base-300 bg-base-100 px-5 py-10 text-center">
                        <p className="font-serif text-2xl text-base-content">No plays yet.</p>
                        <p className="mt-2 text-sm text-base-content/70">Use “Add play” once step 5 is implemented.</p>
                    </div>
                ) : !errorMessage ? (
                    <div className="overflow-x-auto rounded-box border border-base-300 bg-base-100">
                        <table className="table table-zebra w-full min-w-[46rem] text-sm">
                            <thead className="text-xs uppercase tracking-wide text-base-content/70">
                                <tr>
                                    <th scope="col">Played on</th>
                                    <th scope="col">Players</th>
                                    <th scope="col">Duration</th>
                                    <th scope="col">Notes</th>
                                </tr>
                            </thead>
                            <tbody>
                                {/* One table row per play; the backend already sorted them newest first. */}
                                {plays.map(play => (
                                    <tr key={play.id}>
                                        <td className="whitespace-nowrap font-semibold text-base-content">{play.playedOn}</td>
                                        <td className="whitespace-nowrap text-base-content/70">{play.playerCount ?? '—'}</td>
                                        <td className="whitespace-nowrap text-base-content/70">
                                            {play.durationMinutes ? `${play.durationMinutes} min` : '—'}
                                        </td>
                                        <td className="max-w-2xl whitespace-normal text-base-content/70">{play.notes || '—'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : null}
            </section>
        </AppLayout>
    )
}

export default PlayHistoryPage
