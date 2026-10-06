import { useState, type FormEvent } from 'react'
import type { BoardGame, GameFormValues } from '../types/boardGame'

type BoardGameFormProps = {
    initialGame?: BoardGame
    isSaving: boolean
    onCancel: () => void
    onSubmit: (values: GameFormValues) => Promise<void>
}

function createInitialValues(game?: BoardGame): GameFormValues
{
    return {
        name: game?.name ?? '',
        minPlayers: game?.minPlayers?.toString() ?? '',
        maxPlayers: game?.maxPlayers?.toString() ?? '',
        playTimeMinutes: game?.playTimeMinutes?.toString() ?? '',
        notes: game?.notes ?? '',
    }
}

// Shared add/edit form: it starts blank for a new game or prefilled from the selected game.
function BoardGameForm({ initialGame, isSaving, onCancel, onSubmit }: BoardGameFormProps)
{
    const [values, setValues] = useState<GameFormValues>(() => createInitialValues(initialGame))
    const isEditing = Boolean(initialGame)

    async function handleSubmit(event: FormEvent<HTMLFormElement>)
    {
        event.preventDefault()
        await onSubmit(values)
    }

    return (
        <form className="mt-7 rounded-md border border-[#cdd5cf] bg-[#fbfaf6] p-5 sm:p-7" onSubmit={handleSubmit}>
            <h2 className="font-serif text-2xl text-[#20352e]">{isEditing ? 'Edit board game' : 'Add a board game'}</h2>
            <p className="mt-1 text-sm text-[#65736b]">Name is required. The other details are optional.</p>
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-2">
                    Game name
                    <input
                        className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                        value={values.name}
                        onChange={event => setValues({ ...values, name: event.target.value })}
                        maxLength={200}
                        required
                        autoFocus
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Minimum players
                    <input
                        className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                        type="number"
                        min="1"
                        step="1"
                        value={values.minPlayers}
                        onChange={event => setValues({ ...values, minPlayers: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Maximum players
                    <input
                        className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                        type="number"
                        min="1"
                        step="1"
                        value={values.maxPlayers}
                        onChange={event => setValues({ ...values, maxPlayers: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Play time in minutes
                    <input
                        className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                        type="number"
                        min="1"
                        step="1"
                        value={values.playTimeMinutes}
                        onChange={event => setValues({ ...values, playTimeMinutes: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-2">
                    Notes
                    <textarea
                        className="min-h-24 rounded border border-[#bbc8be] bg-white px-3 py-2 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                        value={values.notes}
                        onChange={event => setValues({ ...values, notes: event.target.value })}
                        maxLength={4000}
                    />
                </label>
            </div>
            <div className="mt-5 flex flex-wrap items-center gap-3">
                <button
                    className="min-h-11 rounded bg-[#203c33] px-5 text-sm font-bold text-[#fffdf7] hover:bg-[#2e5548] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c] disabled:cursor-wait disabled:opacity-60"
                    type="submit"
                    disabled={isSaving}
                >
                    {isSaving ? 'Saving...' : isEditing ? 'Save changes' : 'Save game'}
                </button>
                <button
                    className="min-h-11 rounded border border-[#bbc8be] px-4 text-sm font-semibold hover:bg-[#edf1ec] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c] disabled:opacity-60"
                    type="button"
                    onClick={onCancel}
                    disabled={isSaving}
                >
                    Cancel
                </button>
            </div>
        </form>
    )
}

export default BoardGameForm