# ledgers-xs2a-gateway

XS2A Connector for the ledgers project

You can run XS2A Connector in two modes. These are `Embedded` and `Remote` modes. Both modes used `PostgreSQL` database as a storage

This is, you will have to follow these steps to have this connector up and running:

### Embedded mode
- Connector contains all XS2A stuff inside
- Ledgers and ledgers run separately
```
> git clone https://github.com/adorsys/ledgers.git
> cd ledgers
> mvn clean install -DskipITs
> docker build -t adorsys/ledgers .
> cd ..

> git clone https://git.adorsys.de/adorsys/xs2a/ledgers-xs2a-gateway.git
> cd ledgers-xs2a-gateway
> mvn clean install
> docker-compose build
> docker-compose up
```

### Remote mode
- Connector contains only XS2A implementation
- ASPSP Profile runs separately
- Consent management and database run separately
- Ledgers and database run separately
```
> git clone https://github.com/adorsys/ledgers.git
> cd ledgers
> mvn clean install -DskipITs
> docker build -t adorsys/ledgers .
> cd ..

> git clone https://git.adorsys.de/adorsys/xs2a/ledgers-xs2a-gateway.git
> cd ledgers-xs2a-gateway
> mvn clean install -Dremote
> docker-compose -f docker-compose-remote.yml build
> docker-compose -f docker-compose-remote.yml up
```