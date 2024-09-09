# Use an official Temurin OpenJDK 21 runtime as a parent image
FROM eclipse-temurin:21-jdk

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Gradle build the application
RUN ./gradlew build

# Expose the port the app runs on
EXPOSE 37864

# Run the application
CMD ["java", "-jar", "build/libs/freerouting-executable.jar", "--api_server-enabled=true", "--feature_flags-save_jobs=1"]