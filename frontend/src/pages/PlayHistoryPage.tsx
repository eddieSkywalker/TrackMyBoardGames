import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link, useParams } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import PlayForm from '../components/PlayForm'
import type { BoardGame, GamePlay, PlayFormValues } from '../types/boardGame'

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

// Small helper for requests that change data (POST add, PUT edit, DELETE remove).
// body is optional because DELETE sends none; the result is the saved play, or null when there is no response body.
async function sendRequest(
    method: 'POST' | 'PUT' | 'DELETE',
    url: string,
    accessToken: string | undefined,
    body?: unknown,
): Promise<GamePlay | null>
{
    if (!accessToken)
    {
        throw new Error('You are not signed in.')
    }

    const response = await fetch(url, {
        method,
        headers: {
            Authorization: `Bearer ${accessToken}`,
            ...(body ? { 'Content-Type': 'application/json' } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    // A successful DELETE returns 204 No Content, so there is nothing to parse.
    return response.status === 204 ? null : await response.json() as GamePlay
}

// Newest play first, matching the order the backend uses (date, then creation time).
function sortPlays(plays: GamePlay[]): GamePlay[]
{
    return [...plays].sort((left, right) =>
        right.playedOn.localeCompare(left.playedOn) || right.createdAt.localeCompare(left.createdAt))
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
    const [successMessage, setSuccessMessage] = useState<string | null>(null)

    // Form state: isFormOpen shows the add form; editingPlay (when set) shows the edit form for that play.
    const [isFormOpen, setIsFormOpen] = useState(false)
    const [editingPlay, setEditingPlay] = useState<GamePlay | null>(null)
    const [isSaving, setIsSaving] = useState(false)
    const [deletingPlayId, setDeletingPlayId] = useState<string | null>(null)

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

    function openAddForm()
    {
        setEditingPlay(null)
        setIsFormOpen(true)
        setErrorMessage(null)
        setSuccessMessage(null)
    }

    function openEditForm(play: GamePlay)
    {
        setIsFormOpen(false)
        setEditingPlay(play)
        setErrorMessage(null)
        setSuccessMessage(null)
    }

    function closeForm()
    {
        setIsFormOpen(false)
        setEditingPlay(null)
    }

    // Save the form: PUT when editing an existing play, POST when adding a new one.
    async function handleSave(values: PlayFormValues)
    {
        setIsSaving(true)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            // The form holds text, so convert to numbers and leave blank optional fields out.
            const saved = await sendRequest(
                editingPlay ? 'PUT' : 'POST',
                editingPlay
                    ? `${apiBaseUrl}/api/games/${gameId}/plays/${editingPlay.id}`
                    : `${apiBaseUrl}/api/games/${gameId}/plays`,
                accessToken,
                {
                    playedOn: values.playedOn,
                    durationMinutes: values.durationMinutes ? Number(values.durationMinutes) : undefined,
                    playerCount: values.playerCount ? Number(values.playerCount) : undefined,
                    notes: values.notes.trim() || undefined,
                },
            )

            if (saved)
            {
                // Replace the old copy (if editing) with the saved one, then re-sort.
                setPlays(current => sortPlays([...current.filter(play => play.id !== saved.id), saved]))
            }
            setSuccessMessage(editingPlay ? 'Play was updated.' : 'Play was added.')
            closeForm()
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not save this play.')
        }
        finally
        {
            setIsSaving(false)
        }
    }

    // Delete a play after the user confirms; the backend still checks ownership.
    async function handleDelete(play: GamePlay)
    {
        if (!window.confirm(`Delete the play from ${play.playedOn}?`)) return

        setDeletingPlayId(play.id)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            await sendRequest('DELETE', `${apiBaseUrl}/api/games/${gameId}/plays/${play.id}`, accessToken)

            setPlays(current => current.filter(existing => existing.id !== play.id))
            if (editingPlay?.id === play.id) setEditingPlay(null)
            setSuccessMessage('Play was deleted.')
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not delete this play.')
        }
        finally
        {
            setDeletingPlayId(null)
        }
    }

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
                        <button className="btn btn-primary btn-sm" type="button" onClick={openAddForm} disabled={isLoading}>
                            Add play
                        </button>
                    </div>
                </div>

                {/* Add/edit form. key makes React start a fresh form when switching between plays. */}
                {(isFormOpen || editingPlay) && (
                    <PlayForm
                        key={editingPlay?.id ?? 'new'}
                        initialPlay={editingPlay ?? undefined}
                        isSaving={isSaving}
                        onCancel={closeForm}
                        onSubmit={handleSave}
                    />
                )}

                {/* Success banner after an add, edit, or delete. */}
                {successMessage && (
                    <p className="alert alert-soft alert-success mb-5 text-sm in-data-[theme=boardgame-light]:border-[#b9d2c0] in-data-[theme=boardgame-light]:bg-[#e7f2e9] in-data-[theme=boardgame-light]:text-[#1f4d2c]" role="status">
                        {successMessage}
                    </p>
                )}

                {/* Error banner, shown if loading or saving failed. */}
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
                ) : !game ? null : plays.length === 0 ? (
                    <div className="rounded-box border border-dashed border-base-300 bg-base-100 px-5 py-10 text-center">
                        <p className="font-serif text-2xl text-base-content">No plays yet.</p>
                        <p className="mt-2 text-sm text-base-content/70">Use “Add play” to record the first one.</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto rounded-box border border-base-300 bg-base-100">
                        <table className="table table-zebra w-full min-w-[46rem] text-sm">
                            <thead className="text-xs uppercase tracking-wide text-base-content/70">
                                <tr>
                                    <th scope="col">Played on</th>
                                    <th scope="col">Players</th>
                                    <th scope="col">Duration</th>
                                    <th scope="col">Notes</th>
                                    <th scope="col"><span className="sr-only">Actions</span></th>
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
                                                                                <td className="whitespace-nowrap text-right">
                                                                                    <button
                                                                                        className="btn btn-ghost btn-xs"
                                                                                        type="button"
                                                                                        onClick={() => openEditForm(play)}
                                                                                        disabled={deletingPlayId === play.id}
                                                                                    >
                                                                                        Edit
                                                                                    </button>
                                                                                    <button
                                                                                        className="btn btn-ghost btn-xs text-error"
                                                                                        type="button"
                                                                                        onClick={() => void handleDelete(play)}
                                                                                        disabled={deletingPlayId === play.id}
                                                                                    >
                                                                                        {deletingPlayId === play.id ? 'Deleting...' : 'Delete'}
                                                                                    </button>
                                                                                </td>
                                                                            </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </AppLayout>
    )
}

export default PlayHistoryPage
