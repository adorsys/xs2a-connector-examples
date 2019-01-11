# Running



This command will start all services in the project /xs2a-connector-starter and run a sample xs2a request. Get a look at /xs2a-connector-starter/pom.xml to see the commands used to start each micro service. 

To get this up and working you need https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a branch develop with some minor fixes. After the branches https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/tree/bugfix/fpo-making-package-private-interfaces-public and https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/tree/PR_h2_liquibase_support are merged to develop.


```
> mvn clean install

```

with this command we can also get all services up and running in docker.
 
```
docker-compose -f docker-compose-all-with-h2.yml up

```

To cleanup, use following commands

```
> docker-compose -f docker-compose-all-with-h2.yml down

> docker stop $(docker ps -aq)

> docker rm $(docker ps -aq)

> docker volume rm xs2a-connector-examples_xs2a-gateway-data

```

