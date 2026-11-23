FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY mvnw ./mvnw
RUN chmod +x mvnw

COPY metrics-common/pom.xml ./metrics-common/pom.xml
COPY metrics-service/pom.xml ./metrics-service/pom.xml
COPY collector/pom.xml ./collector/pom.xml
RUN ./mvnw dependency:go-offline -pl collector -am -B -q || true

COPY metrics-common/src ./metrics-common/src
COPY collector/src ./collector/src
RUN ./mvnw package -pl collector -am -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/collector/target/collector-*.jar app.jar
RUN chown app:app app.jar

USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
