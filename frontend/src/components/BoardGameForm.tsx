import { useState, type FormEvent } from 'react'
import type { BoardGame, GameFormValues } from '../types/boardGame'
import { blobToDataUrl } from '../api/imageApi'
import ImagePicker from './ImagePicker'

type BoardGameFormProps = {
    initialGame?: BoardGame
    isSaving: boolean
    onCancel: () => void
    onSubmit: (values: GameFormValues, coverImage: string | null) => Promise<void>
}

function createInitialValues(game?: BoardGame): GameFormValues
{
    return {
        name: game?.name ?? '',
        minPlayers: game?.minPlayers?.toString() ?? '',
        maxPlayers: game?.maxPlayers?.toString() ?? '',
        bestPlayers: game?.bestPlayers ?? '',
        playTimeMinutes: game?.playTimeMinutes?.toString() ?? '',
        notes: game?.notes ?? '',
    }
}

// Shared add/edit form: it starts blank for a new game or prefilled from the selected game.
function BoardGameForm({ initialGame, isSaving, onCancel, onSubmit }: BoardGameFormProps)
{
    const [values, setValues] = useState<GameFormValues>(() => createInitialValues(initialGame))
    const isEditing = Boolean(initialGame)

    // The cover is held as text (a "data URL") so it can be shown right away and sent along with the game.
    // Nothing is saved until the form is submitted, so Cancel leaves the old cover alone.
    const [cover, setCover] = useState<string | null>(initialGame?.coverImage ?? null)

    async function handleCoverPicked(image: Blob)
    {
        setCover(await blobToDataUrl(image))
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>)
    {
        event.preventDefault()
        await onSubmit(values, cover)
    }

    // The preview shows the chosen cover, or a placeholder when there is none.
    function renderCoverPreview()
    {
        if (cover)
        {
            return <img className="size-24 rounded object-cover" src={cover} alt="Cover preview" />
        }

        return (
            <div className="grid size-24 place-items-center rounded bg-base-300 text-xs font-semibold uppercase text-base-content/60">
                No cover
            </div>
        )
    }

    return (
        <form className="mt-7 rounded-box border border-base-300 bg-base-100 p-5 sm:p-7" onSubmit={handleSubmit}>
            <h2 className="font-serif text-2xl text-base-content">{isEditing ? 'Edit board game' : 'Add a board game'}</h2>
            <p className="mt-1 text-sm text-base-content/70">Name is required. The other details are optional.</p>
            <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-3">
                    Game name
                    <input
                        className="input input-bordered w-full bg-base-100 font-normal"
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
                        className="input input-bordered w-full bg-base-100 font-normal"
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
                        className="input input-bordered w-full bg-base-100 font-normal"
                        type="number"
                        min="1"
                        step="1"
                        value={values.maxPlayers}
                        onChange={event => setValues({ ...values, maxPlayers: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Best played with
                    <input
                        className="input input-bordered w-full bg-base-100 font-normal"
                        placeholder="e.g. 3-4"
                        maxLength={20}
                        value={values.bestPlayers}
                        onChange={event => setValues({ ...values, bestPlayers: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-3">
                    Play time in minutes
                    <input
                        className="input input-bordered w-full bg-base-100 font-normal"
                        type="number"
                        min="1"
                        step="1"
                        value={values.playTimeMinutes}
                        onChange={event => setValues({ ...values, playTimeMinutes: event.target.value })}
                    />
                </label>
                <div className="grid gap-2 sm:col-span-3">
                    <span className="text-sm font-semibold">Cover photo</span>
                    <div className="flex flex-wrap items-center gap-4">
                        {renderCoverPreview()}
                        <div className="grid gap-2">
                            <ImagePicker maxSize={500} disabled={isSaving} onPick={handleCoverPicked} />
                            {cover && (
                                <button
                                    className="btn btn-ghost btn-sm w-fit"
                                    type="button"
                                    onClick={() => setCover(null)}
                                    disabled={isSaving}
                                >
                                    Remove photo
                                </button>
                            )}
                        </div>
                    </div>
                </div>
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-3">
                    Notes
                    <textarea
                        className="textarea textarea-bordered min-h-24 w-full bg-base-100 font-normal"
                        value={values.notes}
                        onChange={event => setValues({ ...values, notes: event.target.value })}
                        maxLength={4000}
                    />
                </label>
            </div>
            <div className="mt-5 flex flex-wrap items-center gap-3">
                <button
                    className="btn btn-primary min-h-11 px-5 text-sm disabled:cursor-wait disabled:opacity-60"
                    type="submit"
                    disabled={isSaving}
                >
                    {isSaving ? 'Saving...' : isEditing ? 'Save changes' : 'Save game'}
                </button>
                <button
                    className="btn btn-ghost min-h-11 px-4 text-sm disabled:opacity-60"
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