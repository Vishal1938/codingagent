# ──────────────────────────────────────────────────────────────────────────
# Stage 1: Build the Spring Boot jar
# ──────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Cache Maven dependencies — copy pom.xml first, download deps, then copy source.
# This way changes to source code don't re-download all dependencies on every build.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ──────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime image — smaller, with Tesseract installed
# ──────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Install Tesseract OCR + native libraries that Tess4J's JNI needs
# - tesseract-ocr        → the CLI tool
# - tesseract-ocr-eng    → English language data (tessdata)
# - libtesseract-dev     → provides libtesseract.so (the .so file Tess4J loads)
# - libleptonica-dev     → image-processing library Tesseract depends on
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    libtesseract-dev \
    libleptonica-dev \
    && rm -rf /var/lib/apt/lists/*

# Tell JNA where to find the native libraries
ENV LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/codingagent-0.1.jar app.jar

# Railway sets PORT — Spring Boot uses it via ${PORT:8080} in application.properties
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
