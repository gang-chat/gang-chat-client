/// Converts server and transport error copy into a Simplified-Chinese message
/// suitable for direct display. Error codes remain unchanged for branching.
String localizedServerErrorMessage({
  required String code,
  required int statusCode,
  required String message,
}) {
  final trimmed = message.trim();
  if (_containsChinese(trimmed)) return trimmed;

  final normalized = trimmed.toLowerCase();
  final specific = _localizedKnownMessage(normalized);
  if (specific != null) return specific;

  return switch (code.trim().toLowerCase()) {
    'unauthorized' || 'invalid_credentials' => '登录状态无效，请重新登录',
    'forbidden' => '没有权限执行此操作',
    'not_found' => '请求的内容不存在',
    'conflict' || 'idempotency_conflict' => '当前操作与已有状态冲突',
    'blocked' => '当前用户已被该房间屏蔽',
    'rate_limited' => '操作过于频繁，请稍后重试',
    'bad_request' || 'validation_failed' => '请求内容不符合要求',
    'confirmation_required' => '需要确认后才能继续',
    'payload_too_large' => '上传的文件过大',
    'email_unavailable' => '邮件发送服务暂时不可用',
    'email_send_failed' => '验证码邮件发送失败，请稍后重试',
    'email_verification_required' => '请先验证邮箱',
    'password_reset_verification_required' => '请先验证绑定邮箱',
    'account_not_found' => '该用户名或邮箱对应的账号不存在',
    'account_suspended' => '账号已被封禁',
    'verification_expired' || 'challenge_not_found' => '验证码已失效，请重新获取',
    'invalid_verification_code' => '验证码错误',
    'livekit_error' => '语音服务暂时无法完成操作',
    'livekit_unavailable' => '语音服务暂时不可用',
    'screen_share_not_active' => '屏幕共享已结束',
    'music_box_unavailable' => '音乐盒服务暂时不可用',
    'playlist_limit_reached' => '个人歌单数量已达到上限',
    'playlist_item_limit_reached' => '该歌单的歌曲数量已达到上限',
    'playlist_name_conflict' => '已存在同名歌单',
    'playlist_order_conflict' => '歌单顺序已变化，请刷新后重试',
    'upstream_error' => '上游服务暂时不可用',
    'storage_unavailable' => '文件存储服务暂时不可用',
    'stream_unavailable' => '实时连接服务暂时不可用',
    'sticker_asset_expired' => '原表情文件已失效',
    'internal_error' => '服务器暂时无法完成请求，请稍后重试',
    'request_failed' => '请求失败（状态码 $statusCode）',
    _ => _statusFallback(statusCode),
  };
}

String? _localizedKnownMessage(String message) {
  if (message.isEmpty) return null;
  if (message.contains('invalid credentials')) return '账号或密码不正确';
  if (message.contains('too many failed login attempts')) {
    return '登录尝试次数过多，请稍后再试';
  }
  if (message.contains('session revoked')) return '登录会话已被撤销';
  if (message.contains('session expired')) return '登录会话已过期';
  if (message.contains('session invalid')) return '登录会话无效，请重新登录';
  if (message.contains('invalid refresh token') ||
      message.contains('invalid token') ||
      message.contains('missing authorization')) {
    return '登录状态无效，请重新登录';
  }
  if (message.contains('user inactive')) return '账号当前不可用';
  if (message.contains('account suspended')) return '账号已被封禁';
  if (message.contains('no password set')) return '当前账号尚未设置密码';
  if (message.contains('current password incorrect')) return '当前密码不正确';
  if (message.contains('username can be changed once per 24 hours')) {
    return '登录用户名每 24 小时只能修改一次';
  }
  if (message.contains('username or email already taken')) {
    return '登录用户名或邮箱已被占用';
  }
  if (message.contains('username, email or phone number already taken')) {
    return '登录用户名、邮箱或手机号已被占用';
  }
  if (message.contains('room not found')) return '房间不存在';
  if (message.contains('message not found') ||
      message.contains('message does not exist')) {
    return '消息不存在';
  }
  if (message.contains('quoted message is unavailable')) {
    return '被引用的消息不可用';
  }
  if (message.contains('sticker file not found')) return '表情文件不存在';
  if (message.contains('sticker not found')) return '表情不存在';
  if (message.contains('sticker pack not found')) return '表情包不存在';
  if (message.contains('asset not found') ||
      message.contains('file not found')) {
    return '文件不存在';
  }
  if (message.contains('member not found')) return '房间成员不存在';
  if (message.contains('user not found')) return '用户不存在';
  if (message.contains('session not found')) return '登录会话不存在';
  if (message.contains('join request not found')) return '加入申请不存在';
  if (message.contains('room notification not found')) return '通知不存在';
  if (message.contains('admin required')) return '需要管理员权限';
  if (message.contains('owner required')) return '需要房主权限';
  if (message.contains('super user required')) return '需要超级用户权限';
  if (message.contains('cannot manage super user')) return '不能管理超级用户';
  if (message.contains('cannot watch your own screen')) return '不能观看自己的屏幕共享';
  if (message.contains('screen share is not active')) return '屏幕共享已结束';
  if (message.contains('user is muted in this room')) return '您已在该房间被禁言';
  if (message.contains('user is blocked from this room')) return '您已被该房间屏蔽';
  if (message.contains('uploaded file is too large')) return '上传的文件过大';
  if (message.contains('image file is required')) return '请选择图片文件';
  if (message.contains('file is required')) return '请选择文件';
  if (message.contains('invalid json body') ||
      message.contains('invalid request body')) {
    return '请求内容格式错误';
  }
  return null;
}

String _statusFallback(int statusCode) {
  return switch (statusCode) {
    400 => '请求内容不符合要求',
    401 => '登录状态无效，请重新登录',
    403 => '没有权限执行此操作',
    404 => '请求的内容不存在',
    409 => '当前操作与已有状态冲突',
    413 => '上传的文件过大',
    429 => '操作过于频繁，请稍后重试',
    >= 500 => '服务器暂时无法完成请求，请稍后重试',
    _ => '请求失败（状态码 $statusCode）',
  };
}

bool _containsChinese(String value) {
  return RegExp(r'[\u3400-\u9fff]').hasMatch(value);
}
