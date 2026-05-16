import os
import subprocess
import tempfile

from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel


PIPER_BIN = os.getenv("PIPER_BIN", "piper")
PIPER_MODEL = os.getenv("PIPER_MODEL", "cuda")

app = FastAPI(title="Local TTS Service")


class TtsRequest(BaseModel):
    text: str


@app.get("/health")
def health():
    return {"status": "ok", "piper_model": PIPER_MODEL}


@app.post("/synthesize")
def synthesize(req: TtsRequest):
    if not req.text or not req.text.strip():
        raise HTTPException(status_code=400, detail="text is required")
    if not PIPER_MODEL:
        raise HTTPException(status_code=500, detail="PIPER_MODEL is not configured")

    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as out_file:
        out_path = out_file.name

    try:
        cmd = [PIPER_BIN, "-m", PIPER_MODEL, "-f", out_path]
        completed = subprocess.run(
            cmd,
            input=req.text.encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if completed.returncode != 0:
            raise HTTPException(
                status_code=500,
                detail=f"Piper failed: {completed.stderr.decode('utf-8', errors='ignore')}",
            )

        with open(out_path, "rb") as f:
            audio_bytes = f.read()
        return Response(content=audio_bytes, media_type="audio/wav")
    finally:
        if os.path.exists(out_path):
            os.remove(out_path)
