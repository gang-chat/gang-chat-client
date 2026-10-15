import 'package:flutter_test/flutter_test.dart';
import 'package:client/src/app/fnv_hash.dart';
import 'package:client/src/app/music_track_preview.dart';

void main() {
  // Expected values come from the earlier native `int` implementations, so a
  // change here would orphan every cached file on disk.
  test('fnv1a64 matches the 64-bit FNV-1a reference values', () {
    expect(fnv1a64('').toRadixString(16), 'cbf29ce484222325');
    expect(fnv1a64('foobar').toRadixString(16), '85944171f73967e8');
  });

  test('signed hashes render like the native int implementation', () {
    final expected = {
      '': '-340d631b7bdddcdb',
      'a': '-509c23b379fe1374',
      'foobar': '-7a6bbe8e08c69818',
      'https://ky-z.com/gang-chat/uploads/avatars/u1.png': '1760ab0d0fbe3141',
      'https://os.ky-z.com:9000/gang-chat/files/表情.gif?v=2':
          '-435febba40bcd2c2',
    };
    for (final entry in expected.entries) {
      expect(
        fnv1a64Signed(entry.key).toRadixString(16).padLeft(16, '0'),
        entry.value,
        reason: entry.key,
      );
    }
  });

  test('music preview cache keys are unchanged', () {
    expect(
      musicTrackPreviewCacheKey('netease', '1901371647'),
      '5335af98ca736eff',
    );
    expect(
      musicTrackPreviewCacheKey('bilibili', 'BV1xx411c7mD'),
      '70491d7ea7d1c24f',
    );
    expect(musicTrackPreviewCacheKey('', ''), '2f63bd4c8601b7df');
    expect(musicTrackPreviewCacheKey('tencent', '歌曲 id'), '604c8e206fcf16c9');
  });
}
