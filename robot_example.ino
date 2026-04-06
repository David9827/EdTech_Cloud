#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include <driver/i2s.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>

// ================= CẤU HÌNH HỆ THỐNG =================
const char* ssid = "Roland Garros";
const char* password = "66666888";
const char* ws_host = "192.168.100.23"; // Thay bằng IP server của bạn
const uint16_t ws_port = 5999;

// ================= ĐỊNH NGHĨA CHÂN (THEO YÊU CẦU CỦA BẠN) =================
#define I2S_MIC_WS 6
#define I2S_MIC_SCK 5
#define I2S_MIC_SD 4

#define I2S_SPK_DIN 16
#define I2S_SPK_BCLK 15
#define I2S_SPK_LRC 7

#define TFT_SCK 12
#define TFT_SDA 11
#define TFT_RES 14
#define TFT_DC  13
#define TFT_BLK 10

#define LED_R 47
#define LED_G 39
#define LED_B 21

// ================= BIẾN TOÀN CỤC =================
WebSocketsClient webSocket;
Adafruit_ST7789 tft = Adafruit_ST7789(-1, TFT_DC, TFT_RES);
bool is_authenticated = false;
String sessionId = "s1";
String robotId = "esp32s3-01";

// Buffer âm thanh dùng PSRAM
const int SAMPLE_RATE = 16000;
const int RECORD_TIME = 3; // Giây
uint8_t* audioBuffer;

// ================= QUẢN LÝ TRẠNG THÁI LED =================
void setStatus(uint16_t color) {
  digitalWrite(LED_R, (color == ST77XX_RED));
  digitalWrite(LED_G, (color == ST77XX_GREEN));
  digitalWrite(LED_B, (color == ST77XX_BLUE));
}

// ================= KHỞI TẠO ÂM THANH I2S =================
void initI2S() {
  // Config Loa (TX)
  i2s_config_t tx_config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = SAMPLE_RATE,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
    .communication_format = I2S_COMM_FORMAT_STAND_I2S,
    .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
    .dma_buf_count = 8,
    .dma_buf_len = 64
  };
  i2s_driver_install(I2S_NUM_0, &tx_config, 0, NULL);
  i2s_pin_config_t tx_pins = { .bck_io_num = I2S_SPK_BCLK, .ws_io_num = I2S_SPK_LRC, .data_out_num = I2S_SPK_DIN, .data_in_num = -1 };
  i2s_set_pin(I2S_NUM_0, &tx_pins);

  // Config Mic (RX)
  i2s_config_t rx_config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
    .sample_rate = SAMPLE_RATE,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
    .communication_format = I2S_COMM_FORMAT_STAND_I2S,
    .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
    .dma_buf_count = 8,
    .dma_buf_len = 64
  };
  i2s_driver_install(I2S_NUM_1, &rx_config, 0, NULL);
  i2s_pin_config_t rx_pins = { .bck_io_num = I2S_MIC_SCK, .ws_io_num = I2S_MIC_WS, .data_out_num = -1, .data_in_num = I2S_MIC_SD };
  i2s_set_pin(I2S_NUM_1, &rx_pins);
}

// ================= XỬ LÝ WEBSOCKET =================
void webSocketEvent(WStype_t type, uint8_t * payload, size_t length) {
  switch(type) {
    case WStype_DISCONNECTED:
      Serial.println("[WS] Disconnected!");
      setStatus(ST77XX_RED);
      break;
    case WStype_CONNECTED:
      Serial.println("[WS] Connected!");
      sendHello();
      break;
    case WStype_TEXT:
      handleJsonMessage(payload);
      break;
    case WStype_BIN:
      Serial.printf("[WS] Nhan Audio TTS: %u bytes\n", length);
      size_t written;
      i2s_write(I2S_NUM_0, payload, length, &written, portMAX_DELAY);
      break;
  }
}

void sendHello() {
  StaticJsonDocument<200> doc;
  doc["type"] = "HELLO";
  doc["sessionId"] = sessionId;
  doc["robotId"] = robotId;
  String out;
  serializeJson(doc, out);
  webSocket.sendTXT(out);
}

void handleJsonMessage(uint8_t * payload) {
  StaticJsonDocument<512> doc;
  deserializeJson(doc, payload);
  const char* type = doc["type"];

  if (strcmp(type, "ACK") == 0) {
    Serial.println("Server da san sang!");
    setStatus(ST77XX_GREEN);
  } else if (strcmp(type, "ASSISTANT_REPLY") == 0) {
    const char* text = doc["content"];
    tft.fillScreen(ST77XX_BLACK);
    tft.setCursor(0, 50);
    tft.println(text);
  }
}

// ================= GHI ÂM VÀ GỬI AUDIO =================
void recordAndSend() {
  Serial.println("Bat dau ghi am...");
  setStatus(ST77XX_BLUE);

  // 1. Gui AUDIO_START
  StaticJsonDocument<256> startDoc;
  startDoc["type"] = "AUDIO_START";
  startDoc["sessionId"] = sessionId;
  startDoc["robotId"] = robotId;
  startDoc["utteranceId"] = "u1";
  startDoc["format"] = "PCM_16BIT"; // Base code dùng PCM để đơn giản hóa, server bạn có thể dùng FFmpeg chuyển đổi
  startDoc["sampleRate"] = 16000;
  String out;
  serializeJson(startDoc, out);
  webSocket.sendTXT(out);

  // 2. Doc va gui lien tuc (Streaming)
  const int chunk_size = 2048;
  uint8_t data[chunk_size];
  unsigned long start_time = millis();

  while (millis() - start_time < 5000) { // Ghi am 5 giay
    size_t read_bytes;
    i2s_read(I2S_NUM_1, data, chunk_size, &read_bytes, portMAX_DELAY);
    webSocket.sendBIN(data, read_bytes);
    webSocket.loop(); // Giu ket noi WS
  }

  // 3. Gui AUDIO_END
  webSocket.sendTXT("{\"type\":\"AUDIO_END\",\"sessionId\":\"s1\",\"utteranceId\":\"u1\"}");
  setStatus(ST77XX_GREEN);
}

void setup() {
  Serial.begin(115200);

  // Init LED & TFT
  pinMode(LED_R, OUTPUT); pinMode(LED_G, OUTPUT); pinMode(LED_B, OUTPUT);
  pinMode(TFT_BLK, OUTPUT); digitalWrite(TFT_BLK, HIGH);
  SPI.begin(TFT_SCK, -1, TFT_SDA);
  tft.init(240, 240);
  tft.setRotation(2);
  tft.fillScreen(ST77XX_BLACK);
  tft.setTextColor(ST77XX_WHITE);
  tft.println("Robot Connecting...");

  // Init Wifi
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) delay(500);

  // Init Audio & WS
  initI2S();
  webSocket.begin(ws_host, ws_port, "/ws/robot");
  webSocket.onEvent(webSocketEvent);
  webSocket.setReconnectInterval(5000);
}

void loop() {
  webSocket.loop();

  // Gia lap: Nhan nut (hoac lenh tu serial) de bat dau noi
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'r') recordAndSend();
  }
}