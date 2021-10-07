# Couchbase Scala Travel-Sample Application

This is a sample application for getting started with [Couchbase Server] and the [Scala SDK].
The application runs a single page web UI for demonstrating SQL for Documents (N1QL), Sub-document requests and Full Text Search (FTS) querying capabilities.
It uses Couchbase Server together with the [Play] web framework for [Scala], [Vue] and [Bootstrap].

The application is a flight planner that allows the user to search for and select a flight route (including the return flight) based on airports and dates.
Airport selection is done dynamically using an autocomplete box bound to N1QL queries on the server side. After selecting a date, it then searches
for applicable air flight routes from a previously populated database. An additional page allows users to search for Hotels using less structured keywords.


## Prerequisites

To download the application you can clone the repository:

    git clone https://github.com/couchbaselabs/try-cb-scala.git

<!-- If you want to run the application from your IDE rather than from the command line you also need your IDE set up to
work with maven-based projects. We recommend running IntelliJ IDEA, but Eclipse or Netbeans will also work. -->

We recommend running the application with Docker, which starts up all components for you,
but you can also run it in a Mix-and-Match style, which we'll decribe below.

## Running the application with Docker

You will need [Docker](https://docs.docker.com/get-docker/) installed on your machine in order to run this application as we have defined a [_Dockerfile_](Dockerfile) and a [_docker-compose.yml_](docker-compose.yml) to run Couchbase Server 7.0.0, the front-end [Vue app](https://github.com/couchbaselabs/try-cb-frontend-v2.git) and the Scala REST API.

To launch the full application, simply run this command from a terminal:

    docker-compose up

> **_NOTE:_** You may need more than the default RAM to run the images.
We have tested the travel-sample apps with 4.5 GB RAM configured in Docker's Preferences... -> Resources -> Memory.
When you run the application for the first time, it will pull/build the relevant docker images, so it might take a bit of time.

This will start the Scala Play backend, Couchbase Server 7.0.0 and the Vue frontend app.

You should then be able to browse the UI, search for US airports and get flight
route information.

To end the application press <kbd>Control</kbd>+<kbd>C</kbd> in the terminal
and wait for docker-compose to gracefully stop your containers.

## Mix and match services

Instead of running all services, you can start any combination of `backend`,
`frontend`, `db` via docker, and take responsibility for starting the other
services yourself.

As the provided `docker-compose.yml` sets up dependencies between the services,
to make startup as smooth and automatic as possible, we also provide an
alternative `mix-and-match.yml`. We'll look at a few useful scenarios here.

### Bring your own database

If you wish to run this application against your own configuration of Couchbase
Server, you will need version 7.0.0 or later with the `travel-sample`
bucket setup.

> **_NOTE:_** If you are not using Docker to start up the API server, or the
> provided wrapper `wait-for-couchbase.sh`, you will need to create a full text
> search index on travel-sample bucket called 'hotels-index'. You can do this
> via the following command:

    curl --fail -s -u <username>:<password> -X PUT \
            http://<host>:8094/api/index/hotels-index \
            -H 'cache-control: no-cache' \
            -H 'content-type: application/json' \
            -d @fts-hotels-index.json

With a running Couchbase Server, you can pass the database details in:

    CB_HOST=10.144.211.101 CB_USER=Administrator CB_PSWD=password docker-compose -f mix-and-match.yml up backend frontend

The Docker image will run the same checks as usual, and also create the
`hotels-index` if it does not already exist.

### Running the Scala API application manually

You may want to run the Scala application yourself, to make rapid changes to it,
and try out the features of the Couchbase API, without having to re-build the Docker
image. You may still use Docker to run the Database and Frontend components if desired.

Please ensure that you have the following before proceeding.

* Java (Java 11 LTS is recommended. Versions from Java 8+ should work, but note that the recently released Java 17 LTS is not yet supported by the Play framework)
* SBT

There is some Couchbase preparation required, including installing the `travel-sample` bucket, and a required Full Text Search index (in `fts-hotels.index.json`).
You can run the provided `wait-for-couchbase.sh` script to take care of this, after setting the CB_HOST, CB_USER and CB_PSWD environment variables to point to your Couchbase cluster.  E.g.

    export CB_HOST=localhost CB_USER=Administrator CB_PSWD=password
    ./wait-for-couchbase.sh

Now, edit `conf/application.conf` and change the Couchbase configuration to point at your cluster.
Specifically this field `couchbase.host`, and possibly `couchbase.username` and `couchbase.password` if you are following the best practice of setting up a non-Administrator user for data reads and writes.
They should match the CB_HOST, CB_USER and CB_PSWD environment variables, if you used the wait-for-couchbase.sh script.

Compile the application into a single 'fatjar' and run it:

    sbt assembly
    java -jar target/scala-2.12/try-cb-scala-assembly-1.0-SNAPSHOT.jar

The Scala play backend is now running, on http://localhost:8080/.  Navigating to this page in the browser will show:

    Congratulations, the Couchbase Scala SDK backend for the travel sample application is now running.

It is now ready to be used by the frontend.

As an alternative to building and running on the command-line, you can also open this SBT project inside an IDE such as Jetbrains IntelliJ, and create and execute a 'Play 2 App' config to build and run.

If you want to see how the sample frontend Vue application works with your changes,
run it with:

    docker-compose -f mix-and-match.yml up frontend

### Running the front-end manually

To run the frontend components manually without Docker, follow the guide
[here](https://github.com/couchbaselabs/try-cb-frontend-v2)

[Couchbase Server]: https://www.couchbase.com/
[Scala SDK]: https://docs.couchbase.com/scala-sdk/current/hello-world/overview.html
[Spring Boot]: https://spring.io/projects/spring-boot
[Scala]: https://www.scala-lang.org/
[Swagger]: https://swagger.io/resources/open-api/
[Vue]: https://vuejs.org/
[Bootstrap]: https://getbootstrap.com/
