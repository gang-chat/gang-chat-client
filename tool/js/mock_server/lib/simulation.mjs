// Background activity so the UI has something live to show: another seeded
// user posts into the first room every N seconds, and playing music boxes
// advance (structural `music_box_changed` on track change, `music_box_progress`
// every couple of seconds).
import { emitMusicBoxChanged, memberIds, postMessage } from './events.mjs';
import { photoPng } from './media.mjs';
import { progressJson, tick } from './music_box.mjs';
import { CHATTER_LINES, DEMO_USER_ID, FIRST_ROOM_ID } from './seed.mjs';

const MUSIC_TICK_MS = 2000;

export function startSimulation(app) {
  const timers = [];
  const { chatterIntervalMs } = app.config;
  if (chatterIntervalMs > 0) timers.push(setInterval(() => postChatter(app), chatterIntervalMs));
  timers.push(setInterval(() => tickMusicBoxes(app), MUSIC_TICK_MS));
  for (const timer of timers) timer.unref();
  return {
    stop() {
      for (const timer of timers) clearInterval(timer);
    },
  };
}

let chatterCount = 0;

/**
 * Posts one message "from someone else" into FIRST_ROOM_ID: a member who is
 * not currently connected, never Demo or the bot. Mostly text; every 5th is an
 * image and every 7th mentions Demo.
 */
export function postChatter(app, roomId = FIRST_ROOM_ID) {
  const { store, hub } = app;
  const room = store.rooms.get(roomId);
  if (!room) return null;
  const connected = hub.connectedUserIds();
  const humans = memberIds(room).filter((id) => id !== DEMO_USER_ID && !store.users.get(id).isBot);
  const idle = humans.filter((id) => !connected.has(id));
  const pool = idle.length > 0 ? idle : humans;
  if (pool.length === 0) return null;
  chatterCount += 1;
  const senderId = pool[chatterCount % pool.length];
  const line = CHATTER_LINES[chatterCount % CHATTER_LINES.length];
  const spec = { senderId, body: line };

  const demo = store.users.get(DEMO_USER_ID);
  if (chatterCount % 7 === 0 && demo && room.members.has(demo.id)) {
    spec.body = `@${demo.displayName} ${line}`;
    spec.mentions = [{ type: 'user', user_id: demo.id, label: demo.displayName }];
  } else if (chatterCount % 5 === 0) {
    const asset = store.addAsset({
      mimeType: 'image/png',
      filename: `live_${chatterCount}.png`,
      width: 480,
      height: 320,
      ownerUserId: senderId,
      generate: () => photoPng({ width: 480, height: 320, from: '#0ea5e9', to: '#a855f7', seed: chatterCount }),
    });
    Object.assign(spec, { type: 'file', body: '刚拍的', attachments: [{ type: 'file', name: asset.filename, assetId: asset.id }] });
  }
  const message = postMessage(app, room, spec);
  app.log(`[sim] ${store.users.get(senderId).username} -> ${room.id}: ${message.body || message.type}`);
  return message;
}

function tickMusicBoxes(app) {
  for (const room of app.store.rooms.values()) {
    const box = room.musicBox;
    if (!box) continue;
    const result = tick(box);
    if (result === 'changed') emitMusicBoxChanged(app, room);
    else if (result === 'progress') app.hub.sendToUsers(memberIds(room), 'music_box_progress', room.id, progressJson(box));
  }
}
