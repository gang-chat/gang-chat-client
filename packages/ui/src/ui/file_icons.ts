import { Icons, type IconData } from './icon';

export function fileIconForMime(mimeType: string | null | undefined): IconData {
  const value = (mimeType ?? '').toLowerCase();
  if (value.startsWith('image/')) return Icons.image_outlined;
  if (value === 'application/pdf') return Icons.picture_as_pdf_outlined;
  if (value.startsWith('audio/')) return Icons.audio_file_outlined;
  if (value.startsWith('video/')) return Icons.video_file_outlined;
  if (value.includes('zip') || value.includes('tar') || value.includes('compressed')) {
    return Icons.folder_zip_outlined;
  }
  if (value.startsWith('text/') || value.includes('json')) {
    return Icons.description_outlined;
  }
  return Icons.insert_drive_file_outlined;
}
