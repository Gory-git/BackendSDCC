# Build con l'immagine Gradle 8.14 (stessa versione del wrapper del progetto):
# invocare `gradle` invece di `./gradlew` evita il problema dei permessi di
# esecuzione e dei fine riga CRLF, che su Windows si perdono nel COPY.
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /app

# Prima solo i file di build: se le dipendenze non cambiano, Docker riusa il
# layer e i build successivi non riscaricano mezzo Maven Central.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true

COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utente non privilegiato: se qualcuno esce dall'applicazione, non trova root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/build/libs/*.jar app.jar

# Il tetto di heap è esplicito perché l'istanza ha poca RAM e la JVM, lasciata
# libera, ne prenderebbe un quarto senza servirle.
ENV JAVA_OPTS="-Xmx320m -XX:MaxMetaspaceSize=128m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
