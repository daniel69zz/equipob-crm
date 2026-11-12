# Imagen de un microservicio del CRM a partir de su jar ya compilado (mvn package en backend/<servicio>).
FROM eclipse-temurin:21-jre-alpine
ARG SERVICIO
RUN apk add --no-cache wget && addgroup -S crm && adduser -S crm -G crm
WORKDIR /app
COPY backend/${SERVICIO}/target/${SERVICIO}-0.1.0-SNAPSHOT.jar app.jar
USER crm
ENV TZ=America/La_Paz JAVA_OPTS="-XX:MaxRAMPercentage=60"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
