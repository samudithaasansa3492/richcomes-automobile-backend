# Build-only container with Java 21; secrets are never provided during image build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests clean package && \
    mkdir -p /output && cp target/garage-*.jar /output/app.jar

# Non-root Java runtime, no DB password, JWT key or .env baked into the image.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 richcomes && \
    useradd --system --uid 10001 --gid richcomes --no-create-home richcomes
COPY --from=build --chown=10001:10001 /output/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
