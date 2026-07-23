/* T-cubed simulation harness.
 *
 * Drives the WebSocket protocol of Trivial Tile Trivia like real clients would:
 *   - One host per "game", N simulated players (2..10).
 *   - Each player picks a random response time per clue.
 *   - Host opens buzzers, picks the first buzz, scores correct/incorrect with
 *     a configurable probability, broadcasts state to all clients.
 *   - 30 clues per game (full 6x5 Jeopardy board).
 *   - Logs every protocol event to ./sim_log.jsonl and a summary to
 *     ./sim_summary.json.
 *
 * Purpose: exercise the server's routing, identify race conditions, missing
 * acks, orphaned rooms, etc.  This does NOT exercise React-side game logic;
 * for that, browser automation would be needed.
 */
const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');
const URL = 'ws://127.0.0.1:5000/ws';

const LOG_PATH = path.join(__dirname, 'sim_log.jsonl');
const SUMMARY_PATH = path.join(__dirname, 'sim_summary.json');
try { fs.unlinkSync(LOG_PATH); } catch {}

function logEvent(rec) {
  fs.appendFileSync(LOG_PATH, JSON.stringify({ t: Date.now(), ...rec }) + '\n');
}

function rndId(n = 4) {
  const c = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  let s = '';
  for (let i = 0; i < n; i++) s += c[Math.floor(Math.random() * c.length)];
  return s;
}

function wait(ms) { return new Promise(r => setTimeout(r, ms)); }

function send(ws, obj) {
  if (ws.readyState !== 1) return false;
  ws.send(JSON.stringify(obj));
  return true;
}

function connect(role, gameId, peerId) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(URL);
    const timer = setTimeout(() => { try { ws.terminate(); } catch {}; reject(new Error('connect timeout')); }, 5000);
    ws.on('open', () => { clearTimeout(timer); resolve(ws); });
    ws.on('error', e => { clearTimeout(timer); reject(e); });
    ws.role = role; ws.gameId = gameId; ws.peerId = peerId;
  });
}

async function runGame({ players, cluesPerGame, correctProb, hostJudgeMs, buzzMs, gameIndex }) {
  const gameId = rndId(4);
  const stats = {
    gameIndex, gameId, players,
    cluesPerGame, correctProb,
    started: Date.now(),
    finished: null,
    cluesPlayed: 0,
    correctCount: 0,
    incorrectCount: 0,
    timeouts: 0,
    racesObserved: 0,    // multiple buzzes arrived within 20ms of each other
    perPlayer: {},
    protocolErrors: [],
    serverErrors: [],
  };

  // Host
  const host = await connect('host', gameId);
  host.on('message', raw => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    logEvent({ game: gameId, dir: 'host<-server', type: m.type });
    if (m.type === 'ERROR') stats.serverErrors.push(m.message);
  });
  send(host, { type: 'CREATE_ROOM', gameId });
  await wait(50);

  // Players
  const playerSockets = [];
  const buzzBuffer = []; // host's view of current-clue buzzes
  let currentClueIdx = -1;

  for (let i = 0; i < players; i++) {
    const peerId = `p${i}_${rndId(3)}`;
    const ws = await connect('player', gameId, peerId);
    ws.score = 0;
    ws.knowledge = 0.5 + Math.random() * 0.5; // 50-100% per player baseline
    ws.responseMs = () => 200 + Math.random() * buzzMs;
    ws.on('message', raw => {
      let m; try { m = JSON.parse(raw); } catch { return; }
      logEvent({ game: gameId, peer: peerId, dir: 'player<-server', type: m.type, payload_type: m.payload?.type });
      if (m.type === 'FROM_HOST' && m.payload?.type === 'CLUE_OPEN') {
        // Player "decides" whether to buzz - if they "know" the answer.
        const wantsToBuzz = Math.random() < ws.knowledge;
        if (wantsToBuzz) {
          setTimeout(() => {
            send(ws, { type: 'TO_HOST', gameId, peerId, payload: { type: 'BUZZ', t: Date.now() } });
          }, ws.responseMs());
        }
      } else if (m.type === 'HOST_DISCONNECTED') {
        // pass - we just observe
      }
    });
    send(ws, { type: 'JOIN_ROOM', gameId, peerId });
    playerSockets.push(ws);
    stats.perPlayer[peerId] = { knowledge: ws.knowledge.toFixed(2), correct: 0, incorrect: 0, score: 0 };
  }

  await wait(150 + players * 20); // let JOIN_ROOMs settle

  // Host's incoming-buzz handler depends on currentClueIdx so register after.
  host.on('message', raw => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    if (m.type === 'FROM_CLIENT' && m.payload?.type === 'BUZZ') {
      buzzBuffer.push({ peer: m.peerId, t: m.payload.t || Date.now(), arrivedAt: Date.now(), clue: currentClueIdx });
    }
  });

  // Run a full board (cluesPerGame clues).
  for (let clue = 0; clue < cluesPerGame; clue++) {
    currentClueIdx = clue;
    buzzBuffer.length = 0;
    const value = [200, 400, 600, 800, 1000][clue % 5];

    // Broadcast clue open
    send(host, { type: 'BROADCAST', gameId, payload: { type: 'CLUE_OPEN', clue: { idx: clue, value } } });

    // Wait for buzzes (up to hostJudgeMs total)
    const buzzWaitStart = Date.now();
    while (Date.now() - buzzWaitStart < hostJudgeMs && buzzBuffer.length < players) {
      await wait(20);
    }
    if (buzzBuffer.length === 0) {
      stats.timeouts++;
      send(host, { type: 'BROADCAST', gameId, payload: { type: 'CLUE_TIMEOUT', clue: { idx: clue, value } } });
      stats.cluesPlayed++;
      await wait(20);
      continue;
    }
    // Detect race: multiple buzzes within 20ms of first
    const firstArrival = buzzBuffer[0].arrivedAt;
    const within20 = buzzBuffer.filter(b => b.arrivedAt - firstArrival <= 20).length;
    if (within20 > 1) stats.racesObserved++;

    // Pick first buzzer (server should already preserve order)
    buzzBuffer.sort((a, b) => a.arrivedAt - b.arrivedAt);
    const first = buzzBuffer[0];

    // Score correctness using that player's knowledge prob (combined with global correctProb)
    const playerWs = playerSockets.find(p => p.peerId === first.peer);
    const isCorrect = playerWs && (Math.random() < (correctProb * playerWs.knowledge));

    if (isCorrect) {
      stats.correctCount++;
      stats.perPlayer[first.peer].correct++;
      stats.perPlayer[first.peer].score += value;
      send(host, { type: 'TO_CLIENT', gameId, peerId: first.peer, payload: { type: 'JUDGE', correct: true, value } });
      send(host, { type: 'BROADCAST', gameId, payload: { type: 'SCORE_UPDATE', peerId: first.peer, delta: +value } });
    } else {
      stats.incorrectCount++;
      stats.perPlayer[first.peer].incorrect++;
      stats.perPlayer[first.peer].score -= value;
      send(host, { type: 'TO_CLIENT', gameId, peerId: first.peer, payload: { type: 'JUDGE', correct: false, value } });
      send(host, { type: 'BROADCAST', gameId, payload: { type: 'SCORE_UPDATE', peerId: first.peer, delta: -value } });
    }

    stats.cluesPlayed++;
    // small gap between clues
    await wait(30);
  }

  stats.finished = Date.now();
  stats.durationMs = stats.finished - stats.started;
  stats.cluesPerSecond = +(stats.cluesPlayed / (stats.durationMs / 1000)).toFixed(2);

  // Clean shutdown
  for (const ws of playerSockets) { try { ws.close(); } catch {} }
  try { host.close(); } catch {}
  await wait(150);

  return stats;
}

async function main() {
  console.log(`[sim] starting against ${URL}`);
  const all = [];
  const scenarios = [];
  // 4 of each player count: 2, 3, 4, 5, 6, 7, 8, 10 = 32 games
  for (const p of [2, 3, 4, 5, 6, 7, 8, 10]) for (let r = 0; r < 4; r++) scenarios.push(p);

  for (let i = 0; i < scenarios.length; i++) {
    const players = scenarios[i];
    process.stdout.write(`  game ${i + 1}/${scenarios.length} (${players}p) ... `);
    let res;
    try {
      res = await runGame({
        players,
        cluesPerGame: 30,
        correctProb: 0.7,
        hostJudgeMs: 1200,
        buzzMs: 800,
        gameIndex: i,
      });
      console.log(`OK ${res.cluesPlayed}/${res.cluesPerGame} clues, ${res.correctCount}/${res.incorrectCount}/${res.timeouts} c/i/t, ${res.racesObserved} races, ${res.durationMs}ms`);
    } catch (e) {
      console.log(`FAIL ${e.message}`);
      res = { players, gameIndex: i, error: e.message };
    }
    all.push(res);
  }

  // Aggregate
  const successful = all.filter(g => !g.error);
  const summary = {
    totalGames: all.length,
    successful: successful.length,
    failed: all.length - successful.length,
    totalCluesPlayed: successful.reduce((a, g) => a + g.cluesPlayed, 0),
    totalCorrect: successful.reduce((a, g) => a + g.correctCount, 0),
    totalIncorrect: successful.reduce((a, g) => a + g.incorrectCount, 0),
    totalTimeouts: successful.reduce((a, g) => a + g.timeouts, 0),
    totalRaces: successful.reduce((a, g) => a + g.racesObserved, 0),
    totalProtocolErrors: successful.reduce((a, g) => a + (g.protocolErrors?.length || 0), 0),
    totalServerErrors: successful.reduce((a, g) => a + (g.serverErrors?.length || 0), 0),
    avgDurationMs: Math.round(successful.reduce((a, g) => a + g.durationMs, 0) / Math.max(successful.length, 1)),
    byPlayerCount: {},
    perGameDetails: all,
  };
  for (const g of successful) {
    const k = `${g.players}p`;
    if (!summary.byPlayerCount[k]) summary.byPlayerCount[k] = { games: 0, races: 0, timeouts: 0, durationMs: 0 };
    summary.byPlayerCount[k].games++;
    summary.byPlayerCount[k].races += g.racesObserved;
    summary.byPlayerCount[k].timeouts += g.timeouts;
    summary.byPlayerCount[k].durationMs += g.durationMs;
  }
  for (const k of Object.keys(summary.byPlayerCount)) {
    const r = summary.byPlayerCount[k];
    r.avgDurationMs = Math.round(r.durationMs / r.games);
    delete r.durationMs;
  }

  fs.writeFileSync(SUMMARY_PATH, JSON.stringify(summary, null, 2));
  console.log('\n[sim] complete.');
  console.log(`  ${summary.successful}/${summary.totalGames} games OK`);
  console.log(`  ${summary.totalCluesPlayed} clues played`);
  console.log(`  ${summary.totalTimeouts} timeouts, ${summary.totalRaces} buzz races`);
  console.log(`  ${summary.totalServerErrors} server errors, ${summary.totalProtocolErrors} protocol errors`);
  console.log(`  summary: ${SUMMARY_PATH}`);
  console.log(`  log: ${LOG_PATH}`);
}

main().catch(e => { console.error('[sim] fatal:', e); process.exit(1); });
