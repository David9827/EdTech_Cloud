FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

ARG PIPER_RELEASE_URL=https://github.com/rhasspy/piper/releases/latest/download/piper_linux_x86_64.tar.gz

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    tar \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/piper \
    && curl -fL "$PIPER_RELEASE_URL" -o /tmp/piper.tar.gz \
    && tar -xzf /tmp/piper.tar.gz -C /opt/piper --strip-components=1 \
    && chmod +x /opt/piper/piper \
    && rm -f /tmp/piper.tar.gz

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY app.py /app/app.py

EXPOSE 8002

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8002"]
