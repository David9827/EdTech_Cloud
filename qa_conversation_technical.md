# Phan tich ky thuat luong Hoi thoai va QA

Tai lieu nay phan tich chi tiet luong QA trong he thong robot, dua tren code firmware `robot2QA.ino` va backend WebSocket.

## 1) Muc tieu va pham vi

Luong QA co 2 muc tieu chinh:
1. Cho phep robot nhan cau hoi theo thoi gian thuc khi dang IDLE hoac dang doc truyen.
2. Dam bao luong am thanh duoc ngat/chuyen uu tien an toan (khong overlap story-reminder-QA-TTS).

Pham vi tai lieu:
1. Trigger QA o firmware.
2. State machine QA tren firmware.
3. Giao thuc WebSocket robot-backend.
4. Pipeline backend STT -> LLM -> TTS.
5. Co che no-speech, timeout, interrupt, resume.

## 2) Kien truc xu ly QA (end-to-end)

1. **Firmware ESP32 (`robot2QA.ino`)**
   - Phat hien trigger QA (VAD/serial/command CUSTOM).
   - Cat output hien tai, thu audio mic, VAD, stream binary qua WS.
   - Nhan transcript/reply/TTS tu backend va phat loa.
   - Quan ly state resume sau QA.

2. **Backend WS (`RobotWebSocketHandler`)**
   - Nhan event HELLO/AUDIO_START/AUDIO_END/BIN/CANCEL_OUTPUT.
   - Gom audio theo utterance qua `RobotSessionManager`.
   - Goi `AudioPipelineService` de STT + LLM.
   - Tong hop TTS, stream binary ve robot.

3. **AI pipeline (`DefaultAudioPipelineService`)**
   - STT: `sttService.transcribe(...)`.
   - LLM:
     - Neu robot dang playback story: `generateStoryQaReply(...)`.
     - Neu khong: `generateReply(...)`.
   - Tra transcript + assistantReply cho WS handler.

4. **Story context (`StoryService.getQaContext`)**
   - Lay playback state cua robot.
   - Chi lay cac doan da ke den segment hien tai.
   - Mac dinh lay 4 doan gan nhat cho STORY_QA.

## 3) Cac trigger vao luong QA

QA co the bat dau tu 3 nguon:
1. **Auto VAD**: `tickAutoQaTrigger()` -> `startQaInterrupt("vad_auto")`.
2. **Barge-in trong mot so che do**: `startQaInterrupt("vad_barge_in")`.
3. **Nguoi dung/lenh**:
   - Serial `q/Q`.
   - Command `CUSTOM` -> `onCustomCommand()` -> `startQaInterrupt("custom")`.

## 4) State machine QA tren firmware

State QA duoc mo ta boi `QaStep`:
1. `QA_STEP_WAIT_WS`
2. `QA_STEP_WAIT_HELLO_ACK`
3. `QA_STEP_WAIT_AUDIO_START_ACK`
4. `QA_STEP_STREAM_MIC`
5. `QA_STEP_WAIT_TTS_END`
6. `QA_STEP_IDLE` (terminal/reset)

Dieu kien vao state machine:
1. `g_state` phai la `STATE_INTERRUPT_QA`.
2. Tick chinh chay qua `tickInterruptQa()`.

### 4.1 Bat dau QA (`startQaInterrupt`)

Khi vao QA:
1. Neu da o `STATE_INTERRUPT_QA` nhung khong phai truong hop restart tu `WAIT_TTS_END` thi bo qua trigger moi.
2. Luu state can resume (`g_resumeStateAfterQa`) la `STORY_PLAYING` hoac `IDLE`.
3. `flushAudioOutputNow()` de cat loa ngay.
4. Set:
   - `g_wsDropBinaryAudio = true`
   - `g_state = STATE_INTERRUPT_QA`
   - `g_qaStep = QA_STEP_WAIT_WS`
5. Reset co WS/capture/auto detector.
6. Khoi tao WS hoac restart WS tuy tinh huong.

Truong hop dac biet (dang cho `TTS_END` ma co trigger moi):
1. Thu `CANCEL_OUTPUT`.
2. Neu gui cancel that bai -> restart WS client.

### 4.2 Chay theo tung step (`tickInterruptQa`)

1. **WAIT_WS**
   - Doi `g_wsConnected=true`.
   - Gui `HELLO`.
   - Qua `QA_STEP_TIMEOUT_MS` (12000 ms) -> fail.

2. **WAIT_HELLO_ACK**
   - Doi `g_wsHelloAck=true`.
   - Gui `AUDIO_START`.
   - Qua timeout -> fail.

3. **WAIT_AUDIO_START_ACK**
   - Doi `g_wsAudioStartAck=true`.
   - Reset capture state, vao `STREAM_MIC`.
   - Qua timeout -> fail.

4. **STREAM_MIC**
   - Neu loa dang active (guard) -> tam reset hit va cho.
   - Doc mic frame 20 ms (640 bytes PCM 16-bit, 16kHz).
   - AEC + VAD + pre-roll + stream BIN.
   - Ket thuc cau noi theo:
     - End silence: sau toi thieu 500 ms, im lang >= 850 ms.
     - Hoac dat max utterance 9000 ms.
   - Gui `AUDIO_END` -> chuyen `WAIT_TTS_END`.

5. **WAIT_TTS_END**
   - Doi `g_wsTtsEnd=true` -> success.
   - Qua timeout `g_qaTtsWaitTimeoutMs` -> fail.

## 5) VAD va thu thap audio trong QA

## 5.1 Cau hinh chinh

1. `QA_MIC_CHUNK_BYTES = 640` (20 ms/frame).
2. `QA_WAIT_SPEECH_TIMEOUT_MS = 4500`.
3. `QA_MIN_UTTERANCE_MS = 500`.
4. `QA_MAX_UTTERANCE_MS = 9000`.
5. `QA_END_SILENCE_MS = 850`.

## 5.2 Dieu kien speech

Moi frame tinh:
1. `avgAbs` (do lon trung binh).
2. `peakAbs`.
3. `zcr` (zero-crossing rate).
4. `peakToAvg`.

Speech strong:
1. `frameAbs >= threshold`.
2. `zcr` trong dai manh (`QA_VAD_MIN_ZCR..QA_VAD_MAX_ZCR`).
3. `peakToAvg <= QA_VAD_MAX_PEAK_TO_AVG`.

Speech weak dung de cap nhat `lastSpeech`.

## 5.3 Nguong dong va noise floor

1. `threshold = max(QA_VAD_MIN_ABS, noiseEma * QA_VAD_THRESHOLD_MULTIPLIER)`.
2. `noiseEma` cap nhat boi `QA_VAD_NOISE_EMA_ALPHA`.
3. Co co che pre-roll (`QA_PREROLL_FRAMES=12`) de khong cat mat am dau cau noi.

## 5.4 Force start

Neu khong du hit nhung:
1. `QA_ENABLE_FORCE_START = true`
2. Da cho it nhat `QA_FORCE_START_MS = 1000`
3. Hit >= `QA_FORCE_START_MIN_HITS`

thi van bat dau speech de giam tre khi nguoi dung noi nho/nhanh.

## 6) Auto QA trigger va kha nang barge-in

Auto QA duoc bat boi `tickAutoQaTrigger()` voi:
1. Cooldown sau moi QA: `AUTO_QA_COOLDOWN_MS`.
2. Bo loc theo state:
   - STORY_PLAYING: cho phep nghe neu `AUTO_QA_LISTEN_DURING_STORY`.
   - IDLE: cho phep nghe neu `AUTO_QA_LISTEN_IN_IDLE`.
   - INTERRUPT_QA: chi cho phep neu `AUTO_QA_ALLOW_BARGE_IN_DURING_QA_TTS`.

Mac dinh trong code:
1. `AUTO_QA_ALLOW_BARGE_IN_DURING_QA_TTS = false`.
2. Nghia la trong luc QA TTS dang noi, trigger moi thuong bi chan.

Khi dang story:
1. Dung nguong cao hon (`AUTO_QA_STORY_*`).
2. Dung them check echo-dominance:
   - So sanh micAbs voi refAbs tu loa.
   - Neu mic/ref qua thap thi bo candidate.

## 7) Giao thuc WS giua firmware va backend

## 7.1 Khung text firmware gui

1. `HELLO` (sessionId, robotId).
2. `AUDIO_START` (sessionId, robotId, utteranceId, format, sampleRate, channels, frameDurationMs).
3. `AUDIO_END` (sessionId, utteranceId).
4. `CANCEL_OUTPUT` (sessionId, robotId, targetUtteranceId optional).

## 7.2 Khung firmware nhan

1. `ACK`:
   - ACK HELLO (khong utteranceId).
   - ACK AUDIO_START (co utteranceId).
2. `TRANSCRIPT`.
3. `ASSISTANT_REPLY`.
4. `TTS_START`:
   - mime, sampleRate, channels, audioBytesLength.
5. Binary TTS chunk.
6. `TTS_END`.
7. `OUTPUT_CANCELLED`.
8. `ERROR`.

## 7.3 Loc frame de tranh race/old message

Firmware bo qua:
1. Message khong dung `utteranceId` hien tai.
2. `TTS_START/TTS_END` neu khong o `WAIT_TTS_END`.
3. Binary WS neu:
   - khong o `STATE_INTERRUPT_QA`,
   - khong o `WAIT_TTS_END`,
   - hoac dang `g_wsDropBinaryAudio=true`.

## 7.4 Dynamic timeout khi nhan TTS_START

Firmware tinh lai timeout cho `WAIT_TTS_END`:
1. `playMs = audioBytes / (sampleRate*channels*2)`.
2. `dynamicTimeout = playMs + 15000`.
3. Clamp: min 25000, max 180000.

Neu metadata khong du thi dung mac dinh 25000 ms.

## 8) Xu ly no-speech va that bai

## 8.1 Nhan dien no-speech

`isWsNoSpeechError()` coi la no-speech khi:
1. `errorCode == NO_AUDIO` (so sanh lowercase).
2. Hoac `PIPELINE_ERROR` va message chua:
   - "empty transcript"
   - "no speech"
   - "blank audio"

## 8.2 Hanh vi khi no-speech

`restartQaListeningAfterNoSpeech()`:
1. Gui `AUDIO_END`.
2. Flush output.
3. Phat prompt "no speech" tu SPIFFS.
4. Wait them theo do dai prompt.
5. Neu bi STOP -> `finishQaInterrupt(false, "qa_stop_requested")`.
6. Neu khong -> `finishQaInterrupt(true, reasonTag)`.

Luu y:
1. No-speech duoc dong luong theo huong "success co huong dan", khong xem la loi he thong nghiem trong.

## 9) Resume state sau QA

`finishQaInterrupt(success, reason)` se:
1. Reset step + capture + ws flags + auto detector.
2. Neu `g_resumeStateAfterQa == STORY_PLAYING` nhung story da completed/clear roi -> ve `IDLE`.
3. Nguoc lai quay ve state da luu truoc QA.

Tac dung:
1. QA la "tam ngat", xong se tiep tuc task cu neu con hop le.

## 10) Backend pipeline chi tiet

## 10.1 RobotWebSocketHandler

1. `HELLO` -> `ACK`.
2. `AUDIO_START`:
   - validate format/sampleRate/channels.
   - mo utterance trong `RobotSessionManager`.
   - `ACK`.
3. Binary:
   - append vao buffer utterance.
   - neu chua AUDIO_START -> `ERROR AUDIO_NOT_STARTED`.
4. `AUDIO_END`:
   - chot buffer.
   - neu rong -> `ERROR NO_AUDIO`.
   - async `processAudioEnd(...)`.
5. `processAudioEnd(...)`:
   - goi `audioPipelineService`.
   - gui `TRANSCRIPT`, `ASSISTANT_REPLY`.
   - luu turn vao DB qua `MessageService.saveTurnFromWs`.
   - goi TTS, gui `TTS_START`, stream binary, gui `TTS_END`.
6. `CANCEL_OUTPUT`:
   - dat cancel target trong session manager.
   - gui `OUTPUT_CANCELLED`.

## 10.2 Audio pipeline

1. STT xong log moc latency `T2`.
2. Lay story context neu robot dang playback.
3. LLM xong log moc latency `T3`.
4. Tra transcript + reply.

## 10.3 Story-aware QA

Neu co playback state:
1. LLM input co title, segment hien tai, va cac doan da ke.
2. Muc tieu tranh spoil cac doan chua ke.

## 11) Moc latency dang co trong he thong

Firmware:
1. `T1`: luc gui `AUDIO_END`.
2. `T5_WAIT`: luc nhan `TTS_START`.

Backend:
1. `AUDIO_END received`.
2. `T2`: STT done.
3. `T3`: LLM done.
4. `T4`: first binary TTS chunk sent.

He thong da co nen de do:
1. Mic-end -> STT -> LLM -> TTS first-byte.
2. Co the dung cac moc nay de toi uu tre tong QA.

## 12) Rui ro va diem can luu y ky thuat

1. QA dang phu thuoc chat luong VAD/AEC; moi truong on ao de gay false trigger.
2. Timeout he thong can duoc tune theo do dai cau hoi va chat luong mang.
3. Barge-in trong luc QA TTS mac dinh bi tat (`AUTO_QA_ALLOW_BARGE_IN_DURING_QA_TTS=false`).
4. Khi WS reconnect lien tuc, trai nghiem QA co the ngat quang.
5. TTS WAV header duoc skip 44 bytes o firmware; can dong bo voi dinh dang backend.

## 13) Tong ket luong

1. Firmware dung state machine ro rang de quan ly QA.
2. Backend WS pipeline tach bach: session -> STT/LLM -> TTS stream.
3. Co co che fail-safe cho no-speech, timeout, cancel output, resume state.
4. Luong hien tai phu hop cho real-time QA trong boi canh robot dang ke truyen.
