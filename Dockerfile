FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 as build

LABEL maintainer="Couchbase"

WORKDIR /app

ADD . /app

# Install project dependencies and generate jar file
RUN sbt assembly

# Multistage build to produce a small final image
FROM openjdk:8u292-slim-buster as run

WORKDIR /app

COPY --from=build /app/target/scala-2.12/try-cb-scala-assembly-1.0-SNAPSHOT.jar /app/
ADD fts-hotels-index.json /app/
ADD mix-and-match.yml /app/
ADD *.sh /app/
ADD conf/ /app/

RUN apt-get update
RUN apt-get install -y jq curl
RUN chmod +x wait-for-couchbase.sh

# Expose ports
EXPOSE 8080

# Set the entrypoint
ENTRYPOINT ["./wait-for-couchbase.sh", "java", "-jar", "try-cb-scala-assembly-1.0-SNAPSHOT.jar"]

