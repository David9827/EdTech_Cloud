# Luu Do Rieng Cho `robot2QA.ino`

Tai lieu nay mo ta luong xu ly cua firmware `robot2QA.ino` theo dung state va ham trong code.

## 1) Luu do tong quan chuong trinh

```mermaid
flowchart TD
    A[setup] --> B[initHardware]
    B --> C[ensureWifiConnected]
    C --> D[initWsQaClient]
    D --> E[initCommandPullWorker]
    E --> F[printState boot]
    F --> G[loop]

    G --> H[tickBootButtonToggle]
    H --> I{WiFi connected?}
    I -- No --> J[retry wifi + optional stats + delay]
    J --> G

    I -- Yes --> K[g_ws.loop]
    K --> L[processPendingCommands]
    L --> M[tickAutoQaTrigger]
    M --> N[read Serial q/b]
    N --> O[tickInterruptQa]
    O --> P[maybeTickFaceUi]
    P --> Q{STATE_STORY_PLAYING && !g_stopRequested?}
    Q -- Yes --> R[tickStoryPlayback]
    R --> S[delay 20ms]
    Q -- No --> S
    S --> G
```

## 2) Luong polling command (task nen)

```mermaid
flowchart TD
    A[commandPullTask loop] --> B{WiFi connected?}
    B -- No --> C[vTaskDelay COMMAND_PULL_INTERVAL_MS]
    C --> A

    B -- Yes --> D[pullCommandFromServer]
    D --> E{hasCommand?}
    E -- No --> C

    E -- Yes --> F[copy PulledCommand -> CommandMessage]
    F --> G{type STOP_STORY or REMINDER_CREATE?}
    G -- Yes --> H[g_stopRequested=true + flushAudioOutputNow + xQueueReset]
    G -- No --> I[xQueueSend command]
    H --> I
    I --> C
```

## 3) Luong xu ly command

```mermaid
flowchart TD
    A[processPendingCommands] --> B[executeCommand]
    B --> C{cmd.type}
    C -->|START_STORY| D[onStartStory]
    C -->|STOP_STORY| E[onStopStory]
    C -->|REMINDER_CREATE| F[onReminderCreate]
    C -->|REMINDER_CANCEL| G[onReminderCancel]
    C -->|CUSTOM| H[onCustomCommand -> startQaInterrupt]
    C -->|Unknown| I[log + UI error]
```

## 4) Luong doc truyen (START_STORY + NEXT)

```mermaid
flowchart TD
    A[onStartStory] --> B{storyId hop le?}
    B -- No --> C[error + return]
    B -- Yes --> D[postPlaybackStart]
    D --> E{response ok?}
    E -- No --> F[state -> IDLE]
    E -- Yes --> G{interrupted by STOP/QA?}
    G -- Yes --> H[return]
    G -- No --> I[set state STORY_PLAYING or IDLE if completed]

    I --> J[tickStoryPlayback loop]
    J --> K[postPlaybackNext]
    K --> L{ok?}
    L -- No --> J
    L -- Yes --> M{interrupted?}
    M -- Yes --> J
    M -- No --> N{completed?}
    N -- Yes --> O[state -> IDLE, clear currentStory]
    N -- No --> J
```

## 5) Audio output pipeline (HTTP/WS -> Queue -> I2S)

```mermaid
flowchart TD
    A[HTTP story/reminder response] --> B[parseAndPlayAudioResponse]
    B --> C{status}
    C -->|204| D[completed=true, ok=true]
    C -->|!=200| E[ok=false]
    C -->|200| F[read headers + stream body]
    F --> G[enqueueAudioBytes]
    G --> H[audioOutTask xQueueReceive]
    H --> I[apply gain + aec ref]
    I --> J[i2s_write speaker]

    K[WS binary TTS in onWsEvent] --> G
```

## 6) QA interrupt state machine

```mermaid
flowchart TD
    A[startQaInterrupt] --> B[flushAudioOutputNow]
    B --> C[g_state=STATE_INTERRUPT_QA]
    C --> D[g_qaStep=WAIT_WS]
    D --> E[tickInterruptQa]

    E --> F{g_wsError?}
    F -- Yes --> Z[finishQaInterrupt fail]
    F -- No --> G{qaStep}

    G -->|WAIT_WS| H[wait ws connected -> send HELLO]
    G -->|WAIT_HELLO_ACK| I[wait ACK -> send AUDIO_START]
    G -->|WAIT_AUDIO_START_ACK| J[wait ACK -> STREAM_MIC]
    G -->|STREAM_MIC| K[VAD + send BIN + send AUDIO_END]
    G -->|WAIT_TTS_END| L[wait TTS_END or timeout]

    H --> E
    I --> E
    J --> E
    K --> E
    L --> M{ttsEnd?}
    M -- Yes --> N[finishQaInterrupt success]
    M -- No --> E
```

## 7) WS event xu ly trong QA

```mermaid
flowchart TD
    A[onWsEvent] --> B{event type}
    B -->|CONNECTED| C[g_wsConnected=true]
    B -->|DISCONNECTED| D[g_wsConnected=false]
    B -->|BIN| E{state INTERRUPT_QA && step WAIT_TTS_END && !drop?}
    E -- Yes --> F[enqueue ws tts audio]
    E -- No --> G[drop]
    B -->|TEXT ACK| H[set helloAck/audioStartAck]
    B -->|TEXT TRANSCRIPT| I[save transcript]
    B -->|TEXT ASSISTANT_REPLY| J[save assistant reply]
    B -->|TEXT TTS_START| K[set active output + timeout + allow bin]
    B -->|TEXT TTS_END| L[g_wsTtsEnd=true]
    B -->|TEXT OUTPUT_CANCELLED| M[clear active output]
    B -->|TEXT ERROR| N[g_wsError=true]
```

## 8) State machine chinh

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> STORY_PLAYING: START_STORY
    STORY_PLAYING --> IDLE: story completed or STOP_STORY
    IDLE --> INTERRUPT_QA: startQaInterrupt
    STORY_PLAYING --> INTERRUPT_QA: startQaInterrupt
    INTERRUPT_QA --> STORY_PLAYING: finishQaInterrupt resume story
    INTERRUPT_QA --> IDLE: finishQaInterrupt resume idle
```
