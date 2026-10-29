import { useContext } from 'react'
import { CurrentCollectionContext } from '../context/currentCollection'

/** Shares the shelf being viewed without changing the account's saved default collection. */
export function useCurrentCollection()
{
    const state = useContext(CurrentCollectionContext)

    if (!state)
    {
        throw new Error('Current collection state must be used inside its provider.')
    }

    return state
}
