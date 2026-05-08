# ─── Estágio 1: Build ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copia os arquivos do Maven wrapper e o pom.xml primeiro (cache de dependências)
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Baixa as dependências sem compilar o código (camada cacheável)
RUN ./mvnw dependency:go-offline -q

# Copia o código-fonte e compila o JAR
COPY src src
RUN ./mvnw package -DskipTests -q

# ─── Estágio 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Cria um usuário não-root por segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia apenas o JAR gerado pelo estágio de build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
