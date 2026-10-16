/**
 * Converts server and transport error copy into a Simplified-Chinese message
 * suitable for direct display. Error codes remain unchanged for branching.
 */
export function localizedServerErrorMessage(opts: { code: string; statusCode: number; message: string }): string {
  const { code, statusCode, message } = opts;
  const trimmed = message.trim();
  if (_containsChinese(trimmed)) return trimmed;

  const normalized = trimmed.toLowerCase();
  const specific = _localizedKnownMessage(normalized);
  if (specific != null) return specific;

  switch (code.trim().toLowerCase()) {
    case 'unauthorized':
    case 'invalid_credentials':
      return '登录状态无效，请重新登录';
    case 'forbidden':
      return '没有权限执行此操作';
    case 'not_found':
      return '请求的内容不存在';
    case 'conflict':
    case 'idempotency_conflict':
      return '当前操作与已有状态冲突';
    case 'blocked':
      return '当前用户已被该房间屏蔽';
    case 'rate_limited':
      return '操作过于频繁，请稍后重试';
    case 'bad_request':
    case 'validation_failed':
      return '请求内容不符合要求';
    case 'confirmation_required':
      return '需要确认后才能继续';
    case 'payload_too_large':
      return '上传的文件过大';
    case 'email_unavailable':
      return '邮件发送服务暂时不可用';
    case 'email_send_failed':
      return '验证码邮件发送失败，请稍后重试';
    case 'email_verification_required':
      return '请先验证邮箱';
    case 'password_reset_verification_required':
      return '请先验证绑定邮箱';
    case 'account_not_found':
      return '该用户名或邮箱对应的账号不存在';
    case 'account_suspended':
      return '账号已被封禁';
    case 'verification_expired':
    case 'challenge_not_found':
      return '验证码已失效，请重新获取';
    case 'invalid_verification_code':
      return '验证码错误';
    case 'livekit_error':
      return '语音服务暂时无法完成操作';
    case 'livekit_unavailable':
      return '语音服务暂时不可用';
    case 'screen_share_not_active':
      return '屏幕共享已结束';
    case 'music_box_unavailable':
      return '音乐盒服务暂时不可用';
    case 'music_box_revision_conflict':
      return '音乐盒状态已变化，请重试';
    case 'music_box_item_not_ready':
      return '歌曲尚未准备完成';
    case 'music_box_item_already_queued':
      return '已在队列中';
    case 'music_box_queue_limit_reached':
      return '点歌队列已达 200 首上限';
    case 'music_box_permission_denied':
      return '没有权限执行此音乐盒操作';
    case 'playlist_limit_reached':
      return '个人歌单数量已达到上限';
    case 'playlist_item_limit_reached':
      return '该歌单的歌曲数量已达到上限';
    case 'playlist_item_already_exists':
      return '已在歌单内，不可重复添加';
    case 'playlist_name_conflict':
      return '已存在同名歌单';
    case 'playlist_order_conflict':
      return '歌单顺序已变化，请刷新后重试';
    case 'upstream_error':
      return '上游服务暂时不可用';
    case 'storage_unavailable':
      return '文件存储服务暂时不可用';
    case 'stream_unavailable':
      return '实时连接服务暂时不可用';
    case 'sticker_asset_expired':
      return '原表情文件已失效';
    case 'internal_error':
      return '服务器暂时无法完成请求，请稍后重试';
    case 'request_failed':
      return `请求失败（状态码 ${statusCode}）`;
    default:
      return _statusFallback(statusCode);
  }
}

function _localizedKnownMessage(message: string): string | null {
  if (message.length === 0) return null;
  if (message.includes('invalid credentials')) return '账号或密码不正确';
  if (message.includes('too many failed login attempts')) {
    return '登录尝试次数过多，请稍后再试';
  }
  if (message.includes('session revoked')) return '登录会话已被撤销';
  if (message.includes('session expired')) return '登录会话已过期';
  if (message.includes('session invalid')) return '登录会话无效，请重新登录';
  if (
    message.includes('invalid refresh token') ||
    message.includes('invalid token') ||
    message.includes('missing authorization')
  ) {
    return '登录状态无效，请重新登录';
  }
  if (message.includes('user inactive')) return '账号当前不可用';
  if (message.includes('account suspended')) return '账号已被封禁';
  if (message.includes('no password set')) return '当前账号尚未设置密码';
  if (message.includes('current password incorrect')) return '当前密码不正确';
  if (message.includes('username can be changed once per 24 hours')) {
    return '登录用户名每 24 小时只能修改一次';
  }
  if (message.includes('username or email already taken')) {
    return '登录用户名或邮箱已被占用';
  }
  if (message.includes('username, email or phone number already taken')) {
    return '登录用户名、邮箱或手机号已被占用';
  }
  if (message.includes('room not found')) return '房间不存在';
  if (message.includes('message not found') || message.includes('message does not exist')) {
    return '消息不存在';
  }
  if (message.includes('quoted message is unavailable')) {
    return '被引用的消息不可用';
  }
  if (message.includes('sticker file not found')) return '表情文件不存在';
  if (message.includes('sticker not found')) return '表情不存在';
  if (message.includes('sticker pack not found')) return '表情包不存在';
  if (message.includes('asset not found') || message.includes('file not found')) {
    return '文件不存在';
  }
  if (message.includes('member not found')) return '房间成员不存在';
  if (message.includes('user not found')) return '用户不存在';
  if (message.includes('session not found')) return '登录会话不存在';
  if (message.includes('join request not found')) return '加入申请不存在';
  if (message.includes('room notification not found')) return '通知不存在';
  if (message.includes('admin required')) return '需要管理员权限';
  if (message.includes('owner required')) return '需要房主权限';
  if (message.includes('super user required')) return '需要超级用户权限';
  if (message.includes('cannot manage super user')) return '不能管理超级用户';
  if (message.includes('cannot watch your own screen')) return '不能观看自己的屏幕共享';
  if (message.includes('screen share is not active')) return '屏幕共享已结束';
  if (message.includes('user is muted in this room')) return '您已在该房间被禁言';
  if (message.includes('user is blocked from this room')) return '您已被该房间屏蔽';
  if (message.includes('uploaded file is too large')) return '上传的文件过大';
  if (message.includes('image file is required')) return '请选择图片文件';
  if (message.includes('file is required')) return '请选择文件';
  if (message.includes('invalid json body') || message.includes('invalid request body')) {
    return '请求内容格式错误';
  }
  return null;
}

function _statusFallback(statusCode: number): string {
  switch (statusCode) {
    case 400:
      return '请求内容不符合要求';
    case 401:
      return '登录状态无效，请重新登录';
    case 403:
      return '没有权限执行此操作';
    case 404:
      return '请求的内容不存在';
    case 409:
      return '当前操作与已有状态冲突';
    case 413:
      return '上传的文件过大';
    case 429:
      return '操作过于频繁，请稍后重试';
  }
  if (statusCode >= 500) return '服务器暂时无法完成请求，请稍后重试';
  return `请求失败（状态码 ${statusCode}）`;
}

function _containsChinese(value: string): boolean {
  return /[㐀-鿿]/.test(value);
}
