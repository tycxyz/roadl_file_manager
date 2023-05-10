FROM adoptopenjdk/openjdk11:jdk-11.0.11_9-ubuntu-slim as builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM adoptopenjdk/openjdk11:jdk-11.0.11_9-ubuntu-slim
COPY --from=builder dependencies/ ./
COPY --from=builder spring-boot-loader/ ./
COPY --from=builder snapshot-dependencies/ ./
COPY --from=builder application/ ./

ENTRYPOINT ["java"\
,"org.springframework.boot.loader.JarLauncher"\
,"-Dspring.config.location=/configurations/application.yml"]