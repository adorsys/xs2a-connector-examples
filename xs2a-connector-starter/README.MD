# Running

```
> cd xs2a-ctk
> java -jar target/fakesmtp/fakesmtp.jar -s -b -p 2500 -o target/fakesmtp/mails

```

```
> cd xs2a-ctk
> java -jar target/ledgers-app.jar --spring.profiles.active=h2 --ledgers.mockbank.data.load=true

````

```
> cd xs2a-ctk
> java -jar target/online-banking/online-banking-app.jar
```

```
> java -jar target/xs2a-gateway/xs2a-gateway-app.jar --spring.main.allow-bean-definition-overriding=true --spring.profiles.active=h2,mockspi
```