import { QRCodeSVG } from 'qrcode.react'
import { buildScanUrl } from '../api/labelApi'

type BoardGameQrLabelProps = {
    name: string
    edition: string | null
    token: string
}

// The sticker itself: a QR code with the game name (and edition) underneath.
function BoardGameQrLabel({ name, edition, token }: BoardGameQrLabelProps)
{
    return (
        <div className="board-game-qr-label inline-flex w-56 flex-col items-center gap-2 rounded border border-neutral-400 bg-white p-4 text-center text-black">
            <QRCodeSVG value={buildScanUrl(token)} size={160} level="M" bgColor="#ffffff" fgColor="#000000" />
            <p className="text-sm font-bold leading-tight">{name}</p>
            {edition && <p className="text-xs leading-tight">{edition}</p>}
        </div>
    )
}

export default BoardGameQrLabel
