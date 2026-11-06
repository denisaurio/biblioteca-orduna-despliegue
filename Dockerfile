# =====================================================================
#  Sistema de Biblioteca Orduña - imagen de produccion (Coolify)
#
#  UN solo contenedor con todo el sistema:
#    1) compila el frontend (React + Vite)
#    2) lo mete dentro del backend (src/main/resources/static) y compila el jar
#    3) imagen final liviana: solo Java 17 (JRE) + el jar
#
#  La base de datos NO va aqui: es el otro recurso de Coolify (MySQL).
#  Los secretos (R2, BD, JWT) NO van en la imagen: se ponen como variables de
#  entorno en Coolify. El .env esta en .dockerignore.
# =====================================================================

# ---------- 1) Frontend ----------
FROM node:20-alpine AS frontend
WORKDIR /build
# Primero solo las dependencias: Docker las cachea mientras package*.json no cambie.
COPY biblioteca-orduna/frontend/package.json biblioteca-orduna/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY biblioteca-orduna/frontend/ ./
# Mismo origen que el backend: el navegador llama a /api del mismo dominio (sin CORS).
ENV VITE_API_URL=/api
RUN npm run build

# ---------- 2) Backend ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
# Dependencias de Maven en su propia capa (se descargan una vez y quedan en cache).
COPY biblioteca-orduna/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY biblioteca-orduna/src ./src
# El frontend compilado viaja dentro del jar.
COPY --from=frontend /build/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# ---------- 3) Imagen final ----------
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --home /app biblioteca
ENV TZ=America/Lima \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Lima -Dfile.encoding=UTF-8"
WORKDIR /app
COPY --from=backend /build/target/*.jar /app/app.jar
USER biblioteca
EXPOSE 8080
# Coolify marca el contenedor como sano cuando la API responde.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/salud > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
