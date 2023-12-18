# mini-hie

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml up -d;

## Wait a bit

docker-compose -f mrs-debezium.yml up -d;

## Old way

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml -f mrs-debezium.yml up -d;

## Bring all down

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml -f mrs-debezium.yml down;