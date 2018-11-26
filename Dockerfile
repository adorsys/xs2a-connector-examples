FROM adorsys/openjdk-jre-base:8-minideb

MAINTAINER https://git.adorsys.de/adorsys/xs2a/ledgers-xs2a-gateway

ENV SERVER_PORT 8089
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m

WORKDIR /opt/ledgers-xs2a-gateway

COPY ./gateway-app/target/ledgers-xs2a-gateway.jar /opt/ledgers-xs2a-gateway/ledgers-xs2a-gateway.jar

EXPOSE 8089

CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/ledgers-xs2a-gateway/ledgers-xs2a-gateway.jar
