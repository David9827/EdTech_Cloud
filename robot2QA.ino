#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <WebSocketsClient.h>
#include <SPI.h>
#include <SPIFFS.h>
#include <driver/i2s.h>
#include <string.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>

/*
  ============================================================
  ROBOTIC ESP32-S3 FIRMWARE (robotic_esp32.ino)
  ------------------------------------------------------------
  Purpose:
  - Poll command from backend
  - Execute START_STORY / STOP_STORY / REMINDER_* / CUSTOM
  - Stream story audio chunks from backend playback APIs
  - Keep a simple, clear state machine for ESP32-S3 robot

  Required Arduino libraries:
  - ArduinoJson (by Benoit Blanchon)
  - Adafruit GFX Library
  - Adafruit ST7735 and ST7789 Library
  - ESP32 core (for WiFi/HTTPClient/I2S)

  Optional extension:
  - WebSocket QA flow (stub included)
  ============================================================
*/

// ========================= 1) USER CONFIG =========================
static const char* WIFI_SSID = "cong";
static const char* WIFI_PASS = "27042004";

// Example: "http://192.168.1.20:8080"
static const char* BACKEND_BASE_URL = "http://172.20.10.2:5999";

// Robot identity from backend DB
static const char* ROBOT_ID = "4f864132-2c3f-4fff-9811-19a840e93473";
static const char* SESSION_ID = "10000000-0000-0000-0000-000000000001"; // WS session id should be UUID.

// Optional WS QA endpoint (not used by polling flow yet)
static const char* WS_HOST = "172.20.10.2";
static const uint16_t WS_PORT = 5999;

// Optional Bearer token. Keep empty if backend does not require auth.
static const char* AUTH_BEARER_TOKEN = "";

// Timeouts (ms)
static const uint32_t HTTP_CONNECT_TIMEOUT_MS = 8000;
static const uint32_t HTTP_READ_TIMEOUT_MS = 15000;
static const int AUDIO_SAMPLE_RATE = 16000;
static const int AUDIO_OUTPUT_SAMPLE_RATE = 22050;
static const uint32_t WIFI_CONNECT_ATTEMPT_MS = 15000;
static const uint32_t WIFI_RETRY_COOLDOWN_MS = 4000;
static const uint32_t QA_STEP_TIMEOUT_MS = 12000;
static const uint32_t QA_WAIT_TTS_END_MS = 25000;
static const int QA_MIC_CHUNK_BYTES = 640; // 20 ms at 16kHz mono 16-bit PCM
static const char* QA_AUDIO_FORMAT = "PCM_16BIT";
static const int QA_WAV_HEADER_BYTES = 44;
static const uint32_t QA_WAIT_SPEECH_TIMEOUT_MS = 4500;
static const uint32_t QA_FORCE_START_MS = 1600;
static const bool QA_ENABLE_FORCE_START = true;
static const uint8_t QA_FORCE_START_MIN_HITS = 2;
static const uint32_t QA_MAX_UTTERANCE_MS = 9000;
static const uint32_t QA_MIN_UTTERANCE_MS = 500;
static const uint32_t QA_END_SILENCE_MS = 850;
static const uint8_t QA_SPEECH_HIT_FRAMES = 3;
static const uint8_t QA_PREROLL_FRAMES = 12;
static const int QA_VAD_MIN_ABS = 235;
static const float QA_VAD_THRESHOLD_MULTIPLIER = 1.52f;
static const float QA_VAD_NOISE_EMA_ALPHA = 0.06f;
static const uint16_t QA_VAD_MIN_ZCR = 6;
static const uint16_t QA_VAD_MAX_ZCR = 140;
static const uint16_t QA_VAD_WEAK_MIN_ZCR = 4;
static const uint16_t QA_VAD_WEAK_MAX_ZCR = 170;
static const float QA_VAD_MAX_PEAK_TO_AVG = 14.0f;
static const bool AUTO_QA_ENABLED = true;
static const bool AUTO_QA_LISTEN_IN_IDLE = true;
static const uint32_t AUTO_QA_COOLDOWN_MS = 1200;
static const uint8_t AUTO_QA_HIT_FRAMES = 4;
static const int AUTO_QA_MIN_ABS = 230;
static const float AUTO_QA_THRESHOLD_MULTIPLIER = 1.50f;
static const float AUTO_QA_NOISE_EMA_ALPHA = 0.06f;
static const uint16_t AUTO_QA_MIN_ZCR = 7;
static const uint16_t AUTO_QA_MAX_ZCR = 145;
static const float AUTO_QA_MAX_PEAK_TO_AVG = 15.0f;
static const bool AUTO_QA_ALLOW_BARGE_IN_DURING_QA_TTS = true;
static const uint32_t AUTO_QA_QA_TTS_GUARD_MS = 350;
static const int AUTO_QA_QA_TTS_MIN_ABS = 260;
static const float AUTO_QA_QA_TTS_THRESHOLD_MULTIPLIER = 1.75f;
static const uint8_t AUTO_QA_QA_TTS_HIT_FRAMES = 5;
static const uint16_t AUDIO_OUT_CHUNK_BYTES = 512;
static const uint16_t AUDIO_OUT_QUEUE_DEPTH = 96;
static const uint32_t AUDIO_OUT_ENQUEUE_TIMEOUT_MS = 80;
static const uint32_t AUDIO_PROMPT_ENQUEUE_TIMEOUT_MS = 150;
static const uint32_t WIFI_FAIL_PROMPT_COOLDOWN_MS = 15000;
static const char* AUDIO_PROMPT_CANT_CONNECT_WIFI = "/audio/cant_connect_wifi.wav";
static const char* AUDIO_PROMPT_NO_SPEECH = "/audio/no_speech.wav";
static const char* AUDIO_PROMPT_START_SUCCESS = "/audio/start_success.wav";
static const uint32_t STATS_REFRESH_MS = 800;
static const uint32_t BOOT_BUTTON_DEBOUNCE_MS = 45;
static const float AUDIO_OUTPUT_GAIN = 0.9f; // 1.0 = original volume
static const uint32_t COMMAND_PULL_INTERVAL_MS = 1000;
static const uint8_t COMMAND_QUEUE_DEPTH = 8;

// ========================= 1.1) PIN MAP (from robot_example.ino) =========================
#define I2S_MIC_WS 6
#define I2S_MIC_SCK 5
#define I2S_MIC_SD 4

#define I2S_SPK_DIN 16
#define I2S_SPK_BCLK 15
#define I2S_SPK_LRC 7

#define TFT_SCLK 10
#define TFT_MOSI 11
#define TFT_RST 12
#define TFT_DC 13
#define TFT_BLK 14
#define TFT_CS 9

#define LED_R 47
#define LED_G 39
#define LED_B 21
#define BOOT_BUTTON_PIN 0
static const bool BOOT_BUTTON_ACTIVE_LOW = true;

// ========================= 2) APP STATE =========================
enum RobotState {
  STATE_IDLE = 0,
  STATE_STORY_PLAYING = 1,
  STATE_INTERRUPT_QA = 2
};

RobotState g_state = STATE_IDLE;
RobotState g_resumeStateAfterQa = STATE_IDLE;

String g_currentStoryId;
bool g_storyCompleted = false;

unsigned long g_lastWifiAttemptMs = 0;
uint32_t g_wifiRetryCount = 0;
bool g_wifiWasConnected = false;
Adafruit_ST7789 g_tft = Adafruit_ST7789(&SPI, TFT_CS, TFT_DC, TFT_RST);
bool g_tftReady = false;
bool g_i2sTxReady = false;
unsigned long g_lastUiTextMs = 0;
bool g_textUiEnabled = false;
bool g_wifiUiEnabled = true;
bool g_hasEverWifiConnected = false;

static const uint16_t EYE_COLOR = ST77XX_CYAN;
static const uint16_t EYE_BG_COLOR = ST77XX_BLACK;
static const uint16_t EYE_TEXT_HOLD_MS = 1200;

struct EyeFrame {
  int offsetX;
  int offsetY;
  int height;
  bool isHappy;
  int browLift;
  int browTilt;
  uint16_t holdMs;
};

const EyeFrame kIdleEyeFrames[] = {
  {0, 0, 90, false, 0, 2, 1800},
  {0, 0, 10, false, 3, 0, 140},
  {0, 0, 90, false, 1, 3, 750},
  {-25, 0, 90, false, 2, 3, 950},
  {25, 0, 90, false, 2, 3, 950},
  {0, 0, 90, false, 0, 2, 500},
  {0, 0, 10, false, 3, 0, 100},
  {0, 0, 90, false, 1, 2, 100},
  {0, 0, 10, false, 3, 0, 100},
  {0, 0, 90, false, 0, 2, 500},
  {0, 0, 90, true, 4, -1, 1800}
};

bool g_eyeAnimActive = false;
uint8_t g_eyeFrameIndex = 0;
unsigned long g_eyeFrameStartMs = 0;
bool g_listeningFaceShown = false;
bool g_neutralFaceShown = false;
uint8_t g_listeningBrowPhase = 0;
unsigned long g_listeningFaceLastMs = 0;

enum QaStep {
  QA_STEP_IDLE = 0,
  QA_STEP_WAIT_WS = 1,
  QA_STEP_WAIT_HELLO_ACK = 2,
  QA_STEP_WAIT_AUDIO_START_ACK = 3,
  QA_STEP_STREAM_MIC = 4,
  QA_STEP_WAIT_TTS_END = 5
};

QaStep g_qaStep = QA_STEP_IDLE;
unsigned long g_qaStepStartMs = 0;
unsigned long g_qaRecordStartMs = 0;
uint32_t g_qaUtteranceSeq = 0;
String g_qaUtteranceId;

WebSocketsClient g_ws;
bool g_wsInitialized = false;
bool g_wsConnected = false;
bool g_wsHelloAck = false;
bool g_wsAudioStartAck = false;
bool g_wsTtsEnd = false;
bool g_wsError = false;
String g_wsErrorCode;
String g_wsErrorMessage;
String g_wsLastTranscript;
String g_wsLastAssistantReply;
String g_wsActiveOutputUtteranceId;
int g_wsSkipAudioHeaderBytes = 0;
unsigned long g_wsTtsStartMs = 0;
uint32_t g_qaTtsWaitTimeoutMs = QA_WAIT_TTS_END_MS;

bool g_qaSpeechStarted = false;
unsigned long g_qaSpeechStartMs = 0;
unsigned long g_qaLastSpeechMs = 0;
uint8_t g_qaSpeechHitCount = 0;
float g_qaNoiseEma = 300.0f;
uint32_t g_qaSentAudioBytes = 0;
uint8_t g_qaPreRollBuf[QA_PREROLL_FRAMES][QA_MIC_CHUNK_BYTES];
uint16_t g_qaPreRollSize[QA_PREROLL_FRAMES] = {0};
uint8_t g_qaPreRollWriteIdx = 0;
uint8_t g_qaPreRollCount = 0;
bool g_wsDropBinaryAudio = false;
volatile bool g_stopRequested = false;

float g_autoQaNoiseEma = 300.0f;
uint8_t g_autoQaSpeechHits = 0;
unsigned long g_lastQaFinishMs = 0;
bool g_spiffsReady = false;
unsigned long g_lastWifiFailPromptMs = 0;
bool g_statsOverlayEnabled = false;
unsigned long g_statsLastDrawMs = 0;
bool g_bootButtonRawReleased = true;
bool g_bootButtonStableReleased = true;
unsigned long g_bootButtonLastChangeMs = 0;

struct AudioOutChunk {
  uint16_t len = 0;
  uint8_t data[AUDIO_OUT_CHUNK_BYTES];
};

QueueHandle_t g_audioOutQueue = nullptr;
TaskHandle_t g_audioOutTaskHandle = nullptr;
QueueHandle_t g_commandQueue = nullptr;
TaskHandle_t g_commandPullTaskHandle = nullptr;

// ========================= 3) DATA MODELS =========================
struct PulledCommand {
  bool hasCommand = false;
  String commandId;
  String type;      // START_STORY, STOP_STORY, REMINDER_CREATE, ...
  String storyId;
  String reminderId;
};

struct CommandMessage {
  bool hasCommand = false;
  char commandId[64];
  char type[32];
  char storyId[64];
  char reminderId[64];
};

struct PlaybackResponse {
  bool ok = false;
  bool interrupted = false;
  int httpCode = 0;
  bool completed = false;
  String mimeType;
  int sampleRate = 0;
  int channels = 0;
  int segmentOrder = -1;
  int bytesLength = 0;
};

bool pullCommandFromServer(PulledCommand& outCmd);
void executeCommand(const PulledCommand& cmd);
bool isCurrentQaUtterance(const DynamicJsonDocument& doc);
void drawRobotEyebrows(int leftX, int rightX, int eyeWidth, int eyeTopY, int browLift, int browTilt, bool isHappy);
void drawRobotEyes(int offsetX, int offsetY, int height, bool isHappy, int browLift = 0, int browTilt = 2);
void drawListeningEyes(uint8_t phase);
void tickEyeAnimation();
void tickFaceUi();
void pumpUiAndControlDuringBlockingWork();
void initSpiffsStorage();
bool playWavPromptFromSpiffs(const char* path, bool allowAbort);
void playCantConnectWifiPrompt();
bool playNoSpeechPrompt();
void playStartSuccessPrompt();
bool shouldAbortCurrentOutput();
void startQaInterrupt(const char* trigger);
bool sendWsAudioStart();
bool sendWsAudioEnd();
void finishQaInterrupt(bool success, const char* reason);
uint32_t estimateWavPromptDurationMs(const char* path);
void waitWithWsPump(uint32_t waitMs);
void restartQaListeningAfterNoSpeech(const char* reasonTag);
bool isWsNoSpeechError();
void initBootButtonToggle();
void tickBootButtonToggle();
void toggleStatsOverlay();
void drawStatsOverlay(bool forceDraw);
bool isBootButtonReleased();

// ========================= 4) HW INIT + UI =========================
void setStatusLed(bool r, bool g, bool b) {
  digitalWrite(LED_R, r ? HIGH : LOW);
  digitalWrite(LED_G, g ? HIGH : LOW);
  digitalWrite(LED_B, b ? HIGH : LOW);
}

void setStatusColor(uint16_t color) {
  setStatusLed(color == ST77XX_RED, color == ST77XX_GREEN, color == ST77XX_BLUE);
}

void showTextOnTft(const String& message, uint16_t color = ST77XX_WHITE) {
  if (!g_tftReady || !g_textUiEnabled) {
    return;
  }

  g_eyeAnimActive = false;
  g_lastUiTextMs = millis();
  g_tft.fillScreen(ST77XX_BLACK);
  g_tft.setCursor(0, 30);
  g_tft.setTextColor(color);
  g_tft.setTextWrap(true);
  g_tft.println(message);
}

void showWifiStatusOnTft(const String& message, uint16_t color = ST77XX_WHITE) {
  if (!g_tftReady || !g_wifiUiEnabled) {
    return;
  }

  g_tft.fillScreen(ST77XX_BLACK);
  g_tft.setCursor(0, 30);
  g_tft.setTextColor(color);
  g_tft.setTextWrap(true);
  g_tft.println(message);
}

void onFirstWifiConnected() {
  if (g_hasEverWifiConnected) {
    return;
  }

  g_hasEverWifiConnected = true;
  g_wifiUiEnabled = false;
  g_textUiEnabled = false;
  g_eyeAnimActive = false;
  g_listeningFaceShown = false;
  g_neutralFaceShown = false;
  const unsigned long now = millis();
  g_lastUiTextMs = (now > EYE_TEXT_HOLD_MS) ? (now - EYE_TEXT_HOLD_MS) : 0;
  if (g_tftReady) {
    g_tft.fillScreen(EYE_BG_COLOR);
  }
}

void drawRobotEyebrows(int leftX, int rightX, int eyeWidth, int eyeTopY, int browLift, int browTilt, bool isHappy) {
  int lift = browLift;
  int tilt = browTilt;
  if (lift < -4) {
    lift = -4;
  } else if (lift > 8) {
    lift = 8;
  }
  if (tilt < -8) {
    tilt = -8;
  } else if (tilt > 8) {
    tilt = 8;
  }
  if (isHappy && tilt > 0) {
    tilt = 0;
  }

  const int browBaseY = eyeTopY - 18 - lift;
  const int browXPad = 5;
  const int browWidth = eyeWidth - 10;
  const int browThickness = 4;
  const int leftBrowX = leftX + browXPad;
  const int rightBrowX = rightX + browXPad;

  for (int i = 0; i < browThickness; i++) {
    g_tft.drawLine(leftBrowX, browBaseY + i, leftBrowX + browWidth, browBaseY + i + tilt, EYE_COLOR);
    g_tft.drawLine(rightBrowX, browBaseY + i + tilt, rightBrowX + browWidth, browBaseY + i, EYE_COLOR);
  }
}

void drawRobotEyes(int offsetX, int offsetY, int height, bool isHappy, int browLift, int browTilt) {
  if (!g_tftReady) {
    return;
  }

  if (height < 6) {
    height = 6;
  } else if (height > 90) {
    height = 90;
  }

  const int eyeWidth = 60;
  const int normalHeight = 90;
  const int leftX = 40 + offsetX;
  const int rightX = 140 + offsetX;
  const int y = 75 + offsetY + (normalHeight - height) / 2;

  // Chi xoa khu vuc mat de giam giat man hinh.
  g_tft.fillRect(0, 20, 240, 200, EYE_BG_COLOR);

  g_tft.fillRoundRect(leftX, y, eyeWidth, height, 20, EYE_COLOR);
  g_tft.fillRoundRect(rightX, y, eyeWidth, height, 20, EYE_COLOR);

  if (isHappy) {
    g_tft.fillCircle(leftX + (eyeWidth / 2), y + height, 40, EYE_BG_COLOR);
    g_tft.fillCircle(rightX + (eyeWidth / 2), y + height, 40, EYE_BG_COLOR);
  }

  drawRobotEyebrows(leftX, rightX, eyeWidth, y, browLift, browTilt, isHappy);
}

void drawListeningEyes(uint8_t phase) {
  const int lift = (phase == 1) ? 3 : 2;
  const int tilt = (phase == 2) ? 5 : 4;
  drawRobotEyes(0, 0, 68, false, lift, tilt);
}

void tickEyeAnimation() {
  if (!g_tftReady) {
    return;
  }

  if (g_state == STATE_INTERRUPT_QA) {
    g_eyeAnimActive = false;
    return;
  }

  const unsigned long now = millis();
  if ((now - g_lastUiTextMs) < EYE_TEXT_HOLD_MS) {
    return;
  }

  if (!g_eyeAnimActive) {
    g_eyeAnimActive = true;
    g_eyeFrameIndex = 0;
    g_eyeFrameStartMs = now;
    const EyeFrame& frame = kIdleEyeFrames[g_eyeFrameIndex];
    drawRobotEyes(frame.offsetX, frame.offsetY, frame.height, frame.isHappy, frame.browLift, frame.browTilt);
    return;
  }

  const EyeFrame& frame = kIdleEyeFrames[g_eyeFrameIndex];
  if ((now - g_eyeFrameStartMs) < frame.holdMs) {
    return;
  }

  g_eyeFrameIndex++;
  if (g_eyeFrameIndex >= (sizeof(kIdleEyeFrames) / sizeof(kIdleEyeFrames[0]))) {
    g_eyeFrameIndex = 0;
  }
  g_eyeFrameStartMs = now;

  const EyeFrame& nextFrame = kIdleEyeFrames[g_eyeFrameIndex];
  drawRobotEyes(nextFrame.offsetX, nextFrame.offsetY, nextFrame.height, nextFrame.isHappy, nextFrame.browLift, nextFrame.browTilt);
}

void tickFaceUi() {
  if (!g_tftReady) {
    return;
  }

  if (g_statsOverlayEnabled) {
    drawStatsOverlay(false);
    return;
  }

  if (!g_hasEverWifiConnected) {
    return;
  }

  if (g_state == STATE_INTERRUPT_QA && g_qaStep == QA_STEP_STREAM_MIC) {
    const unsigned long now = millis();
    if (!g_listeningFaceShown || (now - g_listeningFaceLastMs) >= 220) {
      g_eyeAnimActive = false;
      drawListeningEyes(g_listeningBrowPhase);
      g_listeningFaceShown = true;
      g_neutralFaceShown = false;
      g_listeningFaceLastMs = now;
      g_listeningBrowPhase = (g_listeningBrowPhase + 1) % 3;
    }
    return;
  }

  g_listeningFaceShown = false;

  if (g_state == STATE_IDLE || g_state == STATE_STORY_PLAYING) {
    g_neutralFaceShown = false;
    tickEyeAnimation();
    return;
  }

  if (!g_neutralFaceShown) {
    g_eyeAnimActive = false;
    drawRobotEyes(0, 0, 90, false, 0, 2);
    g_neutralFaceShown = true;
  }
}

void initI2S() {
  i2s_config_t txConfig = {};
  txConfig.mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX);
  txConfig.sample_rate = AUDIO_OUTPUT_SAMPLE_RATE;
  txConfig.bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT;
  txConfig.channel_format = I2S_CHANNEL_FMT_ONLY_LEFT;
  txConfig.communication_format = I2S_COMM_FORMAT_STAND_I2S;
  txConfig.intr_alloc_flags = ESP_INTR_FLAG_LEVEL1;
  txConfig.dma_buf_count = 8;
  txConfig.dma_buf_len = 64;
  txConfig.use_apll = false;
  txConfig.tx_desc_auto_clear = true;
  txConfig.fixed_mclk = 0;

  esp_err_t err = i2s_driver_install(I2S_NUM_0, &txConfig, 0, nullptr);
  if (err != ESP_OK) {
    Serial.printf("[I2S] TX driver install failed: %d\n", (int)err);
    return;
  }

  i2s_pin_config_t txPins = {};
  txPins.bck_io_num = I2S_SPK_BCLK;
  txPins.ws_io_num = I2S_SPK_LRC;
  txPins.data_out_num = I2S_SPK_DIN;
  txPins.data_in_num = I2S_PIN_NO_CHANGE;

  err = i2s_set_pin(I2S_NUM_0, &txPins);
  if (err != ESP_OK) {
    Serial.printf("[I2S] TX set pin failed: %d\n", (int)err);
    return;
  }

  i2s_config_t rxConfig = {};
  rxConfig.mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX);
  rxConfig.sample_rate = AUDIO_SAMPLE_RATE;
  rxConfig.bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT;
  rxConfig.channel_format = I2S_CHANNEL_FMT_ONLY_LEFT;
  rxConfig.communication_format = I2S_COMM_FORMAT_STAND_I2S;
  rxConfig.intr_alloc_flags = ESP_INTR_FLAG_LEVEL1;
  rxConfig.dma_buf_count = 8;
  rxConfig.dma_buf_len = 64;
  rxConfig.use_apll = false;
  rxConfig.tx_desc_auto_clear = false;
  rxConfig.fixed_mclk = 0;

  err = i2s_driver_install(I2S_NUM_1, &rxConfig, 0, nullptr);
  if (err == ESP_OK) {
    i2s_pin_config_t rxPins = {};
    rxPins.bck_io_num = I2S_MIC_SCK;
    rxPins.ws_io_num = I2S_MIC_WS;
    rxPins.data_out_num = I2S_PIN_NO_CHANGE;
    rxPins.data_in_num = I2S_MIC_SD;

    err = i2s_set_pin(I2S_NUM_1, &rxPins);
  }

  if (err != ESP_OK) {
    Serial.printf("[I2S] RX init failed: %d (QA mic stream disabled)\n", (int)err);
  }

  g_i2sTxReady = true;
  Serial.println("[I2S] TX ready");
}

void initHardware() {
  pinMode(LED_R, OUTPUT);
  pinMode(LED_G, OUTPUT);
  pinMode(LED_B, OUTPUT);
  setStatusColor(ST77XX_RED);

  pinMode(TFT_BLK, OUTPUT);
  digitalWrite(TFT_BLK, HIGH);
  SPI.begin(TFT_SCLK, -1, TFT_MOSI, TFT_CS);
  g_tft.init(240, 240, SPI_MODE3);
  g_tft.setRotation(1);
  g_tft.setTextSize(2);
  g_tft.setTextColor(ST77XX_WHITE);
  g_tftReady = true;

  initBootButtonToggle();
  initI2S();
  initAudioOutputPipeline();
  initSpiffsStorage();
}

// ========================= 5) UTIL HELPERS =========================
String buildUrl(const String& path) {
  String url = String(BACKEND_BASE_URL);
  if (url.endsWith("/")) {
    url.remove(url.length() - 1);
  }
  return url + path;
}

void addJsonHeaders(HTTPClient& http) {
  http.addHeader("Content-Type", "application/json");
  if (strlen(AUTH_BEARER_TOKEN) > 0) {
    http.addHeader("Authorization", String("Bearer ") + AUTH_BEARER_TOKEN);
  }
}

void printState(const char* reason) {
  Serial.printf("[STATE] %s | state=%d | story=%s\n", reason, (int)g_state, g_currentStoryId.c_str());

  if (g_state == STATE_IDLE) {
    setStatusColor(ST77XX_GREEN);
    showTextOnTft(String("IDLE\n") + reason);
  } else if (g_state == STATE_STORY_PLAYING) {
    setStatusColor(ST77XX_BLUE);
    showTextOnTft(String("PLAYING\n") + g_currentStoryId);
  } else {
    setStatusColor(ST77XX_BLUE);
    showTextOnTft("QA Interrupt...");
  }
}

void consumeBody(HTTPClient& http) {
  WiFiClient* stream = http.getStreamPtr();
  if (stream == nullptr) {
    return;
  }
  while (http.connected() && (stream->available() > 0)) {
    stream->read();
  }
}

void pumpUiAndControlDuringBlockingWork() {
  tickBootButtonToggle();
  if (g_wsInitialized) {
    g_ws.loop();
  }

  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == 'q' || c == 'Q') {
      startQaInterrupt("serial");
    } else if (c == 'b' || c == 'B') {
      toggleStatsOverlay();
    }
  }

  tickFaceUi();
}

// ========================= 5.1) AUDIO OUTPUT PIPELINE =========================
void audioOutTask(void* pvParameters) {
  (void)pvParameters;
  AudioOutChunk chunk;

  for (;;) {
    if (xQueueReceive(g_audioOutQueue, &chunk, portMAX_DELAY) != pdTRUE) {
      continue;
    }

    if (!g_i2sTxReady || chunk.len == 0) {
      continue;
    }

    if (AUDIO_OUTPUT_GAIN > 1.001f && chunk.len >= 2) {
      for (size_t i = 0; i + 1 < chunk.len; i += 2) {
        int16_t sample = (int16_t)((uint16_t)chunk.data[i] | ((uint16_t)chunk.data[i + 1] << 8));
        int32_t boosted = (int32_t)((float)sample * AUDIO_OUTPUT_GAIN);
        if (boosted > 32767) {
          boosted = 32767;
        } else if (boosted < -32768) {
          boosted = -32768;
        }
        uint16_t raw = (uint16_t)((int16_t)boosted);
        chunk.data[i] = (uint8_t)(raw & 0xFF);
        chunk.data[i + 1] = (uint8_t)((raw >> 8) & 0xFF);
      }
    }

    size_t written = 0;
    i2s_write(I2S_NUM_0, chunk.data, chunk.len, &written, portMAX_DELAY);
  }
}

void initAudioOutputPipeline() {
  if (g_audioOutQueue != nullptr) {
    return;
  }

  g_audioOutQueue = xQueueCreate(AUDIO_OUT_QUEUE_DEPTH, sizeof(AudioOutChunk));
  if (g_audioOutQueue == nullptr) {
    Serial.println("[AUDIO] queue create failed");
    return;
  }

  BaseType_t ok = xTaskCreatePinnedToCore(
      audioOutTask,
      "audio_out",
      4096,
      nullptr,
      1,
      &g_audioOutTaskHandle,
      1);

  if (ok != pdPASS) {
    Serial.println("[AUDIO] task create failed");
    g_audioOutTaskHandle = nullptr;
    vQueueDelete(g_audioOutQueue);
    g_audioOutQueue = nullptr;
    return;
  }

  Serial.printf("[AUDIO] queue ready depth=%u chunk=%u\n",
                (unsigned)AUDIO_OUT_QUEUE_DEPTH,
                (unsigned)AUDIO_OUT_CHUNK_BYTES);
}

void flushAudioOutputNow() {
  if (g_audioOutQueue != nullptr) {
    xQueueReset(g_audioOutQueue);
  }
  if (g_i2sTxReady) {
    i2s_zero_dma_buffer(I2S_NUM_0);
  }
}

bool enqueueAudioBytes(const uint8_t* data, size_t bytesLen, uint32_t waitMs = AUDIO_OUT_ENQUEUE_TIMEOUT_MS) {
  if (data == nullptr || bytesLen == 0) {
    return true;
  }

  if (g_audioOutQueue == nullptr) {
    return false;
  }

  size_t offset = 0;
  TickType_t waitTicks = pdMS_TO_TICKS(waitMs);
  while (offset < bytesLen) {
    AudioOutChunk chunk;
    size_t n = bytesLen - offset;
    if (n > AUDIO_OUT_CHUNK_BYTES) {
      n = AUDIO_OUT_CHUNK_BYTES;
    }

    chunk.len = (uint16_t)n;
    memcpy(chunk.data, data + offset, n);

    if (xQueueSend(g_audioOutQueue, &chunk, waitTicks) != pdTRUE) {
      return false;
    }
    offset += n;
  }

  return true;
}

void initSpiffsStorage() {
  if (g_spiffsReady) {
    return;
  }
  g_spiffsReady = SPIFFS.begin(true);
  if (!g_spiffsReady) {
    Serial.println("[SPIFFS] mount failed");
    return;
  }

  Serial.println("[SPIFFS] mounted");
  Serial.printf("[SPIFFS] prompts: wifi=%d no_speech=%d start=%d\n",
                SPIFFS.exists(AUDIO_PROMPT_CANT_CONNECT_WIFI) ? 1 : 0,
                SPIFFS.exists(AUDIO_PROMPT_NO_SPEECH) ? 1 : 0,
                SPIFFS.exists(AUDIO_PROMPT_START_SUCCESS) ? 1 : 0);
}

bool playWavPromptFromSpiffs(const char* path, bool allowAbort) {
  if (!g_spiffsReady || path == nullptr || path[0] == '\0') {
    return false;
  }
  if (g_audioOutQueue == nullptr || !g_i2sTxReady) {
    return false;
  }

  File file = SPIFFS.open(path, FILE_READ);
  if (!file) {
    Serial.printf("[AUDIO] prompt missing: %s\n", path);
    return false;
  }

  uint8_t buf[512];
  int skipHeader = QA_WAV_HEADER_BYTES;
  size_t totalQueued = 0;
  bool enqueueFailed = false;

  while (file.available()) {
    if (allowAbort && shouldAbortCurrentOutput()) {
      break;
    }
    pumpUiAndControlDuringBlockingWork();

    int n = file.read(buf, sizeof(buf));
    if (n <= 0) {
      break;
    }

    int offset = 0;
    if (skipHeader > 0) {
      int drop = n > skipHeader ? skipHeader : n;
      skipHeader -= drop;
      offset = drop;
    }

    int audioBytes = n - offset;
    if (audioBytes > 0) {
      if (!enqueueAudioBytes(buf + offset, (size_t)audioBytes, AUDIO_PROMPT_ENQUEUE_TIMEOUT_MS)) {
        enqueueFailed = true;
        break;
      }
      totalQueued += (size_t)audioBytes;
    }
  }

  file.close();
  if (enqueueFailed) {
    Serial.printf("[AUDIO] prompt enqueue failed: %s\n", path);
    return false;
  }
  if (allowAbort && shouldAbortCurrentOutput()) {
    return false;
  }
  Serial.printf("[AUDIO] prompt queued: %s bytes=%u\n", path, (unsigned)totalQueued);
  return totalQueued > 0;
}

void playCantConnectWifiPrompt() {
  unsigned long now = millis();
  if (g_lastWifiFailPromptMs != 0 && (now - g_lastWifiFailPromptMs) < WIFI_FAIL_PROMPT_COOLDOWN_MS) {
    return;
  }
  g_lastWifiFailPromptMs = now;
  flushAudioOutputNow();
  playWavPromptFromSpiffs(AUDIO_PROMPT_CANT_CONNECT_WIFI, false);
}

bool playNoSpeechPrompt() {
  return playWavPromptFromSpiffs(AUDIO_PROMPT_NO_SPEECH, false);
}

void playStartSuccessPrompt() {
  flushAudioOutputNow();
  playWavPromptFromSpiffs(AUDIO_PROMPT_START_SUCCESS, false);
}

void initBootButtonToggle() {
  pinMode(BOOT_BUTTON_PIN, INPUT_PULLUP);
  bool released = isBootButtonReleased();
  g_bootButtonRawReleased = released;
  g_bootButtonStableReleased = released;
  g_bootButtonLastChangeMs = millis();
}

void drawStatsOverlay(bool forceDraw) {
  if (!g_tftReady || !g_statsOverlayEnabled) {
    return;
  }

  unsigned long now = millis();
  if (!forceDraw && g_statsLastDrawMs != 0 && (now - g_statsLastDrawMs) < STATS_REFRESH_MS) {
    return;
  }
  g_statsLastDrawMs = now;

  uint32_t cpuMhz = (uint32_t)ESP.getCpuFreqMHz();
  uint32_t heapTotalKb = (uint32_t)(ESP.getHeapSize() / 1024U);
  uint32_t heapFreeKb = (uint32_t)(ESP.getFreeHeap() / 1024U);
  uint32_t heapUsedKb = heapTotalKb > heapFreeKb ? (heapTotalKb - heapFreeKb) : 0;
  uint32_t heapPct = heapTotalKb == 0 ? 0 : (heapUsedKb * 100U) / heapTotalKb;

  uint32_t psTotalKb = (uint32_t)(ESP.getPsramSize() / 1024U);
  uint32_t psFreeKb = (uint32_t)(ESP.getFreePsram() / 1024U);
  uint32_t psUsedKb = psTotalKb > psFreeKb ? (psTotalKb - psFreeKb) : 0;
  uint32_t psPct = psTotalKb == 0 ? 0 : (psUsedKb * 100U) / psTotalKb;

  uint32_t diskTotalKb = 0;
  uint32_t diskUsedKb = 0;
  uint32_t diskPct = 0;
  if (g_spiffsReady) {
    diskTotalKb = (uint32_t)(SPIFFS.totalBytes() / 1024U);
    diskUsedKb = (uint32_t)(SPIFFS.usedBytes() / 1024U);
    diskPct = diskTotalKb == 0 ? 0 : (diskUsedKb * 100U) / diskTotalKb;
  }

  uint32_t uptimeSec = now / 1000U;
  uint32_t hh = uptimeSec / 3600U;
  uint32_t mm = (uptimeSec % 3600U) / 60U;
  uint32_t ss = uptimeSec % 60U;

  g_tft.fillScreen(ST77XX_BLACK);
  g_tft.setTextSize(2);
  g_tft.setTextColor(ST77XX_GREEN);
  g_tft.setCursor(0, 0);

  char line[64];
  snprintf(line, sizeof(line), "STATS (BOOT: toggle)");
  g_tft.println(line);

  snprintf(line, sizeof(line), "CPU: %lu MHz", (unsigned long)cpuMhz);
  g_tft.println(line);

  snprintf(line, sizeof(line), "RAM: %lu/%lu KB (%lu%%)",
           (unsigned long)heapUsedKb,
           (unsigned long)heapTotalKb,
           (unsigned long)heapPct);
  g_tft.println(line);

  if (psTotalKb > 0) {
    snprintf(line, sizeof(line), "PSRAM: %lu/%lu KB (%lu%%)",
             (unsigned long)psUsedKb,
             (unsigned long)psTotalKb,
             (unsigned long)psPct);
  } else {
    snprintf(line, sizeof(line), "PSRAM: N/A");
  }
  g_tft.println(line);

  if (g_spiffsReady) {
    snprintf(line, sizeof(line), "DISK: %lu/%lu KB (%lu%%)",
             (unsigned long)diskUsedKb,
             (unsigned long)diskTotalKb,
             (unsigned long)diskPct);
  } else {
    snprintf(line, sizeof(line), "DISK: SPIFFS not mounted");
  }
  g_tft.println(line);

  snprintf(line, sizeof(line), "UP: %02lu:%02lu:%02lu",
           (unsigned long)hh,
           (unsigned long)mm,
           (unsigned long)ss);
  g_tft.println(line);

  snprintf(line, sizeof(line), "STATE=%d QA=%d WiFi=%s",
           (int)g_state,
           (int)g_qaStep,
           WiFi.status() == WL_CONNECTED ? "OK" : "DOWN");
  g_tft.println(line);
  g_tft.setTextSize(2);
}

void toggleStatsOverlay() {
  g_statsOverlayEnabled = !g_statsOverlayEnabled;
  g_statsLastDrawMs = 0;

  if (g_statsOverlayEnabled) {
    g_eyeAnimActive = false;
    g_neutralFaceShown = false;
    g_listeningFaceShown = false;
    drawStatsOverlay(true);
    Serial.println("[UI] Stats overlay ON");
    return;
  }

  if (g_tftReady) {
    g_tft.fillScreen(EYE_BG_COLOR);
  }
  g_eyeAnimActive = false;
  g_neutralFaceShown = false;
  g_listeningFaceShown = false;
  Serial.println("[UI] Stats overlay OFF");
}

void tickBootButtonToggle() {
  bool rawReleased = isBootButtonReleased();
  unsigned long now = millis();

  if (rawReleased != g_bootButtonRawReleased) {
    g_bootButtonRawReleased = rawReleased;
    g_bootButtonLastChangeMs = now;
  }

  if ((now - g_bootButtonLastChangeMs) < BOOT_BUTTON_DEBOUNCE_MS) {
    return;
  }

  if (g_bootButtonStableReleased != g_bootButtonRawReleased) {
    bool wasReleased = g_bootButtonStableReleased;
    g_bootButtonStableReleased = g_bootButtonRawReleased;
    if (!wasReleased && g_bootButtonStableReleased) {
      toggleStatsOverlay();
    }
  }
}

bool isBootButtonReleased() {
  int level = digitalRead(BOOT_BUTTON_PIN);
  if (BOOT_BUTTON_ACTIVE_LOW) {
    return level == HIGH;
  }
  return level == LOW;
}

uint32_t estimateWavPromptDurationMs(const char* path) {
  if (!g_spiffsReady || path == nullptr || path[0] == '\0') {
    return 0;
  }

  File file = SPIFFS.open(path, FILE_READ);
  if (!file) {
    return 0;
  }

  size_t bytes = file.size();
  file.close();
  if (bytes <= (size_t)QA_WAV_HEADER_BYTES) {
    return 0;
  }

  size_t pcmBytes = bytes - (size_t)QA_WAV_HEADER_BYTES;
  uint32_t bytesPerSec = (uint32_t)AUDIO_SAMPLE_RATE * 2U; // mono 16-bit PCM
  if (bytesPerSec == 0) {
    return 0;
  }
  return (uint32_t)(((uint64_t)pcmBytes * 1000ULL) / bytesPerSec);
}

void waitWithWsPump(uint32_t waitMs) {
  unsigned long start = millis();
  while ((millis() - start) < waitMs) {
    pumpUiAndControlDuringBlockingWork();
    delay(10);
  }
}

void restartQaListeningAfterNoSpeech(const char* reasonTag) {
  sendWsAudioEnd();
  flushAudioOutputNow();

  bool promptPlayed = playNoSpeechPrompt();
  uint32_t promptMs = promptPlayed ? estimateWavPromptDurationMs(AUDIO_PROMPT_NO_SPEECH) : 0;
  if (promptMs < 250) {
    promptMs = 250;
  }
  waitWithWsPump(promptMs + 120);

  if (g_stopRequested) {
    finishQaInterrupt(false, "qa_stop_requested");
    return;
  }

  const char* finishReason = reasonTag == nullptr ? "qa_no_speech_prompted" : reasonTag;
  Serial.printf("[QA] Prompted no-speech then finish: %s\n", finishReason);
  finishQaInterrupt(true, finishReason);
}

bool isWsNoSpeechError() {
  String code = g_wsErrorCode;
  String message = g_wsErrorMessage;
  code.toLowerCase();
  message.toLowerCase();

  if (code == "no_audio") {
    return true;
  }
  if (code == "pipeline_error") {
    if (message.indexOf("empty transcript") >= 0) {
      return true;
    }
    if (message.indexOf("no speech") >= 0) {
      return true;
    }
    if (message.indexOf("blank audio") >= 0) {
      return true;
    }
  }
  return false;
}

void copyStringToBuf(char* dst, size_t dstSize, const String& src) {
  if (dst == nullptr || dstSize == 0) {
    return;
  }
  size_t n = src.length();
  if (n >= dstSize) {
    n = dstSize - 1;
  }
  memcpy(dst, src.c_str(), n);
  dst[n] = '\0';
}

void commandPullTask(void* pvParameters) {
  (void)pvParameters;
  for (;;) {
    if (WiFi.status() == WL_CONNECTED) {
      PulledCommand pulled;
      bool ok = pullCommandFromServer(pulled);
      if (ok && pulled.hasCommand && g_commandQueue != nullptr) {
        CommandMessage msg = {};
        msg.hasCommand = true;
        copyStringToBuf(msg.commandId, sizeof(msg.commandId), pulled.commandId);
        copyStringToBuf(msg.type, sizeof(msg.type), pulled.type);
        copyStringToBuf(msg.storyId, sizeof(msg.storyId), pulled.storyId);
        copyStringToBuf(msg.reminderId, sizeof(msg.reminderId), pulled.reminderId);

        if (pulled.type == "STOP_STORY") {
          g_stopRequested = true;
          flushAudioOutputNow();
          xQueueReset(g_commandQueue);
        }

        if (xQueueSend(g_commandQueue, &msg, 0) != pdTRUE) {
          CommandMessage dropped;
          xQueueReceive(g_commandQueue, &dropped, 0);
          xQueueSend(g_commandQueue, &msg, 0);
        }
      }
    }

    vTaskDelay(pdMS_TO_TICKS(COMMAND_PULL_INTERVAL_MS));
  }
}

void initCommandPullWorker() {
  if (g_commandQueue != nullptr) {
    return;
  }

  g_commandQueue = xQueueCreate(COMMAND_QUEUE_DEPTH, sizeof(CommandMessage));
  if (g_commandQueue == nullptr) {
    Serial.println("[CMD] queue create failed");
    return;
  }

  BaseType_t ok = xTaskCreatePinnedToCore(
      commandPullTask,
      "cmd_pull",
      6144,
      nullptr,
      1,
      &g_commandPullTaskHandle,
      0);
  if (ok != pdPASS) {
    Serial.println("[CMD] pull task create failed");
    vQueueDelete(g_commandQueue);
    g_commandQueue = nullptr;
    g_commandPullTaskHandle = nullptr;
    return;
  }

  Serial.printf("[CMD] pull task ready intervalMs=%lu\n", (unsigned long)COMMAND_PULL_INTERVAL_MS);
}

void processPendingCommands() {
  if (g_commandQueue == nullptr) {
    return;
  }

  CommandMessage msg;
  while (xQueueReceive(g_commandQueue, &msg, 0) == pdTRUE) {
    PulledCommand cmd;
    cmd.hasCommand = msg.hasCommand;
    cmd.commandId = String(msg.commandId);
    cmd.type = String(msg.type);
    cmd.storyId = String(msg.storyId);
    cmd.reminderId = String(msg.reminderId);
    executeCommand(cmd);
  }
}

// ========================= 6) WIFI =========================
const char* wifiStatusText(wl_status_t status) {
  switch (status) {
    case WL_IDLE_STATUS: return "IDLE";
    case WL_NO_SSID_AVAIL: return "NO_SSID";
    case WL_SCAN_COMPLETED: return "SCAN_DONE";
    case WL_CONNECTED: return "CONNECTED";
    case WL_CONNECT_FAILED: return "CONNECT_FAILED";
    case WL_CONNECTION_LOST: return "CONNECTION_LOST";
    case WL_DISCONNECTED: return "DISCONNECTED";
    default: return "UNKNOWN";
  }
}

void printWifiScanHint() {
  int count = WiFi.scanNetworks(false, true);
  if (count < 0) {
    Serial.printf("[WIFI] scan failed: %d\n", count);
    return;
  }

  bool found = false;
  int bestRssi = -200;
  for (int i = 0; i < count; i++) {
    String ssid = WiFi.SSID(i);
    if (ssid == WIFI_SSID) {
      found = true;
      bestRssi = WiFi.RSSI(i);
      break;
    }
  }

  if (found) {
    Serial.printf("[WIFI] SSID '%s' found. RSSI=%d dBm\n", WIFI_SSID, bestRssi);
  } else {
    Serial.printf("[WIFI] SSID '%s' not found in scan (%d networks). Check 2.4GHz and SSID spelling.\n", WIFI_SSID, count);
  }
}

bool ensureWifiConnected() {
  if (WiFi.status() == WL_CONNECTED) {
    if (!g_wifiWasConnected) {
      g_wifiWasConnected = true;
      Serial.printf("[WIFI] Connected. IP=%s\n", WiFi.localIP().toString().c_str());
      setStatusColor(ST77XX_GREEN);
      onFirstWifiConnected();
      playStartSuccessPrompt();
    }
    return true;
  }

  g_wifiWasConnected = false;
  unsigned long now = millis();
  if (g_lastWifiAttemptMs != 0 && (now - g_lastWifiAttemptMs) < WIFI_RETRY_COOLDOWN_MS) {
    return false;
  }

  g_lastWifiAttemptMs = now;
  g_wifiRetryCount++;
  wl_status_t before = WiFi.status();
  Serial.printf("[WIFI] Connect attempt #%u (prev=%s)\n", (unsigned)g_wifiRetryCount, wifiStatusText(before));

  setStatusColor(ST77XX_RED);
  showWifiStatusOnTft("WiFi connecting...");
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.persistent(false);
  WiFi.disconnect(false, true);
  delay(100);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && (millis() - start) < WIFI_CONNECT_ATTEMPT_MS) {
    delay(250);
    Serial.print(".");
  }
  Serial.println();

  wl_status_t after = WiFi.status();
  if (after == WL_CONNECTED) {
    g_wifiWasConnected = true;
    Serial.printf("[WIFI] Connected. IP=%s\n", WiFi.localIP().toString().c_str());
    setStatusColor(ST77XX_GREEN);
    onFirstWifiConnected();
    playStartSuccessPrompt();
    return true;
  }

  Serial.printf("[WIFI] Attempt failed. status=%s (%d)\n", wifiStatusText(after), (int)after);
  if ((g_wifiRetryCount % 3) == 0) {
    printWifiScanHint();
  }
  showWifiStatusOnTft(String("WiFi retry\n") + wifiStatusText(after), ST77XX_RED);
  playCantConnectWifiPrompt();
  return false;
}

// ========================= 6.5) WS QA FLOW =========================
const char* qaStepText(QaStep step) {
  switch (step) {
    case QA_STEP_IDLE: return "IDLE";
    case QA_STEP_WAIT_WS: return "WAIT_WS";
    case QA_STEP_WAIT_HELLO_ACK: return "WAIT_HELLO_ACK";
    case QA_STEP_WAIT_AUDIO_START_ACK: return "WAIT_AUDIO_START_ACK";
    case QA_STEP_STREAM_MIC: return "STREAM_MIC";
    case QA_STEP_WAIT_TTS_END: return "WAIT_TTS_END";
    default: return "UNKNOWN";
  }
}

void resetQaWsFlags() {
  g_wsHelloAck = false;
  g_wsAudioStartAck = false;
  g_wsTtsEnd = false;
  g_wsError = false;
  g_wsErrorCode = "";
  g_wsErrorMessage = "";
  g_wsLastTranscript = "";
  g_wsLastAssistantReply = "";
  g_wsSkipAudioHeaderBytes = 0;
  g_wsTtsStartMs = 0;
  g_qaTtsWaitTimeoutMs = QA_WAIT_TTS_END_MS;
}

void resetQaCaptureState() {
  g_qaSpeechStarted = false;
  g_qaSpeechStartMs = 0;
  g_qaLastSpeechMs = 0;
  g_qaSpeechHitCount = 0;
  g_qaNoiseEma = 300.0f;
  g_qaSentAudioBytes = 0;
  g_qaPreRollWriteIdx = 0;
  g_qaPreRollCount = 0;
  for (int i = 0; i < QA_PREROLL_FRAMES; i++) {
    g_qaPreRollSize[i] = 0;
  }
}

void analyzePcm16Frame(const uint8_t* data,
                       size_t bytesLen,
                       int& avgAbs,
                       int& peakAbs,
                       uint16_t& zcr,
                       float& peakToAvg) {
  avgAbs = 0;
  peakAbs = 0;
  zcr = 0;
  peakToAvg = 0.0f;

  if (data == nullptr || bytesLen < 2) {
    return;
  }

  size_t sampleCount = bytesLen / 2;
  if (sampleCount == 0) {
    return;
  }

  uint64_t sum = 0;
  int16_t prev = 0;
  bool hasPrev = false;

  for (size_t i = 0; i < sampleCount; i++) {
    size_t idx = i * 2;
    int16_t sample = (int16_t)((uint16_t)data[idx] | ((uint16_t)data[idx + 1] << 8));
    int32_t absSample = sample < 0 ? -sample : sample;
    sum += (uint32_t)absSample;
    if ((int)absSample > peakAbs) {
      peakAbs = (int)absSample;
    }

    if (hasPrev) {
      bool signChanged = ((sample >= 0) != (prev >= 0));
      if (signChanged) {
        zcr++;
      }
    }
    prev = sample;
    hasPrev = true;
  }

  avgAbs = (int)(sum / sampleCount);
  if (avgAbs > 0) {
    peakToAvg = (float)peakAbs / (float)avgAbs;
  }
}

int qaCurrentThresholdAbs() {
  float threshold = g_qaNoiseEma * QA_VAD_THRESHOLD_MULTIPLIER;
  if (threshold < (float)QA_VAD_MIN_ABS) {
    threshold = (float)QA_VAD_MIN_ABS;
  }
  return (int)threshold;
}

void qaUpdateNoiseFloor(int frameAbs) {
  g_qaNoiseEma = (1.0f - QA_VAD_NOISE_EMA_ALPHA) * g_qaNoiseEma + QA_VAD_NOISE_EMA_ALPHA * (float)frameAbs;
  if (g_qaNoiseEma < 80.0f) {
    g_qaNoiseEma = 80.0f;
  }
}

void qaPushPreRoll(const uint8_t* data, size_t bytesLen) {
  if (data == nullptr || bytesLen == 0) {
    return;
  }
  size_t copyLen = bytesLen > QA_MIC_CHUNK_BYTES ? QA_MIC_CHUNK_BYTES : bytesLen;
  memcpy(g_qaPreRollBuf[g_qaPreRollWriteIdx], data, copyLen);
  g_qaPreRollSize[g_qaPreRollWriteIdx] = (uint16_t)copyLen;
  g_qaPreRollWriteIdx = (uint8_t)((g_qaPreRollWriteIdx + 1) % QA_PREROLL_FRAMES);
  if (g_qaPreRollCount < QA_PREROLL_FRAMES) {
    g_qaPreRollCount++;
  }
}

void qaFlushPreRollToWs() {
  if (g_qaPreRollCount == 0) {
    return;
  }

  int startIdx = (int)g_qaPreRollWriteIdx - (int)g_qaPreRollCount;
  if (startIdx < 0) {
    startIdx += QA_PREROLL_FRAMES;
  }

  for (int i = 0; i < g_qaPreRollCount; i++) {
    int idx = (startIdx + i) % QA_PREROLL_FRAMES;
    uint16_t n = g_qaPreRollSize[idx];
    if (n > 0) {
      g_ws.sendBIN(g_qaPreRollBuf[idx], n);
      g_qaSentAudioBytes += n;
    }
  }

  g_qaPreRollCount = 0;
}

void resetAutoQaDetector() {
  g_autoQaNoiseEma = 300.0f;
  g_autoQaSpeechHits = 0;
}

bool autoQaCanListenNow() {
  if (g_state == STATE_INTERRUPT_QA) {
    if (!AUTO_QA_ALLOW_BARGE_IN_DURING_QA_TTS) {
      return false;
    }
    if (g_qaStep != QA_STEP_WAIT_TTS_END) {
      return false;
    }
    if (g_wsTtsStartMs > 0 && (millis() - g_wsTtsStartMs) < AUTO_QA_QA_TTS_GUARD_MS) {
      return false;
    }
    return true;
  }
  if (g_state == STATE_STORY_PLAYING) {
    return true;
  }
  if (AUTO_QA_LISTEN_IN_IDLE && g_state == STATE_IDLE) {
    return true;
  }
  return false;
}

void tickAutoQaTrigger() {
  if (!AUTO_QA_ENABLED) {
    return;
  }
  if (!autoQaCanListenNow()) {
    resetAutoQaDetector();
    return;
  }

  const unsigned long now = millis();
  if (now - g_lastQaFinishMs < AUTO_QA_COOLDOWN_MS) {
    return;
  }

  uint8_t micBuf[QA_MIC_CHUNK_BYTES];
  size_t readBytes = 0;
  esp_err_t err = i2s_read(I2S_NUM_1, micBuf, QA_MIC_CHUNK_BYTES, &readBytes, 0);
  if (err != ESP_OK || readBytes == 0) {
    return;
  }

  int frameAbs = 0;
  int peakAbs = 0;
  uint16_t zcr = 0;
  float peakToAvg = 0.0f;
  analyzePcm16Frame(micBuf, readBytes, frameAbs, peakAbs, zcr, peakToAvg);

  uint8_t requiredHits = AUTO_QA_HIT_FRAMES;
  float thresholdF = g_autoQaNoiseEma * AUTO_QA_THRESHOLD_MULTIPLIER;
  if (thresholdF < (float)AUTO_QA_MIN_ABS) {
    thresholdF = (float)AUTO_QA_MIN_ABS;
  }

  if (g_state == STATE_INTERRUPT_QA && g_qaStep == QA_STEP_WAIT_TTS_END) {
    float ttsThresholdF = g_autoQaNoiseEma * AUTO_QA_QA_TTS_THRESHOLD_MULTIPLIER;
    if (ttsThresholdF < (float)AUTO_QA_QA_TTS_MIN_ABS) {
      ttsThresholdF = (float)AUTO_QA_QA_TTS_MIN_ABS;
    }
    if (thresholdF < ttsThresholdF) {
      thresholdF = ttsThresholdF;
    }
    requiredHits = AUTO_QA_QA_TTS_HIT_FRAMES;
  }

  int threshold = (int)thresholdF;
  bool speechStrong = frameAbs >= threshold;
  bool zcrOk = zcr >= AUTO_QA_MIN_ZCR && zcr <= AUTO_QA_MAX_ZCR;
  bool peakShapeOk = peakToAvg > 0.0f && peakToAvg <= AUTO_QA_MAX_PEAK_TO_AVG;
  bool speechCandidate = speechStrong && zcrOk && peakShapeOk;

  if (!speechCandidate) {
    g_autoQaNoiseEma = (1.0f - AUTO_QA_NOISE_EMA_ALPHA) * g_autoQaNoiseEma + AUTO_QA_NOISE_EMA_ALPHA * (float)frameAbs;
    if (g_autoQaNoiseEma < 80.0f) {
      g_autoQaNoiseEma = 80.0f;
    }
    if (g_autoQaSpeechHits > 0) {
      if (frameAbs < (int)(threshold * 0.8f)) {
        g_autoQaSpeechHits = 0;
      } else {
        g_autoQaSpeechHits--;
      }
    }
  } else if (g_autoQaSpeechHits < 250) {
    g_autoQaSpeechHits++;
  }

  if (g_autoQaSpeechHits >= requiredHits) {
    Serial.printf("[AUTO_QA] Voice trigger abs=%d zcr=%u ratio=%.2f threshold=%d state=%d\n",
                  frameAbs, (unsigned)zcr, peakToAvg, threshold, (int)g_state);
    resetAutoQaDetector();
    if (g_state == STATE_INTERRUPT_QA) {
      startQaInterrupt("vad_barge_in");
    } else {
      startQaInterrupt("vad_auto");
    }
  }
}

void onWsEvent(WStype_t type, uint8_t* payload, size_t length) {
  if (type == WStype_CONNECTED) {
    g_wsConnected = true;
    Serial.println("[WS] Connected /ws/robot");
    return;
  }

  if (type == WStype_DISCONNECTED) {
    g_wsConnected = false;
    g_wsActiveOutputUtteranceId = "";
    Serial.println("[WS] Disconnected");
    return;
  }

  if (type == WStype_BIN) {
    if (!g_i2sTxReady || length == 0) {
      return;
    }
    if (g_state != STATE_INTERRUPT_QA || g_qaStep != QA_STEP_WAIT_TTS_END || g_wsDropBinaryAudio) {
      return;
    }

    size_t offset = 0;
    if (g_wsSkipAudioHeaderBytes > 0) {
      size_t drop = length < (size_t)g_wsSkipAudioHeaderBytes ? length : (size_t)g_wsSkipAudioHeaderBytes;
      g_wsSkipAudioHeaderBytes -= (int)drop;
      offset = drop;
    }

    if (offset < length) {
      if (!enqueueAudioBytes(payload + offset, length - offset)) {
        Serial.println("[AUDIO] ws enqueue failed");
      }
    }
    return;
  }

  if (type == WStype_ERROR) {
    g_wsError = true;
    g_wsErrorCode = "WS_CLIENT_ERROR";
    g_wsErrorMessage = "Client WS error";
    return;
  }

  if (type != WStype_TEXT) {
    return;
  }

  DynamicJsonDocument doc(1024);
  DeserializationError err = deserializeJson(doc, payload, length);
  if (err) {
    Serial.printf("[WS] JSON parse error: %s\n", err.c_str());
    return;
  }

  String typeText = doc["type"].isNull() ? "" : doc["type"].as<String>();
  if (typeText.length() == 0) {
    return;
  }

  if (typeText == "ACK") {
    String ackUtterance = doc["utteranceId"].isNull() ? "" : doc["utteranceId"].as<String>();
    if (ackUtterance.length() == 0) {
      g_wsHelloAck = true;
      Serial.println("[WS] ACK HELLO");
    } else if (ackUtterance == g_qaUtteranceId) {
      g_wsAudioStartAck = true;
      Serial.printf("[WS] ACK AUDIO_START utterance=%s\n", ackUtterance.c_str());
    }
    return;
  }

  if (typeText == "OUTPUT_CANCELLED") {
    String cancelledUtterance = doc["cancelledUtteranceId"].isNull()
                                  ? (doc["utteranceId"].isNull() ? "" : doc["utteranceId"].as<String>())
                                  : doc["cancelledUtteranceId"].as<String>();
    if (cancelledUtterance.length() > 0) {
      Serial.printf("[WS] OUTPUT_CANCELLED utterance=%s\n", cancelledUtterance.c_str());
      if (cancelledUtterance == g_wsActiveOutputUtteranceId) {
        g_wsActiveOutputUtteranceId = "";
        g_wsTtsStartMs = 0;
      }
    } else {
      Serial.println("[WS] OUTPUT_CANCELLED");
      g_wsActiveOutputUtteranceId = "";
      g_wsTtsStartMs = 0;
    }
    return;
  }

  if (typeText == "TRANSCRIPT") {
    if (!isCurrentQaUtterance(doc)) {
      Serial.println("[WS] Ignore TRANSCRIPT from old utterance");
      return;
    }
    g_wsLastTranscript = doc["text"].isNull() ? "" : doc["text"].as<String>();
    Serial.printf("[WS] TRANSCRIPT: %s\n", g_wsLastTranscript.c_str());
    return;
  }

  if (typeText == "ASSISTANT_REPLY") {
    if (!isCurrentQaUtterance(doc)) {
      Serial.println("[WS] Ignore ASSISTANT_REPLY from old utterance");
      return;
    }
    g_wsLastAssistantReply = doc["text"].isNull() ? "" : doc["text"].as<String>();
    Serial.printf("[WS] ASSISTANT_REPLY: %s\n", g_wsLastAssistantReply.c_str());
    // Screen uses expression-only UI, no QA text rendering.
    return;
  }

  if (typeText == "TTS_START") {
    if (!isCurrentQaUtterance(doc)) {
      Serial.println("[WS] Ignore TTS_START from old utterance");
      return;
    }
    if (g_qaStep != QA_STEP_WAIT_TTS_END) {
      Serial.println("[WS] Ignore TTS_START outside WAIT_TTS_END");
      return;
    }
    String utterance = doc["utteranceId"].isNull() ? "" : doc["utteranceId"].as<String>();
    if (utterance.length() > 0) {
      g_wsActiveOutputUtteranceId = utterance;
    }
    String mimeType = doc["audioMimeType"].isNull() ? "" : doc["audioMimeType"].as<String>();
    int audioBytesLen = doc["audioBytesLength"].isNull() ? 0 : doc["audioBytesLength"].as<int>();
    int sampleRate = doc["sampleRate"].isNull() ? AUDIO_SAMPLE_RATE : doc["sampleRate"].as<int>();
    int channels = doc["channels"].isNull() ? 1 : doc["channels"].as<int>();

    if (mimeType.startsWith("audio/wav")) {
      g_wsSkipAudioHeaderBytes = QA_WAV_HEADER_BYTES;
    } else {
      g_wsSkipAudioHeaderBytes = 0;
    }

    if (audioBytesLen > 0 && sampleRate > 0 && channels > 0) {
      uint32_t bytesPerSec = (uint32_t)sampleRate * (uint32_t)channels * 2U;
      uint32_t playMs = bytesPerSec == 0 ? 0 : (uint32_t)(((uint64_t)audioBytesLen * 1000ULL) / bytesPerSec);
      uint32_t dynamicTimeout = playMs + 15000U;
      if (dynamicTimeout < QA_WAIT_TTS_END_MS) {
        dynamicTimeout = QA_WAIT_TTS_END_MS;
      }
      if (dynamicTimeout > 180000U) {
        dynamicTimeout = 180000U;
      }
      g_qaTtsWaitTimeoutMs = dynamicTimeout;
    } else {
      g_qaTtsWaitTimeoutMs = QA_WAIT_TTS_END_MS;
    }

    g_wsTtsEnd = false;
    g_wsDropBinaryAudio = false;
    g_wsTtsStartMs = millis();
    resetAutoQaDetector();
    Serial.printf("[WS] TTS_START mime=%s bytes=%d timeoutMs=%lu\n",
                  mimeType.c_str(),
                  audioBytesLen,
                  (unsigned long)g_qaTtsWaitTimeoutMs);
    return;
  }

  if (typeText == "TTS_END") {
    if (!isCurrentQaUtterance(doc)) {
      Serial.println("[WS] Ignore TTS_END from old utterance");
      return;
    }
    if (g_qaStep != QA_STEP_WAIT_TTS_END) {
      Serial.println("[WS] Ignore TTS_END outside WAIT_TTS_END");
      return;
    }
    String utterance = doc["utteranceId"].isNull() ? "" : doc["utteranceId"].as<String>();
    if (utterance.length() == 0 || utterance == g_wsActiveOutputUtteranceId) {
      g_wsActiveOutputUtteranceId = "";
    }
    g_wsTtsStartMs = 0;
    g_wsTtsEnd = true;
    Serial.println("[WS] TTS_END");
    return;
  }

  if (typeText == "ERROR") {
    g_wsError = true;
    g_wsErrorCode = doc["errorCode"].isNull() ? "UNKNOWN_ERROR" : doc["errorCode"].as<String>();
    g_wsErrorMessage = doc["errorMessage"].isNull() ? "Unknown WS error" : doc["errorMessage"].as<String>();
    Serial.printf("[WS] ERROR code=%s msg=%s\n", g_wsErrorCode.c_str(), g_wsErrorMessage.c_str());
    return;
  }
}

void initWsQaClient() {
  if (g_wsInitialized) {
    return;
  }

  g_ws.begin(WS_HOST, WS_PORT, "/ws/robot");
  g_ws.onEvent(onWsEvent);
  g_ws.setReconnectInterval(3000);
  g_wsInitialized = true;
  Serial.printf("[WS] Init client ws://%s:%u/ws/robot\n", WS_HOST, WS_PORT);
}

void restartWsQaClient() {
  if (g_wsInitialized) {
    g_ws.disconnect();
    g_wsConnected = false;
    g_wsInitialized = false;
    delay(10);
  }
  initWsQaClient();
}

bool sendWsHello() {
  DynamicJsonDocument doc(256);
  doc["type"] = "HELLO";
  doc["sessionId"] = SESSION_ID;
  doc["robotId"] = ROBOT_ID;
  String payload;
  serializeJson(doc, payload);
  return g_ws.sendTXT(payload);
}

bool sendWsAudioStart() {
  g_qaUtteranceId = String("utt-") + String(++g_qaUtteranceSeq);

  DynamicJsonDocument doc(384);
  doc["type"] = "AUDIO_START";
  doc["sessionId"] = SESSION_ID;
  doc["robotId"] = ROBOT_ID;
  doc["utteranceId"] = g_qaUtteranceId;
  doc["format"] = QA_AUDIO_FORMAT;
  doc["sampleRate"] = AUDIO_SAMPLE_RATE;
  doc["channels"] = 1;
  doc["frameDurationMs"] = 20;

  String payload;
  serializeJson(doc, payload);
  Serial.printf("[WS] AUDIO_START utterance=%s format=%s\n", g_qaUtteranceId.c_str(), QA_AUDIO_FORMAT);
  return g_ws.sendTXT(payload);
}

bool sendWsAudioEnd() {
  DynamicJsonDocument doc(256);
  doc["type"] = "AUDIO_END";
  doc["sessionId"] = SESSION_ID;
  doc["utteranceId"] = g_qaUtteranceId;
  String payload;
  serializeJson(doc, payload);
  return g_ws.sendTXT(payload);
}

bool sendWsCancelOutput(const String& targetUtteranceId) {
  DynamicJsonDocument doc(256);
  doc["type"] = "CANCEL_OUTPUT";
  doc["sessionId"] = SESSION_ID;
  doc["robotId"] = ROBOT_ID;
  if (targetUtteranceId.length() > 0) {
    doc["targetUtteranceId"] = targetUtteranceId;
  }
  String payload;
  serializeJson(doc, payload);
  Serial.printf("[WS] CANCEL_OUTPUT target=%s\n",
                targetUtteranceId.length() > 0 ? targetUtteranceId.c_str() : "<active>");
  return g_ws.sendTXT(payload);
}

bool isCurrentQaUtterance(const DynamicJsonDocument& doc) {
  String utterance = doc["utteranceId"].isNull() ? "" : doc["utteranceId"].as<String>();
  if (utterance.length() == 0) {
    return false;
  }
  return utterance == g_qaUtteranceId;
}

bool shouldResumeStoryAfterQa() {
  if (g_state == STATE_STORY_PLAYING) {
    return true;
  }
  return (g_currentStoryId.length() > 0) && (!g_storyCompleted);
}

void finishQaInterrupt(bool success, const char* reason) {
  g_qaStep = QA_STEP_IDLE;
  g_qaStepStartMs = 0;
  g_qaRecordStartMs = 0;
  resetQaCaptureState();
  g_wsDropBinaryAudio = false;
  g_lastQaFinishMs = millis();
  resetAutoQaDetector();

  if (!success) {
    Serial.printf("[QA] Failed: %s | code=%s msg=%s\n", reason, g_wsErrorCode.c_str(), g_wsErrorMessage.c_str());
    showTextOnTft(String("QA failed\n") + reason, ST77XX_RED);
  } else {
    Serial.printf("[QA] Success: %s\n", reason);
  }

  if (g_resumeStateAfterQa == STATE_STORY_PLAYING && (g_storyCompleted || g_currentStoryId.length() == 0)) {
    g_state = STATE_IDLE;
  } else {
    g_state = g_resumeStateAfterQa;
  }
  resetQaWsFlags();
  printState(reason);
}

void startQaInterrupt(const char* trigger) {
  bool restartFromQaTts = (g_state == STATE_INTERRUPT_QA && g_qaStep == QA_STEP_WAIT_TTS_END);
  if (g_state == STATE_INTERRUPT_QA && !restartFromQaTts) {
    return;
  }

  if (!restartFromQaTts) {
    g_resumeStateAfterQa = shouldResumeStoryAfterQa() ? STATE_STORY_PLAYING : STATE_IDLE;
  }

  flushAudioOutputNow();
  g_wsDropBinaryAudio = true;
  g_state = STATE_INTERRUPT_QA;
  g_qaStep = QA_STEP_WAIT_WS;
  g_qaStepStartMs = millis();
  g_qaRecordStartMs = 0;
  resetQaWsFlags();
  resetQaCaptureState();
  resetAutoQaDetector();

  if (restartFromQaTts) {
    bool cancelSent = false;
    if (g_wsConnected) {
      cancelSent = sendWsCancelOutput(g_wsActiveOutputUtteranceId);
    }

    if (!cancelSent) {
      // Fallback if CANCEL_OUTPUT could not be sent.
      restartWsQaClient();
    } else {
      initWsQaClient();
    }
  } else {
    initWsQaClient();
  }

  Serial.printf("[QA] Start interrupt trigger=%s restart=%d\n", trigger, (int)restartFromQaTts);
  setStatusColor(ST77XX_BLUE);
  showTextOnTft(String("QA start\n") + trigger, ST77XX_BLUE);
}

// ========================= 7) BACKEND APIs =========================
bool pullCommandFromServer(PulledCommand& outCmd) {
  outCmd = PulledCommand();

  HTTPClient http;
  String url = buildUrl(String("/api/robots/") + ROBOT_ID + "/commands/pull?consume=true");

  if (!http.begin(url)) {
    Serial.println("[CMD] http.begin failed");
    return false;
  }

  http.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
  http.setTimeout(HTTP_READ_TIMEOUT_MS);
  if (strlen(AUTH_BEARER_TOKEN) > 0) {
    http.addHeader("Authorization", String("Bearer ") + AUTH_BEARER_TOKEN);
  }

  int code = http.GET();
  if (code <= 0) {
    Serial.printf("[CMD] GET failed: %d\n", code);
    http.end();
    return false;
  }

  if (code != 200) {
    String body = http.getString();
    Serial.printf("[CMD] Unexpected status=%d body=%s\n", code, body.c_str());
    http.end();
    return false;
  }

  String body = http.getString();
  http.end();

  DynamicJsonDocument doc(4096);
  DeserializationError err = deserializeJson(doc, body);
  if (err) {
    Serial.printf("[CMD] JSON parse error: %s\n", err.c_str());
    return false;
  }

  if (!doc["success"].as<bool>()) {
    Serial.println("[CMD] API success=false");
    return false;
  }

  JsonVariant data = doc["data"];
  if (data.isNull()) {
    return true;
  }

  outCmd.hasCommand = true;
  outCmd.commandId = data["commandId"].as<String>();
  outCmd.type = data["type"].as<String>();
  outCmd.storyId = data["storyId"].isNull() ? "" : data["storyId"].as<String>();
  outCmd.reminderId = data["reminderId"].isNull() ? "" : data["reminderId"].as<String>();

  return true;
}

bool callStopPlayback() {
  HTTPClient http;
  String url = buildUrl("/api/stories/playback/stop");

  if (!http.begin(url)) {
    Serial.println("[PLAYBACK] stop begin failed");
    return false;
  }

  http.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
  http.setTimeout(HTTP_READ_TIMEOUT_MS);
  addJsonHeaders(http);

  DynamicJsonDocument req(256);
  req["robotId"] = ROBOT_ID;

  String payload;
  serializeJson(req, payload);

  int code = http.POST(payload);
  String body = http.getString();
  http.end();

  if (code != 200) {
    Serial.printf("[PLAYBACK] stop failed status=%d body=%s\n", code, body.c_str());
    return false;
  }

  Serial.println("[PLAYBACK] stop ok");
  return true;
}

PlaybackResponse postPlaybackStart(const String& storyId);
PlaybackResponse postPlaybackNext();

// ========================= 8) AUDIO HANDLER (HW HOOK) =========================
bool shouldAbortCurrentOutput() {
  return (g_state == STATE_INTERRUPT_QA) || g_stopRequested;
}

bool playAudioChunk(WiFiClient& stream, int length, const String& mimeType, int sampleRate, int channels) {
  const int bufSize = 1024;
  uint8_t buf[bufSize];
  int totalQueued = 0;
  bool enqueueFailed = false;

  int remain = length;
  while (remain > 0) {
    if (shouldAbortCurrentOutput()) {
      break;
    }
    pumpUiAndControlDuringBlockingWork();

    // Allow auto VAD trigger while receiving long story audio chunk.
    tickAutoQaTrigger();

    int toRead = remain > bufSize ? bufSize : remain;
    int n = stream.readBytes(buf, toRead);
    if (n <= 0) {
      break;
    }

    if (!enqueueAudioBytes(buf, n)) {
      Serial.println("[AUDIO] enqueue failed during stream");
      enqueueFailed = true;
      break;
    }
    totalQueued += n;

    remain -= n;
  }

  bool interrupted = shouldAbortCurrentOutput();
  Serial.printf("[AUDIO] read=%d queued=%d mime=%s sr=%d ch=%d interrupted=%d enqueueFailed=%d\n",
                length - remain, totalQueued, mimeType.c_str(), sampleRate, channels, (int)interrupted, (int)enqueueFailed);
  return !interrupted && !enqueueFailed;
}

PlaybackResponse parseAndPlayAudioResponse(HTTPClient& http, int code) {
  PlaybackResponse result;
  result.httpCode = code;

  if (code == 204) {
    result.ok = true;
    result.completed = true;
    return result;
  }

  if (code != 200) {
    String errBody = http.getString();
    Serial.printf("[PLAYBACK] status=%d body=%s\n", code, errBody.c_str());
    return result;
  }

  result.mimeType = http.header("Content-Type");
  String completedHeader = http.header("X-Completed");
  String sampleRateHeader = http.header("X-Sample-Rate");
  String channelsHeader = http.header("X-Channels");
  String segmentOrderHeader = http.header("X-Segment-Order");

  result.completed = (completedHeader == "true" || completedHeader == "TRUE");
  result.sampleRate = sampleRateHeader.length() ? sampleRateHeader.toInt() : 0;
  result.channels = channelsHeader.length() ? channelsHeader.toInt() : 0;
  result.segmentOrder = segmentOrderHeader.length() ? segmentOrderHeader.toInt() : -1;

  int contentLen = http.getSize();
  result.bytesLength = contentLen > 0 ? contentLen : 0;

  WiFiClient* stream = http.getStreamPtr();
  if (stream != nullptr && contentLen > 0) {
    bool ok = playAudioChunk(*stream, contentLen, result.mimeType, result.sampleRate, result.channels);
    if (!ok) {
      result.interrupted = shouldAbortCurrentOutput();
      result.ok = result.interrupted;
      return result;
    }
  } else if (stream != nullptr && contentLen < 0) {
    // Chunked transfer: read until connection closes.
    size_t totalQueued = 0;
    while (http.connected()) {
      if (shouldAbortCurrentOutput()) {
        result.interrupted = true;
        break;
      }

      pumpUiAndControlDuringBlockingWork();
      tickAutoQaTrigger();
      int available = stream->available();
      if (available > 0) {
        uint8_t tmp[512];
        int n = stream->readBytes(tmp, available > 512 ? 512 : available);
        if (n <= 0) break;
        if (!enqueueAudioBytes(tmp, n)) {
          Serial.println("[AUDIO] enqueue failed during chunked stream");
          result.interrupted = true;
          break;
        }
        totalQueued += (size_t)n;
        result.bytesLength += n;
      } else {
        delay(5);
      }
    }
    Serial.printf("[AUDIO] chunked read=%d queued=%u interrupted=%d\n",
                  result.bytesLength, (unsigned)totalQueued, (int)result.interrupted);
  } else {
    consumeBody(http);
  }

  result.ok = true;
  return result;
}

PlaybackResponse postPlaybackStart(const String& storyId) {
  HTTPClient http;
  String url = buildUrl("/api/stories/playback/start");

  PlaybackResponse result;

  if (!http.begin(url)) {
    Serial.println("[PLAYBACK] start begin failed");
    return result;
  }

  http.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
  http.setTimeout(HTTP_READ_TIMEOUT_MS);
  addJsonHeaders(http);

  DynamicJsonDocument req(384);
  req["robotId"] = ROBOT_ID;
  req["storyId"] = storyId;

  String payload;
  serializeJson(req, payload);

  int code = http.POST(payload);
  result = parseAndPlayAudioResponse(http, code);
  http.end();

  return result;
}

PlaybackResponse postPlaybackNext() {
  HTTPClient http;
  String url = buildUrl("/api/stories/playback/next");

  PlaybackResponse result;

  if (!http.begin(url)) {
    Serial.println("[PLAYBACK] next begin failed");
    return result;
  }

  http.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
  http.setTimeout(HTTP_READ_TIMEOUT_MS);
  addJsonHeaders(http);

  DynamicJsonDocument req(256);
  req["robotId"] = ROBOT_ID;

  String payload;
  serializeJson(req, payload);

  int code = http.POST(payload);
  result = parseAndPlayAudioResponse(http, code);
  http.end();

  return result;
}

// ========================= 9) COMMAND EXECUTOR =========================
void onStartStory(const String& storyId) {
  if (storyId.length() == 0) {
    Serial.println("[CMD] START_STORY missing storyId");
    setStatusColor(ST77XX_RED);
    showTextOnTft("START_STORY missing storyId");
    return;
  }

  g_stopRequested = false;
  g_currentStoryId = storyId;
  g_storyCompleted = false;

  PlaybackResponse res = postPlaybackStart(storyId);
  if (!res.ok) {
    Serial.println("[CMD] START_STORY failed to start playback");
    g_storyCompleted = true;
    g_currentStoryId = "";
    g_state = STATE_IDLE;
    printState("start_story_failed");
    return;
  }
  if (res.interrupted || g_state == STATE_INTERRUPT_QA) {
    if (g_stopRequested) {
      Serial.println("[CMD] START_STORY interrupted by STOP");
    } else {
      Serial.println("[CMD] START_STORY interrupted by QA");
    }
    return;
  }

  g_storyCompleted = res.completed;
  g_state = g_storyCompleted ? STATE_IDLE : STATE_STORY_PLAYING;
  if (g_storyCompleted) {
    g_currentStoryId = "";
  }

  Serial.printf("[CMD] START_STORY ok segment=%d completed=%d\n", res.segmentOrder, (int)res.completed);
  printState("after START_STORY");
}

void onStopStory() {
  flushAudioOutputNow();
  callStopPlayback();
  g_state = STATE_IDLE;
  g_storyCompleted = true;
  g_currentStoryId = "";
  g_stopRequested = false;
  g_lastQaFinishMs = 0;
  printState("after STOP_STORY");
}

void onReminderCreate(const String& reminderId) {
  // You can call: GET /api/reminders/{reminderId}/execute-data and play reminder audio.
  Serial.printf("[CMD] REMINDER_CREATE reminderId=%s\n", reminderId.c_str());
  showTextOnTft(String("Reminder create\n") + reminderId);
}

void onReminderCancel(const String& reminderId) {
  Serial.printf("[CMD] REMINDER_CANCEL reminderId=%s\n", reminderId.c_str());
  showTextOnTft(String("Reminder cancel\n") + reminderId);
}

void onCustomCommand() {
  // Current mapping: trigger one WS QA interrupt round.
  Serial.println("[CMD] CUSTOM received -> trigger QA interrupt");
  startQaInterrupt("custom");
}

void executeCommand(const PulledCommand& cmd) {
  if (!cmd.hasCommand) {
    return;
  }

  Serial.printf("[CMD] id=%s type=%s storyId=%s reminderId=%s\n",
                cmd.commandId.c_str(), cmd.type.c_str(), cmd.storyId.c_str(), cmd.reminderId.c_str());

  if (cmd.type == "START_STORY") {
    onStartStory(cmd.storyId);
    return;
  }
  if (cmd.type == "STOP_STORY") {
    onStopStory();
    return;
  }
  if (cmd.type == "REMINDER_CREATE") {
    onReminderCreate(cmd.reminderId);
    return;
  }
  if (cmd.type == "REMINDER_CANCEL") {
    onReminderCancel(cmd.reminderId);
    return;
  }
  if (cmd.type == "CUSTOM") {
    onCustomCommand();
    return;
  }

  Serial.printf("[CMD] Unknown type: %s\n", cmd.type.c_str());
  setStatusColor(ST77XX_RED);
  showTextOnTft(String("Unknown cmd\n") + cmd.type);
}

// ========================= 10) STORY LOOP =========================
void tickStoryPlayback() {
  if (g_state != STATE_STORY_PLAYING) {
    return;
  }

  PlaybackResponse res = postPlaybackNext();
  if (!res.ok) {
    Serial.println("[PLAYBACK] next failed, keep state and retry");
    return;
  }
  if (res.interrupted || g_state == STATE_INTERRUPT_QA) {
    if (g_stopRequested) {
      Serial.println("[PLAYBACK] next interrupted by STOP");
    } else {
      Serial.println("[PLAYBACK] next interrupted by QA");
    }
    return;
  }

  g_storyCompleted = res.completed;
  Serial.printf("[PLAYBACK] next ok segment=%d completed=%d\n", res.segmentOrder, (int)res.completed);

  if (g_storyCompleted) {
    g_state = STATE_IDLE;
    g_currentStoryId = "";
    printState("story completed");
  }
}

// ========================= 11) WS QA FLOW =========================
void tickInterruptQa() {
  if (g_state != STATE_INTERRUPT_QA) {
    return;
  }

  if (!g_wsInitialized) {
    initWsQaClient();
  }

  const unsigned long now = millis();

  if (g_wsError) {
    if (isWsNoSpeechError()) {
      restartQaListeningAfterNoSpeech("qa_ws_no_speech");
      return;
    }
    finishQaInterrupt(false, "qa_ws_error");
    return;
  }

  switch (g_qaStep) {
    case QA_STEP_WAIT_WS:
      if (g_wsConnected) {
        if (!sendWsHello()) {
          finishQaInterrupt(false, "qa_hello_send_fail");
          return;
        }
        g_qaStep = QA_STEP_WAIT_HELLO_ACK;
        g_qaStepStartMs = now;
        showTextOnTft("QA hello...", ST77XX_BLUE);
      } else if (now - g_qaStepStartMs > QA_STEP_TIMEOUT_MS) {
        finishQaInterrupt(false, "qa_ws_connect_timeout");
      }
      break;

    case QA_STEP_WAIT_HELLO_ACK:
      if (g_wsHelloAck) {
        if (!sendWsAudioStart()) {
          finishQaInterrupt(false, "qa_audio_start_send_fail");
          return;
        }
        g_qaStep = QA_STEP_WAIT_AUDIO_START_ACK;
        g_qaStepStartMs = now;
      } else if (now - g_qaStepStartMs > QA_STEP_TIMEOUT_MS) {
        finishQaInterrupt(false, "qa_hello_ack_timeout");
      }
      break;

    case QA_STEP_WAIT_AUDIO_START_ACK:
      if (g_wsAudioStartAck) {
        resetQaCaptureState();
        g_qaStep = QA_STEP_STREAM_MIC;
        g_qaRecordStartMs = now;
        g_qaStepStartMs = now;
        showTextOnTft("QA waiting speech...", ST77XX_BLUE);
      } else if (now - g_qaStepStartMs > QA_STEP_TIMEOUT_MS) {
        finishQaInterrupt(false, "qa_audio_start_ack_timeout");
      }
      break;

    case QA_STEP_STREAM_MIC: {
      uint8_t micBuf[QA_MIC_CHUNK_BYTES];
      size_t readBytes = 0;
      esp_err_t err = i2s_read(I2S_NUM_1, micBuf, QA_MIC_CHUNK_BYTES, &readBytes, 20 / portTICK_PERIOD_MS);
      if (err != ESP_OK || readBytes == 0) {
        unsigned long waitMs = now - g_qaRecordStartMs;
        if (waitMs > QA_WAIT_SPEECH_TIMEOUT_MS) {
          restartQaListeningAfterNoSpeech("qa_no_audio");
          return;
        }
        if (now - g_qaRecordStartMs > QA_MAX_UTTERANCE_MS) {
          finishQaInterrupt(false, "qa_mic_timeout");
        }
        break;
      }

      int frameAbs = 0;
      int peakAbs = 0;
      uint16_t zcr = 0;
      float peakToAvg = 0.0f;
      analyzePcm16Frame(micBuf, readBytes, frameAbs, peakAbs, zcr, peakToAvg);
      int threshold = qaCurrentThresholdAbs();
      bool zcrStrongOk = zcr >= QA_VAD_MIN_ZCR && zcr <= QA_VAD_MAX_ZCR;
      bool zcrWeakOk = zcr >= QA_VAD_WEAK_MIN_ZCR && zcr <= QA_VAD_WEAK_MAX_ZCR;
      bool peakShapeOk = peakToAvg > 0.0f && peakToAvg <= QA_VAD_MAX_PEAK_TO_AVG;
      bool speechStrong = (frameAbs >= threshold) && zcrStrongOk && peakShapeOk;
      bool speechWeak = (frameAbs >= (int)(threshold * 0.65f)) && zcrWeakOk && peakShapeOk;

      if (!g_qaSpeechStarted) {
        qaPushPreRoll(micBuf, readBytes);
        unsigned long waitMs = now - g_qaRecordStartMs;
        bool forceStart = QA_ENABLE_FORCE_START &&
                          waitMs >= QA_FORCE_START_MS &&
                          g_qaSpeechHitCount >= QA_FORCE_START_MIN_HITS;

        if (!speechStrong) {
          qaUpdateNoiseFloor(frameAbs);
        }

        if (speechStrong) {
          if (g_qaSpeechHitCount < 250) {
            g_qaSpeechHitCount++;
          }
        } else if (g_qaSpeechHitCount > 0) {
          if (frameAbs < (int)(threshold * 0.8f)) {
            g_qaSpeechHitCount = 0;
          } else {
            g_qaSpeechHitCount--;
          }
        }

        if (g_qaSpeechHitCount >= QA_SPEECH_HIT_FRAMES || forceStart) {
          g_qaSpeechStarted = true;
          g_qaSpeechStartMs = now;
          g_qaLastSpeechMs = now;
          qaFlushPreRollToWs();
          Serial.printf("[QA] Speech started abs=%d zcr=%u ratio=%.2f threshold=%d mode=%s\n",
                        frameAbs,
                        (unsigned)zcr,
                        peakToAvg,
                        threshold,
                        forceStart ? "force" : "vad");
          showTextOnTft("QA recording...", ST77XX_BLUE);
        } else if (waitMs > QA_WAIT_SPEECH_TIMEOUT_MS) {
          restartQaListeningAfterNoSpeech("qa_no_speech");
          return;
        }
        break;
      }

      g_ws.sendBIN(micBuf, readBytes);
      g_qaSentAudioBytes += readBytes;

      if (speechWeak) {
        g_qaLastSpeechMs = now;
      }

      unsigned long utterMs = now - g_qaSpeechStartMs;
      unsigned long silenceMs = now - g_qaLastSpeechMs;
      bool endBySilence = (utterMs >= QA_MIN_UTTERANCE_MS) && (silenceMs >= QA_END_SILENCE_MS);
      bool endByMaxLen = utterMs >= QA_MAX_UTTERANCE_MS;
      if (endBySilence || endByMaxLen) {
        if (!sendWsAudioEnd()) {
          finishQaInterrupt(false, "qa_audio_end_send_fail");
          return;
        }
        Serial.printf("[QA] Speech ended utterMs=%lu silenceMs=%lu sentBytes=%u\n",
                      utterMs, silenceMs, (unsigned)g_qaSentAudioBytes);
        g_wsTtsEnd = false;
        g_qaStep = QA_STEP_WAIT_TTS_END;
        g_qaStepStartMs = now;
        showTextOnTft("QA processing...", ST77XX_BLUE);
      }
      break;
    }

    case QA_STEP_WAIT_TTS_END:
      if (g_wsTtsEnd) {
        finishQaInterrupt(true, "qa_done");
      } else if (now - g_qaStepStartMs > g_qaTtsWaitTimeoutMs) {
        finishQaInterrupt(false, "qa_tts_timeout");
      }
      break;

    case QA_STEP_IDLE:
    default:
      finishQaInterrupt(false, "qa_invalid_step");
      break;
  }
}

// ========================= 12) MAIN LOOP =========================
void setup() {
  Serial.begin(115200);
  delay(800);

  Serial.println("\n=== robotic_esp32 firmware boot ===");
  Serial.printf("[CFG] robotId=%s sessionId=%s backend=%s ws=%s:%u\n",
                ROBOT_ID, SESSION_ID, BACKEND_BASE_URL, WS_HOST, WS_PORT);
  initHardware();
  ensureWifiConnected();
  initWsQaClient();
  initCommandPullWorker();
  printState("boot");
}

void loop() {
  tickBootButtonToggle();

  if (!ensureWifiConnected()) {
    if (g_statsOverlayEnabled) {
      drawStatsOverlay(false);
    }
    delay(50);
    return;
  }

  if (g_wsInitialized) {
    g_ws.loop();
  }

  processPendingCommands();
  tickAutoQaTrigger();

  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == 'q' || c == 'Q') {
      startQaInterrupt("serial");
    } else if (c == 'b' || c == 'B') {
      toggleStatsOverlay();
    }
  }

  tickInterruptQa();
  tickFaceUi();

  if (g_state == STATE_STORY_PLAYING && !g_stopRequested) {
    tickStoryPlayback();
  }

  delay(20);
}
