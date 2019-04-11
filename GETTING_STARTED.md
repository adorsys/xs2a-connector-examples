# Getting started

## Prerequisites

- Java JDK version 1.8.x, Maven 3.x;
- Relational database for Ledgers project. We recommend to use PostgreSQL 9.5;
- Docker.

This XS2A connector works with the XS2A and Ledgers projects. XS2A project is used as a maven dependency and Ledgers is
a separate project with the rest client as a dependency. Depending on the way you want to use the XS2A connector, you 
have 2 variants of launching: 

- XS2A connector with Ledgers as a standalone Java application with its database;
- XS2A connector with Ledgers and its database inside a docker container.

Please note: both ways use external ASPSP profile application and CMS application (from XS2A repository) and we consider 
that those projects are launched and accessible.

### XS2A connector with Ledgers as a standalone application

Ledgers project:
- Set up the PostgreSQL 9.5;
- Create the user `ledgers` with the password `ledgers`;
- Create the database with name `ledgers`;
- Clone the project and build it:
```bash
$ git clone https://github.com/adorsys/ledgers.git
$ cd ledgers
$ mvn clean install
```
- Launch the resulting jar file with the arguments: `-Dspring.profiles.active=postgres -Dledgers.mockbank.data.load=true`;

### XS2A connector with Ledgers inside the docker container

Ledgers project:
- Clone the project: https://github.com/adorsys/ledgers.git;
- Run `docker-compose up` in the root directory.

### XS2A connector

The XS2A connector itself is launched equally in both cases:
- Clone the project and build it:
```bash
$ git clone https://github.com/adorsys/xs2a-connector-examples.git
$ cd xs2a-connector-examples
$ mvn clean install
```
- Launch XS2A connector (LedgersXs2aGatewayApplication.java) as SpringBoot application with the active profile `mock-qwac`: `-Dspring.profiles.active=mock-qwac`

If you have any troubles, first check the properties file of XS2A connector: `xs2a-connector-examples/gateway-app/src/main/resources/application.yml`.
Here you can find the ports and URLs of other projects from this scope (in case of using non-default values). 

After doing all the steps from any variant mentioned before, you may try to launch requests to the specified XS2A endpoints and receive the results.
