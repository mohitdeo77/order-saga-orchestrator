-- Database-per-service: each microservice owns its data and no other service reads it
CREATE DATABASE orchestrator;
CREATE DATABASE orders;
CREATE DATABASE inventory;
CREATE DATABASE payments;
