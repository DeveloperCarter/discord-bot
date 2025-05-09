# Use an official OpenJDK runtime as the base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the Gradle wrapper and project files into the container
COPY gradlew /app/
COPY gradle /app/gradle
COPY build.gradle /app/
COPY settings.gradle /app/

# Make gradlew executable
RUN chmod +x gradlew

# Copy the source code to the container
COPY src /app/src

# Build the project using Gradle
RUN ./gradlew build

# Copy the .env file into the container
COPY .env /app/.env

# Expose the port your application will run on (optional)
EXPOSE 8080

# Command to run the bot
CMD ["./gradlew", "run"]