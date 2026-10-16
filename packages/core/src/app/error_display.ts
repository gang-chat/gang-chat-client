/**
 * Returns Simplified-Chinese copy suitable for a floating notice or error
 * panel without exposing platform exception class names and English details.
 */
export function userFacingErrorMessage(failure: unknown, opts: { fallback?: string } = {}): string {
  const { fallback = '操作失败，请稍后重试' } = opts;
  const raw = String(failure).trim();
  if (_containsChinese(raw)) return raw;

  const normalized = raw.toLowerCase();
  if (normalized.includes('cancelled') || normalized.includes('canceled')) {
    return '操作已取消';
  }
  if (normalized.includes('timeout') || normalized.includes('timed out')) {
    return '操作超时，请稍后重试';
  }
  if (
    normalized.includes('socket') ||
    normalized.includes('connection') ||
    normalized.includes('network') ||
    normalized.includes('handshake')
  ) {
    return '网络连接失败，请检查网络后重试';
  }
  if (normalized.includes('permission') || normalized.includes('access denied')) {
    return '没有权限完成此操作';
  }
  if (normalized.includes('not found') || normalized.includes('missing')) {
    return '所需内容不存在';
  }
  if (normalized.includes('unsupported') || normalized.includes('not supported')) {
    return '当前平台不支持此操作';
  }
  return fallback;
}

function _containsChinese(value: string): boolean {
  return /[㐀-鿿]/.test(value);
}
