FROM adorsys/openjdk-jre-base:8-minideb

MAINTAINER https://github.com/adorsys/xs2a-connector-examples

ENV SERVER_PORT 8089
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n

WORKDIR /opt/xs2a-connector-examples

COPY ./gateway-app/target/gateway-app.jar /opt/xs2a-connector-examples/xs2a-gateway.jar

EXPOSE 8089 8000

CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/xs2a-connector-examples/xs2a-gateway.jar
