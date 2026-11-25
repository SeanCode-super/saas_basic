FROM maven:3.9-eclipse-temurin-21 AS maven
FROM eclipse-temurin:27-jdk AS builder
COPY --from=maven /usr/share/maven /usr/share/maven
WORKDIR /build
COPY server/ server/
COPY web/src/views/industry/IndustryApplication.vue web/src/views/industry/
COPY web/src/api/modules/industry.ts web/src/api/modules/
COPY mobile/src/pages/industry/app.vue mobile/src/pages/industry/
COPY mobile/src/api/industry.ts mobile/src/api/
WORKDIR /build/server
RUN /usr/share/maven/bin/mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:27-jre
WORKDIR /app
RUN groupadd --system saas && useradd --system --gid saas saas && chown saas:saas /app
COPY --from=builder --chown=saas:saas /build/server/saas-basics-app/target/saas-basics-app-0.1.0-SNAPSHOT.jar app.jar
USER saas
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
