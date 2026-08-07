# 音乐盒客户端 API 契约

本文档记录当前客户端与服务端共同支持的音乐盒协议。完整目标、后续阶段和验收矩阵见
[`music-box-next-generation-design.md`](music-box-next-generation-design.md)。

产品界面将 `temporary` 来源统一显示为“点歌队列”。为兼容旧版本，协议枚举和
`temporary_playlist` / `temporary_queue` 字段名保持不变。

音乐盒仍由服务端下载、转码并通过房间内的 `__musicbox__` LiveKit 参与者广播。
Windows、macOS、Android 只负责搜索、控制、状态展示和本地监听音量，不自行播放音源。

## 通用约定

- Base path：`/api/v1`
- 所有接口使用 `Authorization: Bearer <access_token>`。
- 调用者必须是目标房间成员。
- 写接口返回应用后的完整权威快照。
- 新客户端依据 `revision` 拒绝迟到快照；旧服务端没有 `revision` 时继续采用兼容覆盖行为。
- 时间字段沿用现有毫秒时间戳字符串格式。
- `503 music_box_unavailable` 表示服务端未启用音乐盒。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/rooms/:room_id/music-box/state` | 获取权威状态 |
| GET | `/rooms/:room_id/music-box/search` | 搜索歌曲 |
| POST | `/rooms/:room_id/music-box/queue` | 点歌，始终加入房间临时歌单 |
| DELETE | `/rooms/:room_id/music-box/queue/:item_id` | 删除房间队列项 |
| POST | `/rooms/:room_id/music-box/control` | 播放控制和模式切换 |
| POST | `/rooms/:room_id/music-box/activate` | 激活临时、房间或个人歌单 |

个人歌单和房间歌单的 CRUD、分页、重排接口仍使用既有
`/me/music-box/playlists` 和 `/rooms/:room_id/music-box/playlists` 路径。

## 歌单消息分享

个人歌单通过普通房间消息接口分享，继续复用房间成员权限、文字禁言、实时事件、未读数、推送和最后一条消息更新：

```http
POST /rooms/:room_id/messages
```

```json
{
  "client_message_id": "client_uuid",
  "type": "playlist",
  "body": "",
  "attachments": [
    { "type": "playlist", "playlist_id": "mbp_xxx" }
  ]
}
```

客户端不得提交可信歌单详情。服务端验证 `playlist_id` 属于当前账号后，将附件替换为包含 `playlist` 摘要、创建人快照和最多 500 首有序 `items` 的不可变快照，并把正文规范化为 `[歌单] 歌单名`。消息历史始终读取该快照，不依赖源歌单继续存在。

从嵌套消息的“查看歌单”快照窗口底部克隆：

```http
POST /rooms/:room_id/messages/:message_id/playlist/clone-to-me
```

嵌套消息摘要只显示歌曲数量，不重复显示创建人；行末箭头直接打开快照窗口，普通点击仍打开歌单名片。歌单名片只保留“查看歌单”，快照窗口中的每首歌曲复用歌曲名片，支持本地试听和添加到已有个人歌单，底部提供“克隆到我的歌单”和“完成”。服务端重新检查房间访问权、消息未撤回且包含有效快照，然后在单一事务中创建个人歌单并写入全部歌曲；达到个人歌单上限返回 `409 playlist_limit_reached`，不得留下空歌单或部分歌曲。

### 本地试听

设置和房间设置中的歌曲名片使用独立的本地试听接口，不加入房间队列，也不改变
LiveKit 音乐盒参与者的权威播放状态：

```http
POST /api/v1/me/music-box/preview
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "source": "netease",
  "track_id": "t1"
}
```

成功时返回 `audio/mp4` 二进制内容。服务端复用解析和转码流程，将音频统一转换为三个
客户端均可原生播放的 M4A/AAC，并在独立的试听缓存中
按来源和歌曲 ID 去重；客户端再写入应用缓存，后续试听优先命中本地文件。试听播放器
必须与房间 WebRTC 音频隔离，不能请求独占音频焦点、切换通话模式或修改输出路由。
关闭歌曲名片、点击“取消试听”或开始试听另一首歌时，只停止本地试听。

## 权威状态

```json
{
  "enabled": true,
  "revision": 42,
  "active_source": {
    "type": "room_playlist",
    "playlist_id": "playlist_1",
    "name": "房间收藏",
    "owner_user_id": ""
  },
  "temporary_playlist": {
    "queued_count": 2,
    "capabilities": {
      "can_enqueue": true,
      "can_reorder": true,
      "can_clear": true
    }
  },
  "playback": {
    "state": "playing",
    "current_item_id": "mbx_1",
    "position_ms": 42000,
    "volume": 100,
    "mode": "repeat_all",
    "can_previous": true,
    "can_next": true,
    "capabilities": {
      "can_control": true,
      "can_change_mode": true,
      "allowed_modes": [
        "sequential",
        "repeat_one",
        "repeat_all",
        "shuffle"
      ]
    },
    "updated_at": "1785800000000"
  },
  "queue": [],
  "temporary_queue": [],
  "usage": {
    "used_bytes": 3873527,
    "limit_bytes": 209715200
  }
}
```

字段说明：

- `revision`：房间音乐盒结构状态的单调版本号。
- `active_source.type`：`temporary`、`room_playlist` 或 `user_playlist`。
- `queue`：当前激活来源的独立播放队列。
- `temporary_queue`：房间临时歌单；播放已保存歌单时仍会返回，点歌不会丢失。
- `temporary_playlist.queued_count`：临时歌单条目数，可用于标签提示。
- `playback.state`：`stopped`、`playing` 或 `paused`。
- `playback.mode`：`sequential`、`repeat_one`、`repeat_all` 或 `shuffle`。
- 临时歌单的 `allowed_modes` 仅包含 `sequential`、`repeat_one`。
- 已保存房间/个人歌单支持全部四种模式。
- `queue[].status`：`pending`、`downloading`、`ready` 或 `failed`。
- `queue[].requested_by`：由服务端认证身份生成的点歌人展示摘要；其中
  `display_name` 可使用房间名，`avatar_label` 必须使用全局昵称/用户名，避免
  预设头像错误地显示房间专属名称。
- `queue[].can_play_now`：服务端确认该条目可执行优先播放时为 `true`。
- `usage`：当前房间音乐盒转码文件占用和上限，仅用于服务端容量策略和诊断，
  当前客户端不再把它作为音乐盒标题信息展示。

服务重启会保留队列和歌单，但统一恢复为 `stopped`，不会自动出声。

## 搜索与点歌

```http
GET /api/v1/rooms/:room_id/music-box/search?keyword=晴天&source=netease&count=20&page=1
```

搜索结果：

```json
{
  "results": [
    {
      "track_id": "t1",
      "name": "歌曲名",
      "artists": ["歌手"],
      "source": "netease"
    }
  ]
}
```

点歌：

```http
POST /api/v1/rooms/:room_id/music-box/queue
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "source": "netease",
  "track_id": "t1",
  "title": "歌曲名",
  "artist": "歌手",
  "duration_ms": 200000
}
```

点歌人只取认证会话，不能由请求体指定。无论当前播放什么来源，新点歌曲目都进入
`temporary_queue`；当前正在播放已保存歌单时不会修改其播放快照。同一房间的点歌队列已含有相同
`source + track_id` 时返回 `409 music_box_item_already_queued`，客户端显示“已在队列中”并保留服务端权威快照。

个人歌单和房间歌单的单首添加同样以 `source + track_id` 作为具体链接身份，目标歌单已包含该链接时返回 `409 playlist_item_already_exists`。检查和写入在锁定目标歌单的同一事务内完成，不依赖客户端的分页预查结果。

## 播放控制

```http
POST /api/v1/rooms/:room_id/music-box/control
Content-Type: application/json

{
  "action": "next",
  "command_id": "mbx-unique-command",
  "expected_revision": 42
}
```

支持的 `action`：

- `play`
- `pause`
- `resume`
- `previous`
- `next` 或兼容别名 `skip`
- `stop`
- `set_mode`
- `play_now`（同时传入活动队列中的 `item_id`）
- `clear_temporary_playlist`（清空点歌队列）

优先播放示例：

```json
{
  "action": "play_now",
  "item_id": "mbx_1",
  "command_id": "mbx-play-now-command"
}
```

目标必须属于当前激活队列且已准备完成。`play_now` 是单条目命令：客户端应发送
`command_id` 保证重试幂等，但不应携带从歌曲名片等临时 UI 快照取得的
`expected_revision`。服务端会在执行时重新校验房间、活动队列、快照和条目状态，避免
无关的进度或队列更新把一次仍然有效的优先播放拒绝为 revision 冲突。

播放中或暂停中执行时，服务端复用现有 LiveKit 音乐机器人连接和已发布的 Opus
track，只停止读取当前文件并从目标文件开头继续发送；不会为了切歌断开再重连。同一
首歌再次执行会从头播放，暂停状态会转为播放。当前没有播放器时，只有 LiveKit 连接
成功并且目标已成为当前歌曲后才返回成功；连接失败不会提前写入一个“已切换”的中间
状态。成功响应和随后广播的完整权威快照均以目标歌曲为当前项、位置为 `0`。

当前来源为点歌队列且已有正在播放或暂停的歌曲时，服务端会先把目标条目原子移动到
原当前歌曲的下一位，再立即切换播放；因此队列顺序为“刚才播放歌曲 → 优先歌曲 →
原后续歌曲”。当前来源为房间歌单或个人歌单时只立即切歌，歌单顺序保持不变。切歌或
建连失败时，点歌队列顺序必须恢复，不能留下只重排但没有成功播放的半完成状态。

切换模式时增加 `mode`：

```json
{
  "action": "set_mode",
  "mode": "repeat_one",
  "command_id": "mbx-mode-command",
  "expected_revision": 42
}
```

`command_id` 用于幂等去重。需要保护批量编辑或模式切换等易覆盖状态的命令可以携带
`expected_revision`；它与当前状态不一致时返回：

```json
{
  "error": {
    "code": "music_box_revision_conflict",
    "message": "music box state changed; refresh and try again"
  },
  "state": {}
}
```

客户端应应用响应中的最新 `state`，再决定是否重试用户操作。

`play_now` 的稳定业务错误包括：目标不在当前活动队列时返回 `not_found`，目标尚未
准备完成时返回 `music_box_item_not_ready`。客户端应优先展示服务端本地化后的具体
原因，而不是统一显示“操作失败”。

清空点歌队列示例：

```json
{
  "action": "clear_temporary_playlist",
  "command_id": "mbx-clear-queue-command",
  "expected_revision": 42
}
```

这是批量破坏性操作。客户端只在点歌队列非空时启用按钮，执行前必须显示确认界面，
并携带打开确认界面时的 `expected_revision`，避免确认期间其他成员新点的歌曲被意外
清除。服务端只删除该房间的 `temporary_queue`，不删除房间歌单、个人歌单或正在使用的
已保存歌单快照；点歌队列为当前来源时同时停止播放并清除当前项。相同
`command_id` 的重试只应用一次。

上一首规则：当前歌曲已播放超过 3 秒时从头播放；否则返回当前来源中的上一首。
暂停状态下切换上一首/下一首后仍保持暂停。

## 激活歌单

激活房间歌单：

```http
POST /api/v1/rooms/:room_id/music-box/activate
Content-Type: application/json

{
  "source_type": "room_playlist",
  "playlist_id": "playlist_1",
  "start_play": true
}
```

激活个人歌单时使用 `user_playlist`；切回临时歌单：

```json
{
  "source_type": "temporary",
  "start_play": true
}
```

已保存歌单会复制为房间本轮独立播放快照。之后编辑原歌单不会改变正在播放的顺序；
切换来源不会清空临时歌单。

客户端队列标题行在加号按钮左侧保留一个上下文按钮：当前来源不是点歌队列时用于切回
点歌队列，原列表中的独立“切回点歌队列”按钮不再显示；当前来源是点歌队列时用于清空
队列，队列为空时禁用。

## 实时状态

复用 `GET /api/v1/me/stream` SSE：

- 事件名：`music_box_changed`
- payload：与 `GET /music-box/state` 相同的完整快照。

迁移期仍保留完整快照心跳。客户端应用规则：

1. 更高 `revision`：应用完整快照。
2. 相同 `revision` 且 `current_item_id` 相同：允许校准进度。
3. 更低 `revision`：丢弃。
4. 缺少 `revision`：按旧服务端兼容行为处理。

## 当前迁移边界

当前版本完成了权威 revision、控制确认、上一首、四种模式、来源切换、独立临时队列和
已保存歌单播放快照。跨房间共享的规范化音频缓存、精简进度事件、完整播放历史窗口和
更细粒度的角色 capabilities 仍按下一代设计文档分阶段演进；客户端必须只使用服务端
实际返回的 capabilities，不能根据本地 UI 推导权限。
