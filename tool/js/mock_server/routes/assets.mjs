// Uploads (multipart `file` + `purpose`, kept in memory) and the public
// `/mock-assets/<id>[/<filename>]` URLs every asset is served from (with
// Range support so audio/video can seek).
import { HANDLED, badRequest, contentDisposition, created, notFound, parseRange } from '../lib/http.mjs';
import { imageSize, resolveMimeType } from '../lib/media.mjs';
import { route } from '../lib/router.mjs';

function uploadedFile(c, { imagesOnly }) {
  const file = c.multipart?.files.find((part) => part.field === 'file') ?? c.multipart?.files[0];
  if (!file || file.data.length === 0) throw badRequest(imagesOnly ? 'image file is required' : 'file is required');
  const mimeType = resolveMimeType({ declared: file.contentType, filename: file.filename, data: file.data });
  if (imagesOnly && !mimeType.startsWith('image/')) throw badRequest('image file is required');
  const asset = c.store.addAsset({
    mimeType,
    filename: file.filename || 'upload',
    bytes: Buffer.from(file.data),
    ownerUserId: c.user.id,
    purpose: c.multipart.fields.purpose ?? null,
    ...(imageSize(file.data, mimeType) ?? {}),
  });
  c.app.log(`[upload] ${asset.id} ${asset.filename} (${mimeType}, ${asset.sizeBytes} bytes, purpose=${asset.purpose})`);
  return created({ asset: c.store.assetJson(asset) });
}

/** GET /mock-assets/:assetId — serves stored bytes; external assets redirect. */
function serveAsset(c) {
  const asset = c.store.assets.get(c.params.assetId);
  if (!asset) throw notFound('asset not found');
  const { req, res } = c;
  if (asset.externalUrl) {
    res.writeHead(302, { location: asset.externalUrl });
    res.end();
    return HANDLED;
  }
  const bytes = asset.bytes ?? Buffer.alloc(0);
  const headers = {
    'content-type': asset.mimeType,
    'accept-ranges': 'bytes',
    'cache-control': 'public, max-age=3600',
    'content-disposition': contentDisposition('inline', asset.filename ?? asset.id),
  };
  const range = parseRange(req.headers.range, bytes.length);
  if (range?.unsatisfiable) {
    res.writeHead(416, { ...headers, 'content-range': `bytes */${bytes.length}` });
    res.end();
    return HANDLED;
  }
  const body = range ? bytes.subarray(range.start, range.end + 1) : bytes;
  if (range) headers['content-range'] = `bytes ${range.start}-${range.end}/${bytes.length}`;
  res.writeHead(range ? 206 : 200, { ...headers, 'content-length': body.length });
  res.end(req.method === 'HEAD' ? undefined : body);
  return HANDLED;
}

export const uploadRoutes = [
  route('POST', '/uploads/images', (c) => uploadedFile(c, { imagesOnly: true })),
  route('POST', '/uploads/files', (c) => uploadedFile(c, { imagesOnly: false })),
];

/** Mounted at the server root, not under /api/v1. */
export const assetRoutes = [
  route('GET', '/mock-assets/:assetId', serveAsset, { public: true }),
  route('GET', '/mock-assets/:assetId/:filename', serveAsset, { public: true }),
];
