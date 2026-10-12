// Save an SVG QR code as a high-resolution JPEG with a white background.
export async function downloadQrCodeAsJpeg(svg: SVGSVGElement, gameName: string): Promise<void>
{
    const svgText = new XMLSerializer().serializeToString(svg)
    const svgBlob = new Blob([svgText], { type: 'image/svg+xml;charset=utf-8' })
    const svgUrl = URL.createObjectURL(svgBlob)
    const image = new Image()

    try
    {
        await new Promise<void>((resolve, reject) =>
        {
            image.onload = () => resolve()
            image.onerror = () => reject(new Error('Could not prepare the QR code image.'))
            image.src = svgUrl
        })

        const size = 600
        const canvas = document.createElement('canvas')
        canvas.width = size
        canvas.height = size

        const context = canvas.getContext('2d')
        if (!context)
        {
            throw new Error('This browser cannot prepare the QR code image.')
        }

        context.fillStyle = '#ffffff'
        context.fillRect(0, 0, size, size)
        context.drawImage(image, 0, 0, size, size)

        const jpeg = await new Promise<Blob>((resolve, reject) =>
        {
            canvas.toBlob(
                blob => (blob ? resolve(blob) : reject(new Error('Could not create a JPEG image.'))),
                'image/jpeg',
                0.98,
            )
        })

        const downloadUrl = URL.createObjectURL(jpeg)
        const link = document.createElement('a')
        const safeName = gameName.trim().replace(/[^a-zA-Z0-9_-]+/g, '_').replace(/^_+|_+$/g, '')
        link.href = downloadUrl
        link.download = `${safeName || 'BoardGame'}QR.jpg`
        link.click()
        URL.revokeObjectURL(downloadUrl)
    }
    finally
    {
        URL.revokeObjectURL(svgUrl)
    }
}
