FROM adorsys/java:11

LABEL maintainer="https://git.adorsys.de/adorsys/xs2a/xs2a-connector-examples"

ENV SERVER_PORT 8089
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m

WORKDIR /opt/gateway-app-embedded

USER 0
RUN mkdir -p /opt/gateway-app-embedded/logs/ && chmod 777 /opt/gateway-app-embedded/logs/
USER 1001

COPY ./target/gateway-app-embedded.jar /opt/gateway-app-embedded/gateway-app-embedded.jar

EXPOSE 8089
# hadolint ignore=DL3025
CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/gateway-app-embedded/gateway-app-embedded.jar
