# Phan tich thuat toan VAD trong `robotic_esp32.ino`

## Ket luan nhanh
VAD trong file nay la **thuat toan rule-based nhieu dieu kien**, khong dung ML.
No quyet dinh co giong noi dua tren:
- Muc nang luong khung am (`avgAbs`)
- Tan suat doi dau (`ZCR`)
- Hinh dang dinh/nang luong (`peakToAvg`)
- Nguong dong theo nhieu nen (EMA noise floor)
- Bo dem so frame lien tiep de chong nhieu
- Them chan echo khi loa dang phat (mic/reference ratio)

## 1) Dac trung am thanh duoc trich tu moi frame
Ham `analyzePcm16Frame(...)` (quanh dong 1761) tinh:
- `avgAbs`: trung binh tri tuyet doi bien do mau PCM16
- `peakAbs`: bien do dinh trong frame
- `zcr`: so lan doi dau (Zero Crossing Rate dang dem crossings)
- `peakToAvg = peakAbs / avgAbs`

Frame dau vao co kich thuoc `QA_MIC_CHUNK_BYTES = 640`, tuong duong **20 ms** o 16 kHz mono 16-bit.

## 2) VAD chinh khi thu cau noi QA (`QA_STEP_STREAM_MIC`)
Luong nam quanh dong 2999+.

### 2.1. Nguong dong theo nhieu
- `threshold = max(g_qaNoiseEma * 1.28, 180)`
- `g_qaNoiseEma` cap nhat EMA khi frame khong du manh:
  - `noise = (1 - 0.06) * noise + 0.06 * frameAbs`
  - Co chan san noise toi thieu 80

### 2.2. Dieu kien frame noi
- `speechStrong`:
  - `frameAbs >= threshold`
  - `zcr` trong `[6, 140]`
  - `peakToAvg <= 14`
- `speechWeak` (dung de giu trang thai dang noi):
  - `frameAbs >= 0.65 * threshold`
  - `zcr` trong `[4, 170]`
  - `peakToAvg <= 14`

### 2.3. Quyet dinh bat dau noi
- Truoc khi bat dau, frame duoc day vao **pre-roll buffer** (`QA_PREROLL_FRAMES = 12`, ~240 ms) de khong mat am dau cau.
- `g_qaSpeechHitCount` tang khi `speechStrong`, giam/reset khi yeu.
- Bat dau ghi khi:
  - `hitCount >= QA_SPEECH_HIT_FRAMES = 2` (khoang 40 ms lien tiep), hoac
  - `forceStart`: da cho >= 1000 ms va co >= 1 hit.
- Khi start: flush pre-roll len WS roi stream realtime.

### 2.4. Quyet dinh ket thuc noi
Sau khi da start:
- Moi frame neu `speechWeak` thi cap nhat `g_qaLastSpeechMs`.
- Ket thuc boi:
  - **Im lang du lau**: `utterMs >= 500 ms` va `silenceMs >= 850 ms`, hoac
  - **Qua dai**: `utterMs >= 9000 ms`.

## 3) VAD tu kich hoat ngat loi (`tickAutoQaTrigger`)
Luong quanh dong 1893+ de tu phat hien nguoi dung goi robot.

### 3.1. Co che nguong theo ngu canh
Nguong nen:
- `threshold = max(g_autoQaNoiseEma * 1.28, 190)`

Neu dang phat story:
- tang nguong len it nhat `max(noise*1.55, 220)`
- tang so hit can thiet len `4`

Neu dang cho TTS QA (barge-in):
- tang nguong len it nhat `max(noise*2.10, 320)`
- tang so hit len `7`

### 3.2. Dieu kien speech candidate
- `frameAbs >= threshold`
- `zcr` hop le:
  - mode thuong: `[7, 145]`
  - mode story/qa_tts: `[8, 145]`
- `peakToAvg` hop le:
  - mode thuong: `<= 15`
  - mode story/qa_tts: `[1.20, 12]`

### 3.3. Chong bat nham tieng loa (echo dominance reject)
Khi loa dang phat, thuat toan so sanh mic voi reference:
- Uoc luong `refAbs` tu vong dem tin hieu loa (`estimateAecReferenceAbs`)
- Neu `refAbs` du lon:
  - mode story: yeu cau `mic/ref >= 0.58`
  - mode qa_tts: yeu cau `mic/ref >= 0.90`
- Neu thap hon nguong, loai frame khoi speech candidate.

### 3.4. Xac nhan trigger
- Co bo dem `g_autoQaSpeechHits`.
- Dat `requiredHits` thi goi `startQaInterrupt("vad_auto")` hoac `startQaInterrupt("vad_barge_in")`.

## 4) Ban chat nhan dien giong nguoi noi
Thuat toan nhan dien "giong nguoi" bang cach ket hop:
- **Bien do thich nghi theo nhieu nen** (adaptive energy threshold)
- **Dac trung pho thoi gian don gian** (`zcr`, `peakToAvg`)
- **Tinh lien tuc theo thoi gian** (hit frames, silence timeout)
- **Nhan biet boi canh phat loa** (nguong nghiem hon + ti le mic/ref)

Noi ngan gon: day la VAD thu cong duoc tinh chinh kha ky cho embedded realtime, uu tien do on dinh va do tre thap.
