FROM --platform=arm64 alpine:latest
RUN apk add openjdk17
WORKDIR /app
COPY /target/ircd-jar-with-dependencies.jar ./
EXPOSE 23
CMD ["java","-jar","./ircd-jar-with-dependencies.jar"]