import os
import tempfile
import wave

from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from faster_whisper import WhisperModel


MODEL_SIZE = os.getenv("WHISPER_MODEL", "small")
DEVICE = os.getenv("WHISPER_DEVICE", "cuda")
COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
LANGUAGE = os.getenv("WHISPER_LANGUAGE", "vi")

app = FastAPI(title="Local STT Service")
model = WhisperModel(MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_SIZE, "device": DEVICE}


@app.post("/transcribe")
async def transcribe(
    audio: UploadFile = File(...),
    sample_rate: int = Form(16000),
    channels: int = Form(1),
    format: str = Form("OGG_OPUS"),
):
    if audio is None:
        raise HTTPException(status_code=400, detail="audio is required")

    raw_bytes = await audio.read()
    normalized_format = format.upper()
    if channels <= 0:
        channels = 1
    if sample_rate <= 0:
        sample_rate = 16000

    if normalized_format == "OGG_OPUS":
        with tempfile.NamedTemporaryFile(delete=False, suffix=".ogg") as temp_file:
            temp_path = temp_file.name
            temp_file.write(raw_bytes)
    elif normalized_format == "PCM_16BIT":
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
            temp_path = temp_file.name
        with wave.open(temp_path, "wb") as wav_file:
            wav_file.setnchannels(channels)
            wav_file.setsampwidth(2)
            wav_file.setframerate(sample_rate)
            wav_file.writeframes(raw_bytes)
    else:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".bin") as temp_file:
            temp_path = temp_file.name
            temp_file.write(raw_bytes)

    try:
        segments, _ = model.transcribe(temp_path, language=LANGUAGE, vad_filter=True)
        text = " ".join([seg.text.strip() for seg in segments]).strip()
        return {
            "text": text,
            "sample_rate": sample_rate,
            "channels": channels,
            "format": format,
        }
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)
