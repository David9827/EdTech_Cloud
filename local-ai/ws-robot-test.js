#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

// Configure once here for your default local test.
const DEFAULT_CONFIG = {
  url: "ws://127.0.0.1:5999/ws/robot",
  audio: "D:/HOC TAP/HAUI/DATN2026/audio test/response4.wav",
  robotId: "4f864132-2c3f-4fff-9811-19a840e93473",
  saveAudio: "local-ai/reply.wav",
  saveLog: "local-ai/ws-response.log",
  sampleRate: 16000,
  channels: 1,
  format: "OGG_OPUS",
  frameMs: 20,
  chunkSize: 4096,
  chunkDelayMs: 4,
  timeoutMs: 25000,
};

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      continue;
    }
    const key = token.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      args[key] = true;
      continue;
    }
    args[key] = next;
    i += 1;
  }
  return args;
}

function usage() {
  console.log(`
Usage:
  node local-ai/ws-robot-test.js [options]

Defaults are loaded from DEFAULT_CONFIG in this file.
You can still override with CLI flags below.

Optional:
  --url <ws-url>        Default: ws://127.0.0.1:5999/ws/robot
  --audio <path>        Audio file path to send as binary frame
  --robot-id <uuid>     Robot UUID
  --session-id <id>     Default: random UUID
  --utterance-id <id>   Default: utt-<random>
  --sample-rate <n>     Default: 16000
  --channels <n>        Default: 1
  --format <name>       Default: OGG_OPUS
  --frame-ms <n>        Default: 20
  --chunk-size <n>      Default: 4096
  --chunk-delay-ms <n>  Default: 4
  --timeout-ms <n>      Default: 25000
  --save-audio <path>   Save TTS binary response to file
  --save-log <path>     Save WS text events to file

Example:
  node local-ai/ws-robot-test.js
`);
}

function toInt(value, fallback) {
  const n = Number.parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function eventDataToBuffer(data) {
  if (Buffer.isBuffer(data)) {
    return data;
  }
  if (data instanceof ArrayBuffer) {
    return Buffer.from(data);
  }
  if (ArrayBuffer.isView(data)) {
    return Buffer.from(data.buffer, data.byteOffset, data.byteLength);
  }
  if (typeof Blob !== "undefined" && data instanceof Blob) {
    const arr = await data.arrayBuffer();
    return Buffer.from(arr);
  }
  return Buffer.alloc(0);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || args.h) {
    usage();
    process.exit(0);
  }

  const audioPath = args.audio || DEFAULT_CONFIG.audio;
  const robotId = args["robot-id"] || DEFAULT_CONFIG.robotId;
  if (!audioPath || !robotId) {
    usage();
    process.exit(1);
  }

  const resolvedAudioPath = path.resolve(audioPath);
  if (!fs.existsSync(resolvedAudioPath)) {
    console.error(`Audio file not found: ${resolvedAudioPath}`);
    process.exit(1);
  }

  const wsUrl = args.url || DEFAULT_CONFIG.url;
  const sessionId = args["session-id"] || crypto.randomUUID();
  const utteranceId = args["utterance-id"] || `utt-${crypto.randomUUID().slice(0, 8)}`;
  const sampleRate = toInt(args["sample-rate"], DEFAULT_CONFIG.sampleRate);
  const channels = toInt(args.channels, DEFAULT_CONFIG.channels);
  const format = args.format || DEFAULT_CONFIG.format;
  const frameMs = toInt(args["frame-ms"], DEFAULT_CONFIG.frameMs);
  const chunkSize = toInt(args["chunk-size"], DEFAULT_CONFIG.chunkSize);
  const chunkDelayMs = toInt(args["chunk-delay-ms"], DEFAULT_CONFIG.chunkDelayMs);
  const timeoutMs = toInt(args["timeout-ms"], DEFAULT_CONFIG.timeoutMs);
  const saveAudioRaw = args["save-audio"] || DEFAULT_CONFIG.saveAudio;
  const saveAudioPath = saveAudioRaw ? path.resolve(saveAudioRaw) : null;
  const saveLogRaw = args["save-log"] || DEFAULT_CONFIG.saveLog;
  const saveLogPath = saveLogRaw ? path.resolve(saveLogRaw) : null;

  const audioBuffer = fs.readFileSync(resolvedAudioPath);
  const receivedAudioChunks = [];
  const textEvents = [];
  let closing = false;

  const ws = new WebSocket(wsUrl);
  ws.binaryType = "arraybuffer";

  const closeTimer = setTimeout(() => {
    if (!closing) {
      closing = true;
      console.log(`Timeout reached (${timeoutMs}ms), closing connection...`);
      ws.close();
    }
  }, timeoutMs);

  const onClose = () => {
    clearTimeout(closeTimer);
    if (saveLogPath && textEvents.length > 0) {
      fs.writeFileSync(saveLogPath, `${textEvents.join("\n")}\n`, "utf8");
      console.log(`Saved WS text events: ${saveLogPath}`);
    }
    if (saveAudioPath && receivedAudioChunks.length > 0) {
      const merged = Buffer.concat(receivedAudioChunks);
      fs.writeFileSync(saveAudioPath, merged);
      console.log(`Saved TTS audio: ${saveAudioPath} (${merged.length} bytes)`);
    }
  };

  ws.addEventListener("error", (ev) => {
    console.error("WebSocket error:", ev.message || ev.error || "unknown");
  });

  ws.addEventListener("message", async (event) => {
    if (typeof event.data === "string") {
      console.log(event.data);
      textEvents.push(event.data);
      try {
        const obj = JSON.parse(event.data);
        if (obj.type === "ERROR" || obj.type === "TTS_END") {
          if (!closing) {
            closing = true;
            ws.close();
          }
        }
      } catch (_) {
      }
      return;
    }

    const payload = await eventDataToBuffer(event.data);
    receivedAudioChunks.push(payload);
    console.log(`<binary bytes=${payload.length}>`);
  });

  ws.addEventListener("close", () => {
    onClose();
    process.exit(0);
  });

  await new Promise((resolve, reject) => {
    const onOpen = () => resolve();
    const onErr = (e) => reject(new Error(e.message || "WebSocket connect failed"));
    ws.addEventListener("open", onOpen, { once: true });
    ws.addEventListener("error", onErr, { once: true });
  });

  const sendJson = (obj) => ws.send(JSON.stringify(obj));

  sendJson({ type: "HELLO", sessionId, robotId });
  await sleep(120);

  sendJson({
    type: "AUDIO_START",
    sessionId,
    robotId,
    utteranceId,
    format,
    sampleRate,
    channels,
    frameDurationMs: frameMs,
  });
  await sleep(150);

  for (let offset = 0; offset < audioBuffer.length; offset += chunkSize) {
    ws.send(audioBuffer.subarray(offset, offset + chunkSize));
    if (chunkDelayMs > 0) {
      await sleep(chunkDelayMs);
    }
  }

  await sleep(80);
  sendJson({ type: "AUDIO_END", sessionId, robotId, utteranceId });
}

main().catch((err) => {
  console.error("Test failed:", err.message || err);
  process.exit(1);
});
