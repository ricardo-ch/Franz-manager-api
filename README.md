### Franz-manager-api

This api should work with the front-end franz-manager --> [here](https://github.com/GreenCom-Networks/Franz-manager)

#### Development

First, install the dependencies by running `mvn clean package`.

Then, execute the class `FranzManagerApi.java`.

Api should be available at localhost:1337
Apidoc can be found here --> localhost:1337/franz-manager-api/apidoc/

#### Docker

Install dependencies like previous step.

Build your docker : `docker build -t franz-manager-api .`

Then run it : `docker run -e JVM_HEAP_SIZE=512 -e KAFKA_BROKERS="YOUR_KAFKA_BROKERS_STRING"  -p 1337:1337 franz-manager-api`

Api should be available at localhost:1337
Apidoc can be found here --> localhost:1337/franz-manager-api/apidoc/
