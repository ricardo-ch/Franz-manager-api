FROM docker.gcn-lab.fr:5043/greencom-networks/oracle-java8

ENV APP franz-manager-api
WORKDIR /usr/local/GCN/$APP

COPY apidoc apidoc

COPY target/$APP-jar-with-dependencies.jar $APP.jar

CMD java -Xmx${JVM_HEAP_SIZE}m -XX:+ExitOnOutOfMemoryError -jar $APP.jar