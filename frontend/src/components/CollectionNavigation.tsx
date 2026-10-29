import { useId, useRef, useState } from 'react'
import type { GameCollectionSummary } from '../api/collectionApi'

type Props = {
    collections: GameCollectionSummary[]
    selected: GameCollectionSummary
    disabled: boolean
    savingDefault: boolean
    onSelect: (id: string) => void
    onMakeDefault: () => void
}

/** The star saves a preference; the desktop menu only changes the collection being viewed. */
function CollectionNavigation({ collections, selected, disabled, savingDefault, onSelect, onMakeDefault }: Props)
{
    const [menuOpen, setMenuOpen] = useState(false)
    // Give the menu its own accessible ID and remember its button so keyboard focus can return there.
    const menuId = useId()
    const menuButton = useRef<HTMLButtonElement>(null)
    const index = collections.findIndex(collection => collection.id === selected.id)

    return (
        <div className="flex items-start gap-2">
            {/* An outlined star saves this collection as the default. A filled star cannot clear the existing default. */}
            <button
                type="button"
                className="btn btn-square btn-sm border-base-300 bg-base-100"
                aria-label={selected.defaultCollection ? 'Default collection' : 'Make this your default collection'}
                aria-pressed={selected.defaultCollection}
                title={selected.defaultCollection ? 'Your default collection' : 'Make this your default collection'}
                disabled={disabled || savingDefault || selected.defaultCollection}
                onClick={onMakeDefault}
            >
                {savingDefault ? <span className="loading loading-spinner loading-xs" /> : (
                    <svg viewBox="0 0 24 24" className="size-5 text-warning" fill={selected.defaultCollection ? 'currentColor' : 'none'}
                        stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
                        <path strokeLinejoin="round" d="m12 3 2.8 5.7 6.3.9-4.55 4.44 1.07 6.26L12 17.35l-5.62 2.95 1.07-6.26L2.9 9.6l6.3-.9L12 3Z" />
                    </svg>
                )}
            </button>
            {/* A single collection needs the default marker, but no collection-switching controls. */}
            {collections.length > 1 && (
                <>
                    {/* Desktop only: close when focus leaves the menu, but not when moving between its controls. */}
                    <div className="relative hidden sm:block" onBlur={event =>
                    {
                        if (!event.currentTarget.contains(event.relatedTarget)) setMenuOpen(false)
                    }} onKeyDown={event =>
                    {
                        // Escape dismisses the dropdown and returns focus to the button that opened it.
                        if (event.key === 'Escape')
                        {
                            setMenuOpen(false)
                            menuButton.current?.focus()
                        }
                    }}>
                        <button ref={menuButton} type="button" className="btn btn-square btn-sm border-base-300 bg-base-100"
                            aria-label="Choose collection" aria-expanded={menuOpen} aria-controls={menuId}
                            disabled={disabled || savingDefault} onClick={() => setMenuOpen(open => !open)}>
                            <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden="true">
                                <circle cx="5" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="19" cy="12" r="2" />
                            </svg>
                        </button>
                        {menuOpen && (
                            <div id={menuId} className="absolute right-0 z-30 mt-2 w-64 rounded-box border border-base-300 bg-base-100 p-3 shadow-xl">
                                <label className="mb-2 block text-sm font-semibold" htmlFor={`${menuId}-select`}>View collection</label>
                                <select id={`${menuId}-select`} className="select select-bordered w-full"
                                    value={selected.id} disabled={disabled || savingDefault} onChange={event =>
                                    {
                                        // Viewing another collection does not save it as the default.
                                        onSelect(event.target.value)
                                        setMenuOpen(false)
                                        menuButton.current?.focus()
                                    }}>
                                    {collections.map(collection => (
                                        <option key={collection.id} value={collection.id}>
                                            {collection.name}{collection.defaultCollection ? ' (default)' : ''}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}
                    </div>
                    {/* Mobile only: buttons offer an alternative to swiping and stop at either end without looping. */}
                    <div className="flex gap-1 sm:hidden">
                        <button className="btn btn-square btn-sm bg-base-100" type="button" aria-label="Previous collection"
                            disabled={disabled || savingDefault || index === 0} onClick={() => onSelect(collections[index - 1].id)}>
                            <svg viewBox="0 0 24 24" className="size-4 text-base-content" fill="none" stroke="currentColor"
                                strokeWidth="2" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>
                        </button>
                        <button className="btn btn-square btn-sm bg-base-100" type="button" aria-label="Next collection"
                            disabled={disabled || savingDefault || index === collections.length - 1}
                            onClick={() => onSelect(collections[index + 1].id)}>
                            <svg viewBox="0 0 24 24" className="size-4 text-base-content" fill="none" stroke="currentColor"
                                strokeWidth="2" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg>
                        </button>
                    </div>
                </>
            )}
        </div>
    )
}

export default CollectionNavigation
