# 音乐盒客户端 API 契约

本文档记录当前客户端与服务端共同支持的音乐盒协议。完整目标、后续阶段和验收矩阵见
[`music-box-next-generation-design.md`](music-box-next-generation-design.md)。

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
- `queue[].requested_by`：由服务端认证身份生成的点歌人展示摘要。
- `usage`：当前房间音乐盒转码文件占用和上限。

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
`temporary_queue`；当前正在播放已保存歌单时不会修改其播放快照。

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

切换模式时增加 `mode`：

```json
{
  "action": "set_mode",
  "mode": "repeat_one",
  "command_id": "mbx-mode-command",
  "expected_revision": 42
}
```

`command_id` 用于幂等去重。`expected_revision` 与当前状态不一致时返回：

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
