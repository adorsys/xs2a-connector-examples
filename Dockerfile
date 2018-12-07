FROM adorsys/openjdk-jre-base:8-minideb

MAINTAINER https://github.com/adorsys/xs2a-connector-examples

ENV SERVER_PORT 8089
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m

WORKDIR /opt/xs2a-connector-examples

COPY ./gateway-app/target/xs2a-connector-examples.jar /opt/xs2a-connector-examples/xs2a-connector-examples.jar

EXPOSE 8089

CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/xs2a-connector-examples/xs2a-connector-examples.jar
