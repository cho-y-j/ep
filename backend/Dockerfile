FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
# S-9-C: 작업계획서 DOCX → PDF 변환에 LibreOffice headless 필요
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice-writer libreoffice-core fonts-noto-cjk fonts-nanum gosu \
    && rm -rf /var/lib/apt/lists/* \
    # non-root user (uploads volume 쓰기 권한)
    && groupadd -r skep && useradd -r -g skep -u 10001 -m -d /home/skep skep \
    && mkdir -p /app/uploads && chown -R skep:skep /app /home/skep
WORKDIR /app
COPY --from=build --chown=skep:skep /app/target/skep-backend.jar app.jar
# Docker volume 이 마운트되면 /app/uploads 가 root:root 로 덮어씌워짐 → entrypoint 에서 chown 후 skep 사용자로 전환.
COPY <<'EOF' /entrypoint.sh
#!/bin/sh
set -e
chown -R skep:skep /app/uploads || true
exec gosu skep java -jar /app/app.jar
EOF
RUN chmod +x /entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
