# ==========================================
# Stage 1: Build Spring Boot Application
# ==========================================
FROM maven:3.9.8-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy Maven source files and build fat jar
COPY . .
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Minimalist Lightweight Runtime
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user security practice
RUN addgroup -S saas && adduser -S saas -G saas

COPY --from=builder --chown=saas:saas /build/saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar app.jar

USER saas

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SERVER_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
