FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r app && useradd -r -g app -m app \
    && apt-get update && apt-get install -y --no-install-recommends wget gosu \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY app.jar app.jar
RUN mkdir -p /app/storage /tmp/uploads \
    && chown -R app:app /app /tmp/uploads
RUN printf '#!/bin/sh\nchmod -R 777 /app/logs 2>/dev/null; true\nexec gosu app java -jar /app/app.jar\n' > /entrypoint.sh \
    && chmod +x /entrypoint.sh
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=90s \
    CMD wget -qO- http://localhost:${API_PORT:-8090}/health | grep -q UP || exit 1
ENTRYPOINT ["/entrypoint.sh"]