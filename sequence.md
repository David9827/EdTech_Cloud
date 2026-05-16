# Sequence Flow: `robot2QA.ino` <-> Backend

## 1) Command Polling + Story Playback

```mermaid
sequenceDiagram
    autonumber
    participant App as Mobile/App
    participant RC as RobotController
    participant RCS as RobotStoryCommandService
    participant Redis as Redis
    participant ESP as ESP32 (robot2QA.ino)
    participant SC as StoryController
    participant SS as StoryService
    participant DB as PostgreSQL
    participant TTS as TtsService
    participant RemC as ReminderController

    App->>RC: POST /api/robots/{robotId}/commands (START_STORY/STOP_STORY/...)
    RC->>RCS: issueCommand(robotId, request)
    RCS->>DB: validate robot (+ story if START_STORY)
    RCS->>Redis: LPUSH robot:command:stack:{robotId} (TTL 120s)
    RCS-->>RC: RobotStoryCommandResponse
    RC-->>App: success=true, data=command

    loop every 1s (COMMAND_PULL_INTERVAL_MS)
        ESP->>RC: GET /api/robots/{robotId}/commands/pull?consume=true
        RC->>RCS: pollCommand(robotId, consume=true)
        RCS->>Redis: LPOP stack (skip expired command)
        RCS-->>RC: command or null
        RC-->>ESP: { success, data }
    end

    alt data.type == START_STORY
        ESP->>SC: POST /api/stories/playback/start {robotId, storyId}
        SC->>SS: startPlayback(robotId, storyId)
        SS->>DB: load Robot + Story(PUBLISHED) + StorySegments
        SS->>DB: save robot status=STORY_PLAYING
        SS->>DB: save story_playback_state
        SS->>Redis: save playback state + segment cache
        SS->>TTS: synthesize first segment if cache miss
        SS-->>SC: StoryPlaybackAudioChunk(audio bytes + headers)
        SC-->>ESP: HTTP 200 audio + X-Completed/X-Segment-Order
        ESP->>ESP: parseAndPlayAudioResponse() -> playAudioChunk()

        loop until completed=true or HTTP 204
            ESP->>SC: POST /api/stories/playback/next {robotId}
            SC->>SS: nextPlayback(robotId)
            SS->>Redis: resolve playback state
            SS->>DB: fallback state if cache miss
            SS->>TTS: synthesize next segment if needed
            SS-->>SC: next audio chunk or completed
            SC-->>ESP: HTTP 200/204 + headers
            ESP->>ESP: play audio, update state STORY_PLAYING/IDLE
        end
    else data.type == STOP_STORY
        ESP->>ESP: flushAudioOutputNow(), set stopRequested
        ESP->>SC: POST /api/stories/playback/stop {robotId}
        SC->>SS: stopPlayback(robotId)
        SS->>Redis: clear playback state key
        SS->>DB: delete story_playback_state, robot->IDLE
        SC-->>ESP: 200 OK
    else data.type == REMINDER_CREATE
        ESP->>ESP: preemptForReminderPriority()
        loop REMINDER_REPEAT_COUNT = 3
            ESP->>RemC: GET /api/reminders/{reminderId}/execute-audio
            RemC-->>ESP: reminder audio bytes
            Note over ESP: play reminder audio, wait 30s between repeats
        end
    end
```

## 2) Reminder Dispatch (Backend Scheduler -> Robot)

```mermaid
sequenceDiagram
    autonumber
    participant App as Mobile/App
    participant RemC as ReminderController
    participant RemS as ReminderService
    participant TTS as TtsService
    participant Redis as Redis
    participant Sched as ReminderCommandDispatchScheduler
    participant RCS as RobotStoryCommandService
    participant ESP as ESP32
    participant RC as RobotController

    App->>RemC: POST /api/reminders
    RemC->>RemS: createReminder()
    RemS->>TTS: prefetch reminder audio (title + message)
    RemS->>Redis: cache reminder:audio:{reminderId}
    RemC-->>App: reminder ACTIVE

    loop every 5s
        Sched->>RemS: query due reminders (ACTIVE, scheduleAt<=now, has robot)
        Sched->>RCS: issueCommand(type=REMINDER_CREATE, reminderId)
        RCS->>Redis: LPUSH robot:command:stack:{robotId}
        Sched->>RemS: mark reminder DONE
    end

    ESP->>RC: GET /api/robots/{robotId}/commands/pull?consume=true
    RC-->>ESP: REMINDER_CREATE
    ESP->>RemC: GET /api/reminders/{reminderId}/execute-audio
    RemC->>RemS: getExecuteAudio(reminderId)
    RemS->>Redis: load audio cache (or synthesize if miss)
    RemC-->>ESP: audio bytes + sampleRate/channels
```

## 3) QA Interrupt via WebSocket `/ws/robot`

```mermaid
sequenceDiagram
    autonumber
    participant ESP as ESP32 (STATE_INTERRUPT_QA)
    participant WSH as RobotWebSocketHandler
    participant SM as RobotSessionManager
    participant APS as AudioPipelineService
    participant STT as SttService (local/cloud)
    participant SS as StoryService
    participant LLM as LlmService
    participant TTS as TtsService

    ESP->>WSH: WS connect /ws/robot
    WSH->>SM: register(session)

    ESP->>WSH: HELLO
    WSH-->>ESP: ACK
    ESP->>WSH: AUDIO_START(sessionId, robotId, utteranceId, PCM_16BIT,16k,1ch)
    WSH->>SM: startUtterance(...)
    WSH-->>ESP: ACK

    loop QA_STEP_STREAM_MIC (VAD/AEC)
        ESP->>WSH: Binary PCM frames
        WSH->>SM: appendAudio(frame)
    end

    ESP->>WSH: AUDIO_END
    WSH->>SM: endUtterance() -> snapshot bytes
    WSH->>APS: processAudio(sessionId, robotId, utteranceId, bytes)
    APS->>STT: transcribe(audio)
    APS->>SS: getQaContext(robotId, recentSegments=4)
    alt playback state exists
        APS->>LLM: generateStoryQaReply(context + question)
    else no playback state
        APS->>LLM: generateReply(question)
    end
    APS-->>WSH: transcript + assistantReply

    WSH-->>ESP: TRANSCRIPT
    WSH-->>ESP: ASSISTANT_REPLY
    WSH->>TTS: synthesize(assistantReply)
    WSH-->>ESP: TTS_START(metadata)
    loop stream reply audio
        WSH-->>ESP: Binary audio chunk
        ESP->>ESP: enqueueAudioBytes() and play speaker
    end
    WSH-->>ESP: TTS_END
    ESP->>ESP: finishQaInterrupt() and resume STORY_PLAYING/IDLE

    opt need to cut current TTS output
        ESP->>WSH: CANCEL_OUTPUT(targetUtteranceId)
        WSH->>SM: requestCancelOutput()
        WSH-->>ESP: OUTPUT_CANCELLED
    end
```

## 4) Notes matched to code

- Firmware command pull: `pullCommandFromServer()` -> `GET /api/robots/{robotId}/commands/pull?consume=true`.
- Story playback APIs: `POST /api/stories/playback/start|next|stop`.
- Reminder audio used by firmware: `GET /api/reminders/{reminderId}/execute-audio`.
- QA WS event chain: `HELLO -> AUDIO_START -> (BIN)* -> AUDIO_END -> TRANSCRIPT/ASSISTANT_REPLY -> TTS_START -> (BIN)* -> TTS_END`.
