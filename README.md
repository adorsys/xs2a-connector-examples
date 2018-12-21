# xs2a-connector-examples

XS2A Connector for the ledgers project

You can run XS2A Connector in two modes: `Embedded` and `Remote`. Both modes use `PostgreSQL` database as a storage

The following steps will get this connector up and running:

### Embedded mode
- Connector contains all XS2A modules inside
- Ledgers and database run separately
```
> git clone https://github.com/adorsys/xs2a-connector-examples.git
> cd xs2a-connector-examples
> mvn clean install
> docker pull adorsys/ledgers
> docker pull adorsys/xs2a-connector-examples
> docker-compose up
```

### Remote mode
- Connector contains only XS2A implementation
- ASPSP Profile runs separately
- Consent management and database run separately
- Ledgers and database run separately
```
> git clone https://github.com/adorsys/xs2a-connector-examples.git
> cd xs2a-connector-examples
> mvn clean install -Dremote
> docker pull adorsys/ledgers
> docker pull adorsys/xs2a-connector-examples
> docker pull adorsys/xs2a-consent-management
> docker pull adorsys/xs2a-aspsp-profile
> docker-compose -f docker-compose-remote.yml up
```