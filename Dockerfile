FROM adorsys/java:8

MAINTAINER https://github.com/adorsys/xs2a-connector-examples

ENV SERVER_PORT 8089
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n

WORKDIR /opt/gateway-app

COPY ./gateway-app/target/gateway-app.jar /opt/gateway-app/gateway-app.jar

EXPOSE 8089 8000

CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/gateway-app/gateway-app.jar
