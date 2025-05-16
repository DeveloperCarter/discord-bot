# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build

# JVM options for lighter memory footprint during build (optional)
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx384m -Xms128m -XX:MaxRAMPercentage=75"

WORKDIR /app

# Copy only Gradle wrapper files first to leverage caching of Gradle distribution and dependencies
COPY gradlew .
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/

RUN chmod +x ./gradlew

# Pre-cache Gradle dependencies and distribution
RUN ./gradlew --no-daemon --version

# Now copy rest of the project files
COPY . .

# Build the fat jar (shadowJar)
RUN ./gradlew clean shadowJar --no-daemon

# Stage 2: Runtime - Minimal image
FROM eclipse-temurin:17-jre-alpine

# JVM options to limit memory usage in container
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx384m -Xms128m -XX:MaxRAMPercentage=75"

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/discord-bot-final-1.0-SNAPSHOT-all.jar ./app.jar

# Expose any port your bot might need (optional, mostly for web apps)
EXPOSE 8080

# Run the jar
CMD ["java", "-jar", "app.jar"]
