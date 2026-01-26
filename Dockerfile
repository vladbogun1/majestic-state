# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Europe/Moscow
ENV JAVA_OPTS=""

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /app/target/*.jar /app/app.jar

USER app
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
