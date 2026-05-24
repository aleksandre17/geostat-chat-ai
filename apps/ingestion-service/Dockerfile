FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8093
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
    CMD wget -qO- http://localhost:${INGESTION_PORT:-8093}/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
