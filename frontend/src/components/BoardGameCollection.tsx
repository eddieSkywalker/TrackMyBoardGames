import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { BoardGame } from '../types/boardGame'

// The grey "No cover" square shown when a game has no picture (or it is still loading).
function CoverPlaceholder({ name }: { name: string })
{
    return (
        <div
            className="grid size-14 shrink-0 place-items-center rounded bg-base-300 text-[10px] font-semibold uppercase tracking-wide text-base-content/60"
            role="img"
            aria-label={`${name} cover image placeholder`}
        >
            No cover
        </div>
    )
}

type BoardGameCollectionProps = {
    games: BoardGame[]
    onEdit: (game: BoardGame) => void
    onDelete: (game: BoardGame) => void
    deletingGameId: string | null
}

// Shows a searchable collection table and passes edit/delete actions back to its parent component.
function BoardGameCollection({ games, onEdit, onDelete, deletingGameId }: BoardGameCollectionProps)
{
    const [search, setSearch] = useState('')
    const [selectedCover, setSelectedCover] = useState<{ name: string; image: string } | null>(null)
    const coverDialog = useRef<HTMLDialogElement>(null)
    const filteredGames = games.filter(game => game.name.toLowerCase().includes(search.trim().toLowerCase()))

    // Open the native dialog when a cover is selected; the browser provides Escape and focus handling.
    useEffect(() =>
    {
        const dialog = coverDialog.current
        if (!dialog) return

        if (selectedCover && !dialog.open)
        {
            dialog.showModal()
        }
        else if (!selectedCover && dialog.open)
        {
            dialog.close()
        }
    }, [selectedCover])

    return (
        <>
            <label className="sr-only" htmlFor="game-search">Search games by name</label>
            <input
                className="input input-bordered mb-3 w-full bg-base-100 text-sm sm:max-w-sm"
                id="game-search"
                type="search"
                placeholder="Search your games"
                value={search}
                onChange={event => setSearch(event.target.value)}
            />
            {filteredGames.length === 0 ? (
                <p className="rounded-box border border-base-300 bg-base-100 px-4 py-6 text-sm text-base-content/70" role="status">
                    No games match “{search}”.
                </p>
            ) : (
                <div className="overflow-x-auto rounded-box border border-base-300 bg-base-100">
                    <table className="table table-zebra w-full min-w-[52rem] text-sm">
                        <thead className="text-xs uppercase tracking-wide text-base-content/70">
                            <tr>
                                <th scope="col">Cover</th>
                                <th scope="col">Game</th>
                                <th scope="col">Players</th>
                                <th scope="col">Best with</th>
                                <th scope="col">Play time</th>
                                <th scope="col">Plays</th>
                                <th className="w-px whitespace-nowrap text-right" scope="col">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredGames.map(game => (
                                <tr className="transition-colors hover:bg-base-300" key={game.id}>
                                    <td>
                                        {game.coverImage ? (
                                            <button
                                                className="block rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                                                type="button"
                                                aria-label={`Enlarge ${game.name} cover`}
                                                onClick={() =>
                                                {
                                                    if (game.coverImage)
                                                    {
                                                        setSelectedCover({ name: game.name, image: game.coverImage })
                                                    }
                                                }}
                                            >
                                                <img
                                                    className="size-14 shrink-0 rounded object-cover transition-transform hover:scale-105"
                                                    src={game.coverImage}
                                                    alt=""
                                                />
                                            </button>
                                        ) : (
                                            <CoverPlaceholder name={game.name} />
                                        )}
                                    </td>
                                    <th className="max-w-sm whitespace-normal font-semibold text-base-content" scope="row">
                                        <Link className="link link-primary font-semibold" to={`/collection/${game.id}`}>{game.name}</Link>
                                        {game.notes && <p className="mt-1 text-left text-xs font-normal text-base-content/70">{game.notes}</p>}
                                    </th>
                                    <td className="whitespace-nowrap text-base-content/70">
                                        {(game.minPlayers || game.maxPlayers)
                                            ? `${game.minPlayers ?? '?'}–${game.maxPlayers ?? '?'} players`
                                            : '—'}
                                    </td>
                                    <td className="whitespace-nowrap text-base-content/70">
                                        {game.bestPlayers || '—'}
                                    </td>
                                    <td className="whitespace-nowrap text-base-content/70">
                                        {game.playTimeMinutes ? `${game.playTimeMinutes} min` : '—'}
                                    </td>
                                    <td className="whitespace-nowrap text-base-content/70">
                                        {game.playCount}
                                    </td>
                                    <td className="w-px whitespace-nowrap">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                className="btn btn-ghost btn-sm text-primary"
                                                type="button"
                                                onClick={() => onEdit(game)}
                                            >
                                                Edit
                                            </button>
                                            <button
                                                className="btn btn-ghost btn-sm text-error"
                                                type="button"
                                                onClick={() => onDelete(game)}
                                                disabled={deletingGameId === game.id}
                                            >
                                                {deletingGameId === game.id ? 'Deleting...' : 'Delete'}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            <dialog
                ref={coverDialog}
                className="modal"
                aria-label={selectedCover ? `${selectedCover.name} cover` : undefined}
                onClose={() => setSelectedCover(null)}
                onClick={event =>
                {
                    if (event.target === coverDialog.current)
                    {
                        coverDialog.current?.close()
                    }
                }}
            >
                {selectedCover && (
                    <div className="modal-box relative max-h-[calc(100dvh-1rem)] w-fit max-w-4xl overflow-hidden bg-base-100 p-2 shadow-2xl">
                        <button
                            className="btn btn-circle btn-sm btn-ghost absolute right-4 top-4 z-10 bg-base-100/80"
                            type="button"
                            aria-label="Close enlarged cover"
                            onClick={() => coverDialog.current?.close()}
                        >
                            ✕
                        </button>
                        <img
                            className="mx-auto max-h-[calc(100dvh-2rem)] max-w-full rounded object-contain"
                            src={selectedCover.image}
                            alt={`${selectedCover.name} cover`}
                        />
                    </div>
                )}
            </dialog>
        </>
    )
}

export default BoardGameCollection