FROM openjdk:17-alpine
COPY /target/ThumbnailTester-0.0.1-SNAPSHOT.jar URL_Shortener.jar

ENTRYPOINT ["java","-jar","URL_Shortener.jar"]