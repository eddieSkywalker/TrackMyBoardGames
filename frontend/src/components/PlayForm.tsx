import { useState, type FormEvent } from 'react'
import type { GamePlay, PlayFormValues } from '../types/boardGame'

type PlayFormProps = {
    initialPlay?: GamePlay
    isSaving: boolean
    onCancel: () => void
    onSubmit: (values: PlayFormValues) => Promise<void>
}

// Today's date as yyyy-mm-dd in the user's own time zone (what <input type="date"> expects).
function today(): string
{
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${now.getFullYear()}-${month}-${day}`
}

// Blank values for a new play (dated today) or prefilled values from the play being edited.
// Form inputs always hold text, so numbers are stored as strings and converted on submit.
function createInitialValues(play?: GamePlay): PlayFormValues
{
    return {
        playedOn: play?.playedOn ?? today(),
        durationMinutes: play?.durationMinutes?.toString() ?? '',
        playerCount: play?.playerCount?.toString() ?? '',
        notes: play?.notes ?? '',
    }
}

// Shared add/edit form for one play. The parent decides what "save" does; this only collects the values.
function PlayForm({ initialPlay, isSaving, onCancel, onSubmit }: PlayFormProps)
{
    const [values, setValues] = useState<PlayFormValues>(() => createInitialValues(initialPlay))
    const isEditing = Boolean(initialPlay)

    async function handleSubmit(event: FormEvent<HTMLFormElement>)
    {
        // Stop the browser's default full-page form submit; React handles it instead.
        event.preventDefault()
        await onSubmit(values)
    }

    return (
        <form className="mb-6 rounded-box border border-base-300 bg-base-100 p-5 sm:p-7" onSubmit={handleSubmit}>
            <h2 className="font-serif text-2xl text-base-content">{isEditing ? 'Edit play' : 'Add a play'}</h2>
            <p className="mt-1 text-sm text-base-content/70">Date is required. The other details are optional.</p>
            <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <label className="grid gap-1.5 text-sm font-semibold">
                    Date played
                    <input
                        className="input w-full bg-base-100 font-normal"
                        type="date"
                        value={values.playedOn}
                        onChange={event => setValues({ ...values, playedOn: event.target.value })}
                        required
                        autoFocus={window.matchMedia('(hover: hover) and (pointer: fine)').matches}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Total players
                    <input
                        className="input w-full bg-base-100 font-normal"
                        type="number"
                        min="1"
                        step="1"
                        value={values.playerCount}
                        onChange={event => setValues({ ...values, playerCount: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold">
                    Play time in minutes
                    <input
                        className="input w-full bg-base-100 font-normal"
                        type="number"
                        min="1"
                        step="1"
                        value={values.durationMinutes}
                        onChange={event => setValues({ ...values, durationMinutes: event.target.value })}
                    />
                </label>
                <label className="grid gap-1.5 text-sm font-semibold sm:col-span-3">
                    Notes
                    <textarea
                        className="textarea min-h-24 w-full bg-base-100 font-normal"
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
                    {isSaving ? 'Saving...' : isEditing ? 'Save changes' : 'Save play'}
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

export default PlayForm
