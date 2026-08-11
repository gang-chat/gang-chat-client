import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/protocol/error_messages.dart';

void main() {
  test('keeps existing Chinese server messages', () {
    expect(
      localizedServerErrorMessage(
        code: 'email_verification_required',
        statusCode: 400,
        message: '请先验证邮箱',
      ),
      '请先验证邮箱',
    );
  });

  test('localizes known English server messages', () {
    expect(
      localizedServerErrorMessage(
        code: 'not_found',
        statusCode: 404,
        message: 'sticker not found',
      ),
      '表情不存在',
    );
    expect(
      localizedServerErrorMessage(
        code: 'unauthorized',
        statusCode: 401,
        message: 'session expired',
      ),
      '登录会话已过期',
    );
  });

  test('uses Chinese code and status fallbacks for unknown messages', () {
    expect(
      localizedServerErrorMessage(
        code: 'validation_failed',
        statusCode: 400,
        message: 'field xyz is malformed',
      ),
      '请求内容不符合要求',
    );
    expect(
      localizedServerErrorMessage(
        code: 'unknown',
        statusCode: 503,
        message: 'upstream exploded',
      ),
      '服务器暂时无法完成请求，请稍后重试',
    );
  });

  test('reports a suspended account instead of a credential error', () {
    expect(
      localizedServerErrorMessage(
        code: 'account_suspended',
        statusCode: 403,
        message: 'account suspended',
      ),
      '账号已被封禁',
    );
  });

  test('localizes duplicate music queue and playlist conflicts', () {
    expect(
      localizedServerErrorMessage(
        code: 'music_box_item_already_queued',
        statusCode: 409,
        message: 'music box item is already queued',
      ),
      '已在队列中',
    );
    expect(
      localizedServerErrorMessage(
        code: 'music_box_queue_limit_reached',
        statusCode: 409,
        message: 'music box request queue reached its 200 item limit',
      ),
      '点歌队列已达 200 首上限',
    );
    expect(
      localizedServerErrorMessage(
        code: 'music_box_permission_denied',
        statusCode: 403,
        message: 'the current room role cannot perform this music box action',
      ),
      '没有权限执行此音乐盒操作',
    );
    expect(
      localizedServerErrorMessage(
        code: 'playlist_item_already_exists',
        statusCode: 409,
        message: 'music playlist item already exists',
      ),
      '已在歌单内，不可重复添加',
    );
  });
}
