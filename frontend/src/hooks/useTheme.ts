import { useEffect, useState } from 'react'

const themeStorageKey = 'track-my-board-games-theme'

export type AppTheme = 'boardgame-light' | 'boardgame-dark'

function getSavedTheme(): AppTheme
{
    try
    {
        return window.localStorage.getItem(themeStorageKey) === 'boardgame-dark'
            ? 'boardgame-dark'
            : 'boardgame-light'
    }
    catch
    {
        return 'boardgame-light'
    }
}

// Tracks the chosen light/dark theme, remembers it in browser storage, and applies it to <html>.
export function useTheme()
{
    const [theme, setTheme] = useState<AppTheme>(getSavedTheme)

    useEffect(() =>
    {
        try
        {
            window.localStorage.setItem(themeStorageKey, theme)
        }
        catch
        {
            // The app remains usable when browser storage is unavailable.
        }
    }, [theme])

    // Apply the theme to <html> so the area around #root (its padding) follows it too.
    useEffect(() =>
    {
        document.documentElement.setAttribute('data-theme', theme)
        return () => document.documentElement.removeAttribute('data-theme')
    }, [theme])

    return [theme, setTheme] as const
}
