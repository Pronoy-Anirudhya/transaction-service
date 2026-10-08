# transaction-service

Send Money transaction service for an MFS POC. Java 25 + Spring Boot 4.1.1 + PostgreSQL: validates
rules, prices fee/VAT/commission, enforces tier limits, calls the TigerBeetle-backed ledger service
synchronously for an atomic posting, repairs in-doubt transactions, and publishes confirmed events
to RabbitMQ.
