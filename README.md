# mini-hie

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml up -d;

## Wait a bit

docker-compose -f mrs-debezium.yml up -d;

docker-compose -f data-warehouse.yml up -d;

## GoFR Facility Registry

docker-compose -f gofr.yml up -d 

## IHRIS 

docker-compose -f ihris.yml up -d 

-- For Local Testing Navigate to http://localhost:3008

## Old way

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml -f mrs-debezium.yml -f data-warehouse.yml up -d;

## Bring all down

docker-compose -f impilo.yml -f hapi-fhir.yml  -f openhim.yml -f kafka.yml -f mrs-debezium.yml -f data-warehouse.yml -f gofr.yml -f ihris.yml down;