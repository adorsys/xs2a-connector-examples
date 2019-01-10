FROM adorsys/openjdk-jre-base:8-minideb

MAINTAINER https://github.com/adorsys/xs2a-connector-examples

ENV SERVER_PORT 48080
ENV JAVA_OPTS -Xmx1024m
ENV JAVA_TOOL_OPTIONS -Xmx1024m

WORKDIR /opt/aspsp-profile

COPY ./xs2a-connector-starter/target/aspsp-profile/aspsp-profile.jar /opt/aspsp-profile/aspsp-profile.jar

EXPOSE 48080

CMD exec $JAVA_HOME/bin/java $JAVA_OPTS -jar /opt/aspsp-profile/aspsp-profile.jar
