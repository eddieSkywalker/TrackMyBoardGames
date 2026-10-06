import { useState } from 'react'
import type { BoardGame } from '../types/boardGame'

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
    const filteredGames = games.filter(game => game.name.toLowerCase().includes(search.trim().toLowerCase()))

    return (
        <>
            <label className="sr-only" htmlFor="game-search">Search games by name</label>
            <input
                className="mb-3 min-h-10 w-full rounded border border-[#bbc8be] bg-white px-3 text-sm outline-none placeholder:text-[#7b8981] focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20 sm:max-w-sm"
                id="game-search"
                type="search"
                placeholder="Search your games"
                value={search}
                onChange={event => setSearch(event.target.value)}
            />
            {filteredGames.length === 0 ? (
                <p className="border-y border-[#dce1da] bg-[#fbfaf6] px-4 py-6 text-sm text-[#65736b]" role="status">
                    No games match “{search}”.
                </p>
            ) : (
                <div className="overflow-x-auto border-y border-[#dce1da] bg-[#fbfaf6]">
                    <table className="w-full min-w-[42rem] border-collapse text-left text-sm">
                        <thead className="bg-[#edf1ec] text-xs uppercase tracking-wide text-[#52665b]">
                            <tr>
                                <th className="px-4 py-3 font-bold" scope="col">Game</th>
                                <th className="px-4 py-3 font-bold" scope="col">Players</th>
                                <th className="px-4 py-3 font-bold" scope="col">Play time</th>
                                <th className="px-4 py-3 text-right font-bold" scope="col">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[#dce1da]">
                            {filteredGames.map(game => (
                                <tr className="transition-colors hover:bg-[#eef3ed]" key={game.id}>
                                    <th className="px-4 py-4 font-semibold text-[#20352e]" scope="row">
                                        <div>{game.name}</div>
                                        {game.notes && <p className="mt-1 max-w-2xl whitespace-normal text-left text-xs font-normal text-[#65736b]">{game.notes}</p>}
                                    </th>
                                    <td className="whitespace-nowrap px-4 py-4 text-[#65736b]">
                                        {(game.minPlayers || game.maxPlayers)
                                            ? `${game.minPlayers ?? '?'}–${game.maxPlayers ?? '?'} players`
                                            : '—'}
                                    </td>
                                    <td className="whitespace-nowrap px-4 py-4 text-[#65736b]">
                                        {game.playTimeMinutes ? `${game.playTimeMinutes} min` : '—'}
                                    </td>
                                    <td className="px-4 py-4">
                                        <div className="flex justify-end gap-3">
                                            <button
                                                className="font-semibold text-[#27634e] hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c]"
                                                type="button"
                                                onClick={() => onEdit(game)}
                                            >
                                                Edit
                                            </button>
                                            <button
                                                className="font-semibold text-[#9a3f2d] hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c] disabled:cursor-wait disabled:opacity-60"
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
        </>
    )
}

export default BoardGameCollection