// Shrink a picture before saving. Phone photos are often 4-12 MB, but a cover or background
// never needs more than about 1-2 thousand pixels, so this keeps uploads fast and under the 5 MB limit.
export async function resizeImage(file: File, maxSize: number): Promise<Blob>
{
    // Decode the chosen file into pixels (this also respects the way a phone was held when it took the photo).
    const bitmap = await createImageBitmap(file)

    // Work out the new size, keeping the same proportions and never making the picture bigger.
    const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height))
    const width = Math.round(bitmap.width * scale)
    const height = Math.round(bitmap.height * scale)

    // Draw the picture onto a hidden canvas at the new size.
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context)
    {
        throw new Error('This browser cannot prepare the picture for upload.')
    }

    // Paint white first, because JPEG has no transparency and see-through parts would turn black.
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, width, height)
    context.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()

    // Turn the canvas back into a compressed JPEG file.
    return new Promise((resolve, reject) =>
    {
        canvas.toBlob(
            blob => (blob ? resolve(blob) : reject(new Error('Could not prepare the picture for upload.'))),
            'image/jpeg',
            0.85,
        )
    })
}

// Turn a picture into text ("data:image/jpeg;base64,...") so it can travel inside game or collection JSON
// and be used directly as an <img> source.
export function blobToDataUrl(image: Blob): Promise<string>
{
    return new Promise((resolve, reject) =>
    {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.onerror = () => reject(new Error('Could not read the picture.'))
        reader.readAsDataURL(image)
    })
}
