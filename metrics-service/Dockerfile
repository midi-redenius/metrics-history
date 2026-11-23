FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY mvnw ./mvnw
RUN chmod +x mvnw

COPY metrics-common/pom.xml ./metrics-common/pom.xml
COPY metrics-service/pom.xml ./metrics-service/pom.xml
COPY collector/pom.xml ./collector/pom.xml
RUN ./mvnw dependency:go-offline -pl metrics-service -am -B -q || true

COPY metrics-common/src ./metrics-common/src
COPY metrics-service/src ./metrics-service/src
RUN ./mvnw package -pl metrics-service -am -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/metrics-service/target/metrics-service-*.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
