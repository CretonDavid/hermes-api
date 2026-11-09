# Construction de l'API (les tests tournent en développement, pas ici)
FROM eclipse-temurin:27-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q package -DskipTests && cp target/*.jar app.jar

FROM eclipse-temurin:27-jre
RUN useradd --system --no-create-home hermes
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER hermes
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
