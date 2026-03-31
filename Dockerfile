# Use lightweight Java image
# ---------- BUILD STAGE ----------
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy everything
COPY . .

# Give permission to mvnw
RUN chmod +x mvnw

# Build jar (skip tests 🔥)
RUN ./mvnw clean package -DskipTests


# ---------- RUN STAGE ----------
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Run app
ENTRYPOINT ["java", "-jar", "app.jar"]