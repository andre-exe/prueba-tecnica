# etapa 1: compilar. se copia primero solo el pom para que las dependencias queden en una capa que se reutiliza
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# los tests de integracion necesitan docker (testcontainers), por eso aqui solo se empaqueta
COPY src ./src
RUN mvn -B -q package -DskipTests

# se separa el jar en capas (dependencias, aplicacion) para que reconstruir tras un cambio de codigo sea rapido
RUN java -Djarmode=tools -jar target/coworking-reservations-*.jar extract --layers --destination extracted

# etapa 2: imagen final, solo jre y usuario sin privilegios
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar coworking-reservations-0.0.1-SNAPSHOT.jar"]
