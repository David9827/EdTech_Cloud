# Local STT/TTS with Docker

This folder provides 2 containerized services:

- `stt` (faster-whisper) on port `8001`
- `tts` (piper wrapper) on port `8002`

Prerequisite for GPU STT:
- NVIDIA driver installed
- NVIDIA Container Toolkit installed for Docker

## 1) Prepare model

Put your `.onnx` voice model in:

`local-ai/models/vi_voice.onnx`
`tts` image downloads Piper binary automatically during build.
You can change Piper path/model in `local-ai/docker-compose.yml` (`PIPER_BIN`, `PIPER_MODEL`).

## 2) Start containers

```powershell
cd local-ai
docker compose up -d --build
```

Health checks:

- `http://127.0.0.1:8001/health`
- `http://127.0.0.1:8002/health`

## 3) Enable local mode in backend

Set in `src/main/resources/application.yml`:

```yaml
stt:
  provider: local
  local-url: http://127.0.0.1:8001/transcribe

tts:
  provider: local
  local-url: http://127.0.0.1:8002/synthesize
```

## 4) Stop containers

```powershell
cd local-ai
docker compose down
```
