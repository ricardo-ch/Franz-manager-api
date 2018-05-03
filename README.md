### Franz-manager-api

This api should work with the front-end franz-manager --> [here](https://github.com/GreenCom-Networks/Franz-manager)

### Environment variables

The api needs 2 environment variables to work :

KAFKA_BROKERS (ie -> '127.0.0.2:2181,127.0.0.3:2181,127.0.0.4:2181')

KAFKA_BROKERS_JMX (ie -> '127.0.0.2:9997,127.0.0.3:9997,127.0.0.4:9997')

#### Development

First, install the dependencies by running `mvn clean package`.

Then, execute the class `FranzManagerApi.java`.

Api should be available at localhost:1337
Apidoc can be found here --> localhost:1337/franz-manager-api/apidoc/

#### Docker

Install dependencies like previous step.

Build your docker : `docker build -t franz-manager-api .`

Then run it : `docker run -e JVM_HEAP_SIZE=512 -e KAFKA_BROKERS="YOUR_KAFKA_BROKERS_STRING" -e KAFKA_BROKERS_JMX="YOUR_KAFKA_BROKERS_JMX"  -p 1337:1337 franz-manager-api`

Api should be available at localhost:1337
Apidoc can be found here --> localhost:1337/franz-manager-api/apidoc/
