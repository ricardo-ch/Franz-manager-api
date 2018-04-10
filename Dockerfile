FROM java:8-alpine

ENV APP franz-manager-api
WORKDIR /usr/local/$APP

COPY apidoc apidoc

COPY target/$APP-jar-with-dependencies.jar $APP.jar

CMD java -Xmx${JVM_HEAP_SIZE}m -XX:+ExitOnOutOfMemoryError -jar $APP.jar