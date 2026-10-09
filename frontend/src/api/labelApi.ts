import { apiBaseUrl, requireToken, responseError } from './apiHelpers'

// One QR label as returned by the backend. The token is the secret text stored inside the QR code.
export type QrLabel = {
    id: string
    gameId: string
    token: string
    revoked: boolean
    createdAt: string
    revokedAt: string | null
}

// The web address a phone opens when it scans the label, e.g. https://myapp.com/scan/abc123.
export function buildScanUrl(token: string): string
{
    return `${window.location.origin}/scan/${token}`
}

// Get every label the user has made (needs a token with the read:games scope).
export async function fetchLabels(accessToken: string | undefined): Promise<QrLabel[]>
{
    const token = requireToken(accessToken)
    const response = await fetch(`${apiBaseUrl}/api/labels`, {
        headers: { Authorization: `Bearer ${token}` },
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    return await response.json() as QrLabel[]
}

// Make a brand new label for one game (needs a token with the write:games scope).
export async function createLabel(gameId: string, accessToken: string | undefined): Promise<QrLabel>
{
    const token = requireToken(accessToken)
    const response = await fetch(`${apiBaseUrl}/api/games/${gameId}/labels`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }

    return await response.json() as QrLabel
}

// Stop a label from working (needs write:games). The printed sticker will no longer scan to the game.
export async function revokeLabel(gameId: string, labelId: string, accessToken: string | undefined): Promise<void>
{
    const token = requireToken(accessToken)
    const response = await fetch(`${apiBaseUrl}/api/games/${gameId}/labels/${labelId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
    })

    if (!response.ok)
    {
        throw new Error(await responseError(response))
    }
}
// Find the label that is still working for a game, or null if the game has none yet.
export function findActiveLabel(labels: QrLabel[], gameId: string): QrLabel | null
{
    for (const label of labels)
    {
        if (label.gameId === gameId && !label.revoked)
        {
            return label
        }
    }

    return null
}
