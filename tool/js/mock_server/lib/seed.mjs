// Seeded demo world: users, rooms, ~60 messages per room covering every
// message kind the client renders, live participants, a playing music box,
// stickers, playlists, invites, join requests and notifications.
//
// Generation is deterministic (fixed PRNG seed); timestamps are relative to
// server start so the history always looks recent.
import { systemMessageSpec } from './events.mjs';
import { avatarPng, buildPdf, buildZip, encodeWav, photoPng, stickerPng } from './media.mjs';
import { TRACK_CATALOG, createMusicBox, queueItem } from './music_box.mjs';
import { createPlaylist, sharedPlaylistJson } from './playlists.mjs';
import { DAY, HOUR, MINUTE, SECOND, createRng, nextId, pick, randomInt } from './util.mjs';

export const DEMO_USER_ID = 'usr_demo';
export const BOT_USER_ID = 'usr_gangbot';
/** The room that receives the periodic simulated incoming messages. */
export const FIRST_ROOM_ID = 'room_hq';
/** Rooms a newly registered / email-login user is dropped into so the UI is not empty. */
const ONBOARDING_ROOM_IDS = ['room_hq', 'room_music', 'room_games', 'room_random'];

const USERS = [
  { id: 'usr_demo', username: 'demo', displayName: 'Demo', email: 'demo@example.com', emailVerified: true, phoneNumber: '13800000000', bio: '演示账号 · 用来测试 Gang Chat 桌面端', defaultAvatarKey: 'blue-3', online: true },
  { id: 'usr_alice', username: 'alice', displayName: 'Alice', email: 'alice@example.com', emailPublic: true, bio: '前端 / Electron 移植负责人', gender: 'female', defaultAvatarKey: 'pink-2', avatarColor: '#ec4899', online: true },
  { id: 'usr_bob', username: 'bob', displayName: '老鲍', email: 'bob@example.com', bio: '后端 & 运维，偶尔打游戏', gender: 'male', defaultAvatarKey: 'amber-2', online: true },
  { id: 'usr_carol', username: 'carol', displayName: 'Carol', email: 'carol@example.com', bio: 'UI 设计师', gender: 'female', defaultAvatarKey: 'violet-2', avatarColor: '#8b5cf6', online: true },
  { id: 'usr_dave', username: 'dave', displayName: '大卫', email: 'dave@example.com', bio: '周末开黑组织者', gender: 'male', defaultAvatarKey: 'green-2', online: true },
  { id: 'usr_erin', username: 'erin', displayName: 'Erin', email: 'erin@example.com', bio: 'QA · 专门找 bug', defaultAvatarKey: 'teal-2' },
  { id: 'usr_frank', username: 'frank', displayName: '阿方', email: 'frank@example.com', defaultAvatarKey: 'orange-2' },
  { id: 'usr_grace', username: 'grace', displayName: 'Grace', email: 'grace@example.com', bio: '电台 DJ 🎧', gender: 'female', defaultAvatarKey: 'indigo-2', avatarColor: '#6366f1', online: true },
  { id: 'usr_heidi', username: 'heidi', displayName: '海蒂', email: 'heidi@example.com', bio: '摄影 / 旅行', defaultAvatarKey: 'rose-2' },
  { id: 'usr_ivan', username: 'ivan', displayName: 'Ivan', email: 'ivan@example.com', bio: 'Flutter 爱好者', defaultAvatarKey: 'navy-2' },
  { id: 'usr_judy', username: 'judy', displayName: '朱迪', email: 'judy@example.com', bio: '新来的', defaultAvatarKey: 'mint-2' },
  { id: 'usr_mallory', username: 'mallory', displayName: 'Mallory', email: 'mallory@example.com', bio: '广告位招租', defaultAvatarKey: 'red-2' },
  { id: 'usr_admin', username: 'admin', displayName: '超级管理员', email: 'admin@example.com', emailVerified: true, isSuperuser: true, bio: '拥有全站管理权限的演示账号', defaultAvatarKey: 'crimson-2' },
  { id: BOT_USER_ID, username: 'gang-bot', displayName: 'gang-bot', isBot: true, defaultAvatarKey: 'cyan-2', online: true, password: null },
];

const ALL_HUMANS = USERS.filter((user) => !user.isBot && user.id !== 'usr_mallory').map((user) => user.id);

const ROOMS = [
  {
    id: 'room_hq', name: 'Gang 总部', description: 'Gang Chat 演示总部：公告、闲聊和语音都在这里。',
    visibility: 'public', joinPolicy: 'approval_required', defaultAvatarKey: 'blue-3',
    owner: 'usr_demo', admins: ['usr_alice'],
    members: ['usr_bob', 'usr_carol', 'usr_dave', 'usr_erin', 'usr_frank', 'usr_grace', 'usr_heidi', 'usr_admin'],
    nicknames: { usr_bob: '鲍哥' }, demoSettings: { isPinned: true },
    topic: 'hq', messageCount: 60, recallWindowSeconds: 120, stickerPack: 'pack_room_hq',
    live: [
      ['usr_alice', { joinedMinutesAgo: 42 }],
      ['usr_bob', { joinedMinutesAgo: 35, micMuted: true }],
      ['usr_carol', { joinedMinutesAgo: 12, screenSharing: true, screenViewerIds: ['usr_alice'] }],
    ],
  },
  {
    id: 'room_dev', name: '开发频道', description: 'Electron 移植进度、代码评审与 CI 状态。',
    visibility: 'private', joinPolicy: 'approval_required', defaultAvatarKey: 'emerald-2',
    owner: 'usr_alice', admins: ['usr_demo'], members: ['usr_bob', 'usr_carol', 'usr_ivan', 'usr_erin'],
    topic: 'dev', messageCount: 60, demoUnread: 7, mentionDemoInUnread: true,
  },
  {
    id: 'room_music', name: '音乐电台', description: '7×24 小时点歌台，来一首？',
    visibility: 'public', joinPolicy: 'open', defaultAvatarKey: 'amber-2', avatarColors: ['#f59e0b', '#ef4444'],
    owner: 'usr_grace', admins: [], members: ['usr_demo', 'usr_alice', 'usr_bob', 'usr_heidi'],
    topic: 'music', messageCount: 50, musicPlaying: true,
  },
  {
    id: 'room_games', name: '游戏开黑', description: '今晚几点上线？',
    visibility: 'public', joinPolicy: 'open', defaultAvatarKey: 'lime-2',
    owner: 'usr_dave', admins: [], members: ['usr_demo', 'usr_bob', 'usr_erin', 'usr_frank', 'usr_ivan'],
    nicknames: { usr_dave: '队长' }, topic: 'games', messageCount: 60, demoUnread: 2,
    live: [
      ['usr_dave', { joinedMinutesAgo: 25, cameraOn: true }],
      ['usr_erin', { joinedMinutesAgo: 8, micMuted: true, headphonesMuted: true }],
    ],
  },
  {
    id: 'room_design', name: '设计评审', description: '设计稿、交互走查与视觉规范。',
    visibility: 'private', joinPolicy: 'approval_required', defaultAvatarKey: 'violet-2',
    owner: 'usr_carol', admins: [], members: ['usr_demo', 'usr_alice', 'usr_grace'],
    demoSettings: { notificationPolicy: 'silent', remarkName: '设计组' },
    topic: 'design', messageCount: 45, recallPolicy: 'admin_approval',
  },
  {
    id: 'room_random', name: '闲聊水群', description: '什么都可以聊，但别刷屏。',
    visibility: 'public', joinPolicy: 'open', defaultAvatarKey: 'orange-2',
    owner: 'usr_bob', admins: ['usr_demo'], members: ALL_HUMANS.filter((id) => id !== 'usr_bob' && id !== 'usr_demo'),
    topic: 'random', messageCount: 60, demoUnread: 3, stickerPack: 'pack_room_random',
  },
  // Rooms Demo has not joined: search results, an invite, an application.
  {
    id: 'room_flutter', name: 'Flutter 交流', description: 'Flutter / Dart 技术讨论，欢迎新人。',
    visibility: 'public', joinPolicy: 'open', defaultAvatarKey: 'sky-2',
    owner: 'usr_ivan', admins: [], members: ['usr_alice', 'usr_heidi', 'usr_judy'], topic: 'dev', messageCount: 20,
  },
  {
    id: 'room_photo', name: '摄影爱好者', description: '晒图、器材与后期交流。',
    visibility: 'public', joinPolicy: 'approval_required', defaultAvatarKey: 'rose-2',
    owner: 'usr_heidi', admins: [], members: ['usr_grace', 'usr_judy'], topic: 'photo', messageCount: 16,
  },
  {
    id: 'room_books', name: '读书会', description: '每月一本书，周日晚上线上讨论。',
    visibility: 'private', joinPolicy: 'approval_required', defaultAvatarKey: 'brown-2',
    owner: 'usr_alice', admins: [], members: ['usr_carol', 'usr_erin'], topic: 'books', messageCount: 14,
  },
  {
    id: 'room_archive', name: '旧项目组', description: '已归档，仅供查阅。',
    visibility: 'public', joinPolicy: 'closed', defaultAvatarKey: 'olive-2', gangBotEnabled: false,
    owner: 'usr_frank', admins: [], members: ['usr_erin', 'usr_ivan'], topic: 'random', messageCount: 10,
  },
];

const PHRASES = {
  hq: ['大家早！今天的站会改到 10:30', '新版安装包已经上传到内部分发了', '今晚 9 点总部语音有分享会，欢迎来听', 'Electron 版的登录流程我测过了，没问题', '记得更新一下个人资料里的头像', '下周开始轮值，排班表我晚点发', '谁把会议室的投屏线拿走了 😂', '这周的周报大家周五前交一下', '新同学欢迎多多关照', '服务器今晚 2 点维护，大概半小时'],
  dev: ['CI 又红了，看起来是 flaky test', 'Electron 主进程的 SSE 重连我加了指数退避', '这个 PR 需要两个 approve', 'macOS 的签名证书下个月到期，记得续', 'vitest 全绿 ✅', 'LiveKit 断线时 UI 应该展示错误态，我补了个提示', '房间列表的排序逻辑和 Flutter 版保持一致了', '有人能帮忙复现一下 Windows 上的托盘图标问题吗', '我把 api_client 的重试策略移植过来了', 'TypeScript 严格模式开起来了，修了 40 多处类型'],
  music: ['来一首周杰伦！', '这首好听，求歌名', '点了一首《平凡之路》', '音量是不是有点大', '电台今晚放到几点？', 'DJ 换个风格呗', '收藏了，加到我的歌单', '下一首我来点'],
  games: ['今晚 9 点开黑，来不来', '我先上线等你们', '这把太刺激了', '队长带带我', '谁开个房间', '我网卡了，等我重连', '周末打通宵吗', '新赛季的皮肤好看'],
  design: ['新版图标稿上传了，大家看看', '这个间距建议用 8 的倍数', '暗色模式下对比度不太够', '交互走查发现三处不一致', '按钮圆角统一成 8px 吧', '下周评审会改到周三', '插画风格我们再对齐一下'],
  random: ['中午吃什么？', '今天天气不错', '有人看昨晚的比赛吗', '摸鱼中……', '推荐一部电影吧', '周末去爬山有人一起吗', '咖啡机又坏了', '这个表情包笑死我了'],
  photo: ['今天拍的日落，分享一下', '这个镜头值得入吗', '后期用的什么预设？', '下周末一起去扫街吧'],
  books: ['这个月读《三体》', '第二部比第一部精彩', '周日晚上 8 点线上讨论', '推荐一本轻松点的书吧'],
};
const SHORT_REPLIES = ['👍', '收到', '好的', '哈哈哈哈', '+1', '晚点看', '有道理', 'OK', '稍等我一下', '可以的', '666', '辛苦了'];
const LINK_LINES = [
  'PR 在这里：https://github.com/gang-chat/gang-chat-client/pull/1287',
  '文档参考 https://www.electronjs.org/docs/latest/tutorial/process-model',
  '这篇不错 https://flutter.dev/multi-platform/desktop',
  '占位图可以用 https://picsum.photos/ ，很方便',
];
const LONG_MESSAGE = '今日发布清单：\n1. 修复房间列表未读数不同步\n2. 语音面板支持调节成员音量\n3. 设置页支持切换语言\n4. 优化大图预览的内存占用\n\n有问题直接在这里回复我。';

/** Lines for the periodic simulated incoming messages in FIRST_ROOM_ID. */
export const CHATTER_LINES = ['有人在吗？', '刚刚的构建过了 ✅', '今晚语音几点开始？', '我把新的设计稿发到群文件了', '这个 bug 复现了，我去提个 issue', '晚饭吃什么', '谁有空帮我 review 一下 PR', '测试环境刚刚重启了', '新版本体验不错 👍', '下午三点开会别忘了', '我先下线了，明天见', '音乐电台那首歌叫什么？'];

const PHOTO_PALETTES = [['#3b82f6', '#f472b6'], ['#10b981', '#3b82f6'], ['#f59e0b', '#ef4444'], ['#8b5cf6', '#06b6d4'], ['#0ea5e9', '#22c55e'], ['#f43f5e', '#f97316'], ['#6366f1', '#a855f7']];
const PHOTO_SIZES = [[480, 320], [320, 480], [400, 400], [560, 315]];
const STICKER_FACES = [
  ['开心', '#facc15', 'smile'], ['大笑', '#fb923c', 'grin'], ['震惊', '#38bdf8', 'wow'], ['无语', '#a1a1aa', 'meh'],
  ['眨眼', '#f472b6', 'wink'], ['好耶', '#4ade80', 'smile'], ['嘿嘿', '#c084fc', 'grin'], ['啊这', '#f87171', 'wow'],
];

/** Message kinds placed at fixed positions of each room's history; other slots are plain text. */
const SPECIAL_KINDS = {
  0: 'joined', 4: 'image', 6: 'mention_other', 8: 'link', 11: 'file', 12: 'ask_bot', 13: 'bot_reply', 14: 'voice',
  17: 'sticker', 20: 'quote', 23: 'mention_demo', 26: 'recalled', 29: 'image_external', 32: 'role_changed',
  35: 'music_track', 38: 'long', 41: 'file', 44: 'voice', 46: 'playlist', 47: 'live_joined', 50: 'mention_all',
  53: 'images', 56: 'quote',
};

/** Populates `store` with the demo world. Call once the public URL is known. */
export function seedStore(store) {
  const now = Date.now();
  const rng = createRng(20261016);
  const media = createMediaFactory(store, rng);

  for (const spec of USERS) {
    const { avatarColor, ...user } = spec;
    if (avatarColor) user.avatarAssetId = media.avatar(avatarColor).id;
    store.addUser({ ...user, seeded: true });
  }

  seedStickerPacks(store, media, now);
  seedPlaylists(store, now);

  for (const spec of ROOMS) {
    const room = store.addRoom({
      id: spec.id,
      name: spec.name,
      description: spec.description,
      visibility: spec.visibility,
      joinPolicy: spec.joinPolicy,
      defaultAvatarKey: spec.defaultAvatarKey,
      avatarAssetId: spec.avatarColors ? media.photo(spec.avatarColors, [256, 256]).id : null,
      createdBy: spec.owner,
      createdAt: now - randomInt(rng, 60, 200) * DAY,
      gangBotEnabled: spec.gangBotEnabled ?? true,
      messageRecallPolicy: spec.recallPolicy ?? 'sender_only',
      messageRecallWindowSeconds: spec.recallWindowSeconds ?? null,
    });
    const memberOrder = [spec.owner, ...spec.admins, ...spec.members.filter((id) => !spec.admins.includes(id))];
    memberOrder.forEach((userId, index) => {
      const role = userId === spec.owner ? 'owner' : spec.admins.includes(userId) ? 'admin' : 'member';
      store.addMember(room, userId, {
        role,
        joinedAt: room.createdAt + index * DAY,
        roomDisplayName: spec.nicknames?.[userId] ?? null,
        ...(userId === DEMO_USER_ID ? spec.demoSettings : {}),
      });
    });
    room.musicBox = createMusicBox();
    seedMessages(store, room, spec, { rng, media, now });
    seedLive(store, room, spec, now);
  }

  seedMusicBoxes(store, now);
  seedSocialGraph(store, now);
  store.memberVolumes.set(`room_hq:${DEMO_USER_ID}:usr_bob`, { volume: 60, updatedAt: now - DAY });
}

// ---------------------------------------------------------------------------
// Media

function createMediaFactory(store, rng) {
  let photoSeed = 0;
  const files = [
    () => ({ filename: 'Electron 迁移计划.pdf', mimeType: 'application/pdf', bytes: buildPdf(['Gang Chat - Electron migration plan', '', '1. Port protocol + auth (done)', '2. Rooms, chat, members', '3. Live channel via LiveKit', '4. Settings & notifications', '', 'Generated by the Gang Chat mock server.']) }),
    () => ({ filename: '发布说明-v1.0.0.md', mimeType: 'text/markdown', bytes: Buffer.from('# Gang Chat 1.0.0\n\n- 全新桌面端\n- 语音频道\n- 音乐盒\n') }),
    () => ({ filename: 'crash-log-2026-10-15.txt', mimeType: 'text/plain', bytes: Buffer.from(Array.from({ length: 40 }, (_, i) => `[2026-10-15 10:${String(i).padStart(2, '0')}:00] INFO mock log line ${i}`).join('\n')) }),
    () => ({ filename: '用户反馈汇总.csv', mimeType: 'text/csv', bytes: Buffer.from('日期,渠道,反馈\n2026-10-01,邮件,登录很快\n2026-10-02,群聊,希望支持暗色模式\n2026-10-03,应用内,语音偶尔断开\n') }),
    () => ({ filename: '设计资源.zip', mimeType: 'application/zip', bytes: buildZip([{ name: 'README.md', data: '# 设计资源\n' }, { name: 'colors.json', data: '{"primary":"#3b82f6"}\n' }]) }),
    () => ({ filename: 'config.sample.json', mimeType: 'application/json', bytes: Buffer.from(JSON.stringify({ api_base_url: 'http://127.0.0.1:21116/api/v1' }, null, 2)) }),
  ];
  let fileIndex = 0;
  return {
    photo(colors = PHOTO_PALETTES[photoSeed % PHOTO_PALETTES.length], size = PHOTO_SIZES[photoSeed % PHOTO_SIZES.length]) {
      photoSeed += 1;
      const seed = photoSeed;
      const [width, height] = size;
      return store.addAsset({
        mimeType: 'image/png',
        filename: `IMG_${String(2000 + seed)}.png`,
        width,
        height,
        generate: () => photoPng({ width, height, from: colors[0], to: colors[1], seed }),
      });
    },
    externalPhoto() {
      photoSeed += 1;
      const seed = photoSeed;
      // Generated locally so the mock works offline (sandboxes, CI); set
      // MOCK_EXTERNAL_IMAGES=1 to exercise a remote image host instead.
      if (process.env.MOCK_EXTERNAL_IMAGES === '1') {
        return store.addAsset({ mimeType: 'image/jpeg', filename: `picsum-${seed}.jpg`, width: 640, height: 427, sizeBytes: 48_213, externalUrl: `https://picsum.photos/seed/gang${seed}/640/427` });
      }
      return store.addAsset({
        mimeType: 'image/png',
        filename: `photo-${seed}.png`,
        width: 640,
        height: 427,
        generate: () => photoPng({ width: 640, height: 427, from: '#1F3B57', to: '#C46A2E', seed }),
      });
    },
    avatar(color) {
      return store.addAsset({ mimeType: 'image/png', filename: 'avatar.png', width: 128, height: 128, generate: () => avatarPng({ color }) });
    },
    sticker(color, mood) {
      return store.addAsset({ mimeType: 'image/png', filename: 'sticker.png', width: 160, height: 160, generate: () => stickerPng({ color, mood }) });
    },
    voice(durationMs, createdAt) {
      return store.addAsset({ mimeType: 'audio/wav', filename: `voice_${createdAt}.wav`, generate: () => encodeWav({ durationMs, pitch: randomInt(rng, 150, 260) }) });
    },
    file() {
      const spec = files[fileIndex++ % files.length]();
      return store.addAsset(spec);
    },
  };
}

// ---------------------------------------------------------------------------
// Stickers and playlists

function seedStickerPacks(store, media, now) {
  const addPack = (id, { scope, roomId = null, ownerUserId = null, name, sortOrder, faces }) => {
    const pack = { id, scope, roomId, ownerUserId, name, sortOrder, updatedAt: now - 3 * DAY, stickers: [] };
    faces.forEach(([stickerName, color, mood], index) => {
      pack.stickers.push({ id: nextId('stk'), name: stickerName, sortOrder: (index + 1) * 10, assetId: media.sticker(color, mood).id });
    });
    store.stickerPacks.set(id, pack);
  };
  addPack('pack_demo_main', { scope: 'personal', ownerUserId: DEMO_USER_ID, name: '常用表情', sortOrder: 10, faces: STICKER_FACES });
  addPack('pack_demo_cats', { scope: 'personal', ownerUserId: DEMO_USER_ID, name: '猫猫', sortOrder: 20, faces: [['猫猫点头', '#fbbf24', 'smile'], ['猫猫震惊', '#fda4af', 'wow'], ['猫猫睡觉', '#cbd5e1', 'meh'], ['猫猫比心', '#f9a8d4', 'wink']] });
  addPack('pack_alice', { scope: 'personal', ownerUserId: 'usr_alice', name: 'Alice 的表情', sortOrder: 10, faces: [['冲鸭', '#fb7185', 'grin'], ['好的', '#34d399', 'smile'], ['疑问', '#60a5fa', 'wow'], ['再见', '#a78bfa', 'wink']] });
  addPack('pack_room_hq', { scope: 'room', roomId: 'room_hq', name: '总部专属', sortOrder: 10, faces: [['收到', '#22c55e', 'smile'], ['加班中', '#64748b', 'meh'], ['下班！', '#f97316', 'grin']] });
  addPack('pack_room_random', { scope: 'room', roomId: 'room_random', name: '水群必备', sortOrder: 10, faces: [['吃瓜', '#84cc16', 'grin'], ['摸鱼', '#06b6d4', 'meh'], ['哈哈', '#eab308', 'grin']] });
}

const tracks = (...indexes) => indexes.map((index) => TRACK_CATALOG[index]);

function seedPlaylists(store, now) {
  const add = (ownerKey, name, items, daysAgo) => createPlaylist(store, ownerKey, { name, items, createdAt: now - daysAgo * DAY });
  add(`user:${DEMO_USER_ID}`, '通勤路上', tracks(0, 2, 3, 9, 14, 17), 30);
  add(`user:${DEMO_USER_ID}`, '写代码 BGM', tracks(19, 20, 12, 10, 21), 20);
  add(`user:${DEMO_USER_ID}`, '收藏', tracks(4, 5, 7), 5);
  add('user:usr_grace', 'DJ 私藏', tracks(11, 13, 15, 16, 18, 6), 40);
  add('room:room_music', '电台精选', tracks(0, 1, 2, 4, 6, 8, 11, 15), 60);
  add('room:room_music', '周末派对', tracks(9, 10, 13, 17, 21), 14);
  add('room:room_hq', '总部歌单', tracks(3, 5, 12, 18), 25);
}

function seedMusicBoxes(store, now) {
  const music = store.rooms.get('room_music').musicBox;
  const requesters = ['usr_grace', 'usr_alice', 'usr_demo', 'usr_bob', 'usr_heidi'];
  music.temporaryQueue = tracks(6, 0, 8, 15, 11).map((entry, index) =>
    queueItem({ ...entry, artist: entry.artists.join(' / ') }, requesters[index], now - (50 - index * 5) * MINUTE),
  );
  music.state = 'playing';
  music.currentItemId = music.temporaryQueue[1].id;
  music.startedAt = now - 40 * SECOND;
  music.revision = 12;

  const hq = store.rooms.get('room_hq').musicBox;
  hq.temporaryQueue = tracks(3, 12).map((entry) => queueItem({ ...entry, artist: entry.artists.join(' / ') }, 'usr_alice', now - 2 * HOUR));
  hq.revision = 3;
}

// ---------------------------------------------------------------------------
// Messages

function seedMessages(store, room, spec, { rng, media, now }) {
  const count = spec.messageCount;
  const members = [...room.members.keys()];
  const demoIsMember = room.members.has(DEMO_USER_ID);
  const unreadTail = spec.demoUnread ?? 0;
  const kinds = Array.from({ length: count }, (_, index) => SPECIAL_KINDS[index] ?? 'text');
  if (spec.mentionDemoInUnread) kinds[count - 3] = 'mention_demo';
  const start = now - 3 * DAY;
  const step = (3 * DAY - 6 * MINUTE) / count;
  const others = (excluded) => members.filter((id) => id !== excluded);
  // The last `unreadTail + 2` messages are never sent by Demo so the unread badge is exact.
  const senderFor = (index) => {
    const pool = index >= count - unreadTail - 2 ? others(DEMO_USER_ID) : members;
    return pick(rng, pool);
  };
  const stickerPack = store.stickerPacks.get(spec.stickerPack ?? 'pack_alice');
  const roomPlaylist = [...store.playlists.values()].find((playlist) => playlist.ownerKey === `room:${room.id}`);

  for (let index = 0; index < count; index++) {
    const createdAt = Math.round(start + index * step + rng() * step * 0.6);
    const senderId = senderFor(index);
    const previous = room.messages[room.messages.length - 1];
    let message = { senderId, createdAt, body: '' };
    switch (kinds[index]) {
      case 'joined':
        message = systemMessageSpec(store, room, { event: 'room_member_joined', userId: members[members.length - 1], createdAt });
        break;
      case 'live_joined': {
        const liveUser = spec.live?.[0]?.[0] ?? senderId;
        message = systemMessageSpec(store, room, { event: 'live_joined', userId: liveUser, createdAt });
        break;
      }
      case 'role_changed': {
        const admin = spec.admins.find((id) => id !== DEMO_USER_ID) ?? spec.admins[0];
        message = admin
          ? systemMessageSpec(store, room, { event: 'room_role_changed', actorId: spec.owner, targetId: admin, fromRole: 'member', toRole: 'admin', createdAt })
          : { ...message, body: pick(rng, PHRASES[spec.topic]) };
        break;
      }
      case 'image':
        message = { ...message, type: 'file', attachments: [fileAttachment(media.photo())] };
        break;
      case 'images':
        message = { ...message, type: 'file', body: '今天的两张图', attachments: [fileAttachment(media.photo()), fileAttachment(media.photo())] };
        break;
      case 'image_external':
        message = { ...message, type: 'file', attachments: [fileAttachment(media.externalPhoto())] };
        break;
      case 'file':
        message = { ...message, type: 'file', body: index % 2 ? '' : '资料在这里', attachments: [fileAttachment(media.file())] };
        break;
      case 'voice': {
        const durationMs = randomInt(rng, 2, 9) * 1000 + randomInt(rng, 0, 900);
        const asset = media.voice(durationMs, createdAt);
        message = { ...message, type: 'audio', attachments: [{ type: 'audio', name: asset.filename, assetId: asset.id, durationMs }] };
        break;
      }
      case 'sticker': {
        const sticker = pick(rng, stickerPack.stickers);
        message = { ...message, type: 'sticker', body: `[表情] ${sticker.name}`, attachments: [{ type: 'sticker', stickerId: sticker.id, name: sticker.name, assetId: sticker.assetId }] };
        break;
      }
      case 'link':
        message.body = pick(rng, LINK_LINES);
        break;
      case 'long':
        message.body = LONG_MESSAGE;
        break;
      case 'quote':
        message = { ...message, body: pick(rng, SHORT_REPLIES), quotes: previous ? [store.quoteSpec(room, room.messages[Math.max(0, room.messages.length - 3)])] : [] };
        break;
      case 'mention_other': {
        const target = store.users.get(pick(rng, others(senderId)));
        message.body = `@${target.displayName} 这个你看下`;
        message.mentions = [{ type: 'user', user_id: target.id, label: target.displayName }];
        break;
      }
      case 'mention_demo':
        if (demoIsMember) {
          const demo = store.users.get(DEMO_USER_ID);
          message.senderId = others(DEMO_USER_ID)[index % others(DEMO_USER_ID).length];
          message.body = `@${demo.displayName} 有空看一下这个问题吗？`;
          message.mentions = [{ type: 'user', user_id: demo.id, label: demo.displayName }];
        } else {
          message.body = pick(rng, PHRASES[spec.topic]);
        }
        break;
      case 'mention_all':
        message.body = '@所有人 今晚 9 点语音集合';
        message.mentions = [{ type: 'all', label: '所有人' }];
        message.senderId = spec.owner;
        break;
      case 'ask_bot':
        message.body = room.gangBotEnabled ? '@gang-bot 帮我总结一下今天的讨论' : pick(rng, PHRASES[spec.topic]);
        message.mentions = room.gangBotEnabled ? [{ type: 'bot', label: 'gang-bot' }] : [];
        break;
      case 'bot_reply':
        if (room.gangBotEnabled && previous) {
          message = { senderId: BOT_USER_ID, createdAt, body: '今天主要讨论了三件事：发布节奏、语音稳定性和设置页改版。（mock 回复）', quotes: [store.quoteSpec(room, previous)] };
        } else {
          message.body = pick(rng, SHORT_REPLIES);
        }
        break;
      case 'recalled':
        message = { ...message, body: '这条消息发错了', recalledAt: createdAt + 30 * SECOND, recalledBy: senderId };
        break;
      case 'music_track': {
        const entry = pick(rng, TRACK_CATALOG);
        const track = { id: nextId('pli'), playlist_id: '', track_id: entry.trackId, source: entry.source, title: entry.title, artists: entry.artists, duration_ms: entry.durationMs };
        message = { ...message, type: 'music_track', body: `[歌曲] ${entry.title}`, attachments: [{ type: 'music_track', track }] };
        break;
      }
      case 'playlist':
        if (roomPlaylist) {
          message = {
            ...message,
            type: 'playlist',
            body: `[歌单] ${roomPlaylist.name}`,
            attachments: [{ type: 'playlist', playlistId: roomPlaylist.id, playlistScope: 'room', sourceRoomId: room.id, playlist: sharedPlaylistJson(store, roomPlaylist, senderId) }],
          };
        } else {
          message.body = pick(rng, PHRASES[spec.topic]);
        }
        break;
      default:
        message.body = rng() < 0.3 ? pick(rng, SHORT_REPLIES) : pick(rng, PHRASES[spec.topic]);
    }
    store.addMessage(room, message);
  }

  // Everyone has read everything, except Demo's configured unread tail.
  for (const membership of room.members.values()) membership.lastReadSeq = room.seq;
  if (demoIsMember && unreadTail > 0) {
    let remaining = unreadTail;
    let seq = room.seq;
    for (let i = room.messages.length - 1; i >= 0 && remaining > 0; i--) {
      const message = room.messages[i];
      seq = message.seq - 1;
      if (message.senderId !== DEMO_USER_ID && message.type !== 'system' && !store.isRemoved(message)) remaining -= 1;
    }
    room.members.get(DEMO_USER_ID).lastReadSeq = seq;
  }
}

function fileAttachment(asset) {
  return { type: 'file', name: asset.filename, assetId: asset.id };
}

function seedLive(store, room, spec, now) {
  for (const [userId, { joinedMinutesAgo, screenViewerIds = [], ...state }] of spec.live ?? []) {
    room.live.participants.set(
      userId,
      store.newParticipant(userId, { ...state, joinedAt: now - joinedMinutesAgo * MINUTE, screenViewerIds: new Set(screenViewerIds) }),
    );
  }
  room.live.updatedAt = now - 5 * MINUTE;
}

// ---------------------------------------------------------------------------
// Invites, join requests, notifications, blacklist

function seedSocialGraph(store, now) {
  const request = (spec) => {
    const value = { id: nextId('jrq'), status: 'pending', reason: '', source: 'public_search', inviterIds: [], createdAt: now - HOUR, reviewedAt: null, reviewerId: null, requestNotificationDeleted: false, reviewNotificationDeleted: false, ...spec };
    value.updatedAt ??= value.reviewedAt ?? value.createdAt;
    store.joinRequests.set(value.id, value);
  };
  request({ roomId: 'room_hq', userId: 'usr_judy', reason: '想加入总部，一起测试 Electron 客户端', createdAt: now - 3 * HOUR });
  request({ roomId: 'room_hq', userId: 'usr_ivan', source: 'invitation', inviterIds: ['usr_alice'], createdAt: now - 26 * HOUR });
  request({ roomId: 'room_photo', userId: DEMO_USER_ID, reason: '喜欢拍照，想交流一下', createdAt: now - 5 * HOUR });
  request({ roomId: 'room_archive', userId: DEMO_USER_ID, status: 'rejected', reason: '想看看以前的资料', createdAt: now - 4 * DAY, reviewedAt: now - 2 * DAY, reviewerId: 'usr_frank' });

  const invite = { id: nextId('inv'), roomId: 'room_books', inviterId: 'usr_alice', inviteeId: DEMO_USER_ID, status: 'pending', createdAt: now - 2 * HOUR, updatedAt: now - 2 * HOUR, hidden: false };
  store.invites.set(invite.id, invite);

  const dev = store.rooms.get('room_dev');
  const mention = [...dev.messages].reverse().find((message) => message.mentions.some((m) => m.user_id === DEMO_USER_ID));
  const notify = (spec) => {
    const value = { id: nextId('ntf'), userId: DEMO_USER_ID, actorId: null, fromRole: null, toRole: null, messageId: null, messagePreview: null, readAt: null, ...spec };
    store.notifications.set(value.id, value);
  };
  notify({ type: 'member_removed', roomId: 'room_flutter', actorId: 'usr_ivan', createdAt: now - 20 * DAY, readAt: now - 19 * DAY });
  notify({ type: 'role_promoted', roomId: 'room_dev', actorId: 'usr_alice', fromRole: 'member', toRole: 'admin', createdAt: now - 5 * DAY, readAt: now - 5 * DAY });
  if (mention) {
    notify({ type: 'mentioned', roomId: 'room_dev', actorId: mention.senderId, messageId: mention.id, messagePreview: mention.body, createdAt: mention.createdAt });
  }

  store.rooms.get('room_hq').blacklist.set('usr_mallory', { createdAt: now - 3 * DAY, blockedBy: DEMO_USER_ID });
}

/** Gives a brand-new account (register or unknown email login) a populated home screen. */
export function onboardNewUser(store, user) {
  for (const roomId of ONBOARDING_ROOM_IDS) {
    const room = store.rooms.get(roomId);
    if (!room || room.members.has(user.id)) continue;
    store.addMember(room, user.id, { role: 'member', joinedAt: Date.now(), lastReadSeq: Math.max(0, room.seq - 2) });
  }
}
