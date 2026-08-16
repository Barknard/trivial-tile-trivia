/*
 * Where does the relay itself give out?
 *
 * capacity-test.js runs real browsers, which is what players actually use but
 * caps out on the test machine's memory long before the server struggles. This
 * one talks the game's WebSocket protocol directly, so it can put hundreds of
 * players on a game and measure the server rather than the browser.
 *
 *   node relay-capacity-test.js
 *   LEVELS=50,100,200,400 node relay-capacity-test.js
 *   BASE=http://192.168.1.42:5000 node relay-capacity-test.js
 *
 * Needs `npm install ws`.
 */
const WebSocket = require('ws');

const BASE = (process.env.BASE || 'http://127.0.0.1:5000').replace(/^http/, 'ws');
const LEVELS = (process.env.LEVELS || '25,50,100,200').split(',').map(Number);
const ROUNDS = Number(process.env.ROUNDS || 10);
const PAYLOAD = Number(process.env.PAYLOAD || 20000); // board-state sized

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const open = (url) => new Promise((resolve, reject) => {
  const ws = new WebSocket(url, { maxPayload: 64 * 1024 * 1024 });
  ws.once('open', () => resolve(ws));
  ws.once('error', reject);
});

async function runLevel(players) {
  const gameId = 'CAP' + Math.floor(Math.random() * 900 + 100);
  const host = await open(`${BASE}/ws`);
  let fromClients = 0;
  const buzzLatencies = [];
  host.on('message', (raw) => {
    const m = JSON.parse(raw.toString());
    if (m.type === 'FROM_CLIENT') {
      fromClients++;
      if (m.payload && m.payload.sentAt) buzzLatencies.push(Date.now() - m.payload.sentAt);
    }
  });
  host.send(JSON.stringify({ type: 'CREATE_ROOM', gameId }));
  await sleep(200);

  const joinStart = Date.now();
  const clients = [];
  const received = new Array(players).fill(0);
  for (let i = 0; i < players; i++) {
    const ws = await open(`${BASE}/ws`);
    ws.on('message', (raw) => {
      if (JSON.parse(raw.toString()).type === 'FROM_HOST') received[i]++;
    });
    ws.send(JSON.stringify({ type: 'JOIN_ROOM', gameId, peerId: `p${i}` }));
    clients.push(ws);
  }
  await sleep(Math.max(600, players * 8));
  const joinMs = Date.now() - joinStart;

  // The host pushes board state; every player buzzes back.
  const board = 'x'.repeat(PAYLOAD);
  const broadcastStart = Date.now();
  for (let round = 0; round < ROUNDS; round++) {
    host.send(JSON.stringify({ type: 'BROADCAST', gameId, payload: { round, board } }));
    clients.forEach((ws, i) => ws.send(JSON.stringify({
      type: 'TO_HOST', gameId, peerId: `p${i}`, payload: { buzz: round, sentAt: Date.now() },
    })));
    await sleep(60);
  }
  await sleep(1200 + players * 4);
  const elapsed = Date.now() - broadcastStart;

  const expectedEach = ROUNDS;
  const gotAll = received.filter((n) => n >= expectedEach).length;
  const expectedHost = ROUNDS * players;
  const sorted = buzzLatencies.slice().sort((a, b) => a - b);

  clients.forEach((ws) => ws.close());
  host.close();
  await sleep(150);

  return {
    players,
    joinMs,
    broadcastsDelivered: gotAll,
    buzzesReceived: fromClients,
    buzzesExpected: expectedHost,
    medianLatency: sorted.length ? sorted[Math.floor(sorted.length / 2)] : null,
    p95Latency: sorted.length ? sorted[Math.floor(sorted.length * 0.95)] : null,
    elapsed,
    mbMoved: Math.round((ROUNDS * players * PAYLOAD) / 1048576),
  };
}

(async () => {
  console.log(`Relay capacity against ${BASE}, ${ROUNDS} rounds of ${(PAYLOAD / 1000)}KB board state\n`);
  const rows = [];
  for (const players of LEVELS) {
    process.stdout.write(`  ${String(players).padStart(4)} players... `);
    try {
      const r = await runLevel(players);
      rows.push(r);
      const clean = r.broadcastsDelivered === players && r.buzzesReceived === r.buzzesExpected;
      console.log(`joined in ${(r.joinMs / 1000).toFixed(1)}s, ` +
        `broadcasts ${r.broadcastsDelivered}/${players}, buzzes ${r.buzzesReceived}/${r.buzzesExpected}, ` +
        `p95 ${r.p95Latency}ms  ${clean ? 'OK' : '<-- lossy'}`);
      if (!clean) break;
    } catch (err) {
      console.log(`FAILED: ${String(err.message).slice(0, 70)}`);
      rows.push({ players, failed: String(err.message).slice(0, 60) });
      break;
    }
  }

  console.log('\n' + '='.repeat(100));
  console.log('players   join    broadcasts delivered   buzzes received      median  p95    data moved');
  console.log('='.repeat(100));
  for (const r of rows) {
    if (r.failed) { console.log(`${String(r.players).padStart(7)}   ${r.failed}`); continue; }
    console.log(
      `${String(r.players).padStart(7)} ${((r.joinMs / 1000).toFixed(1) + 's').padStart(7)}` +
      ` ${(r.broadcastsDelivered + '/' + r.players).padStart(22)}` +
      ` ${(r.buzzesReceived + '/' + r.buzzesExpected).padStart(18)}` +
      ` ${(r.medianLatency + 'ms').padStart(9)} ${(r.p95Latency + 'ms').padStart(6)}` +
      ` ${(r.mbMoved + ' MB').padStart(11)}`);
  }
  const best = rows.filter((r) => !r.failed && r.broadcastsDelivered === r.players
    && r.buzzesReceived === r.buzzesExpected).pop();
  console.log('\n' + (best ? `Relay handled ${best.players} players with nothing dropped.`
    : 'No level completed cleanly.'));
})();
