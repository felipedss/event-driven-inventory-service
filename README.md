# Inventory Service

A Spring Boot microservice responsible for inventory management in the event-driven order platform. It participates in distributed sagas as a Kafka consumer/producer, reserving stock as part of the order fulfillment flow coordinated by the Saga Orchestrator.

## Overview

| Property | Value |
|---|---|
| Port | `8082` |
| Java | 17 |
| Spring Boot | 4.0.3 |
| Database | PostgreSQL (`inventory_db`) |
| Messaging | Apache Kafka |

## Getting Started

### Prerequisites

Kafka and PostgreSQL must be running before starting the service. Start them via Docker Compose from the shared infrastructure:

```bash
cd ~/Workspace/event-driven-simulator/infrastructure/docker-compose
docker compose up -d
```

This provides:
- **Kafka** (KRaft mode) — external: `localhost:9094`, inter-container: `kafka:9092`
- **PostgreSQL** — `localhost:5432`, database: `inventory_db`

### Running the Service

```bash
mvn spring-boot:run
```

The service will be available at `http://localhost:8082`.

## API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/inventory/{productId}` | Get inventory item by product ID |
| `POST` | `/api/v1/inventory/` | Add a new inventory item |

### Example: Add Inventory

```bash
curl -X POST http://localhost:8082/api/v1/inventory/ \
  -H "Content-Type: application/json" \
  -d '{"productId": "prod-123", "availableQuantity": 100}'
```

### Example: Get Inventory

```bash
curl http://localhost:8082/api/v1/inventory/prod-123
```

## Kafka Integration

| Direction | Topic | Event |
|---|---|---|
| Consumes | `order.inventory.reserve` | `ReserveInventoryCommand` |
| Produces | `order.inventory.reserved` | `InventoryReservedEvent` |
| Produces | `order.inventory.failed` | `InventoryReservationFailedEvent` |

### Reservation Flow

1. Saga Orchestrator publishes a `ReserveInventoryCommand` to `order.inventory.reserve`
2. This service looks up the item by `productId`
3. If found and stock is sufficient → decrements quantity, publishes `InventoryReservedEvent`
4. Otherwise → publishes `InventoryReservationFailedEvent` with reason:
   - `"Product not found"`
   - `"Insufficient stock"`

## Development

```bash
mvn test                          # Run all tests
mvn test -Dtest=ClassName         # Run a single test class
mvn spotless:apply                # Auto-format (Google Java Format)
mvn spotless:check                # Verify formatting
mvn clean install                 # Full build
```

### Pre-Commit Checklist

Before committing:
1. `mvn spotless:apply`
2. `mvn spotless:check`
3. `mvn test`

## Project Structure

```
src/main/java/com/platform/inventoryservice/
├── config/           Kafka consumer and producer configuration
├── controller/       REST endpoints
├── service/          Reservation business logic
├── model/            InventoryItem JPA entity
├── repository/       Spring Data JPA repository
├── messaging/
│   ├── consumer/     Kafka listener for ReserveInventoryCommand
│   └── producer/     Kafka publisher for result events
├── event/
│   ├── inbound/      ReserveInventoryCommand DTO
│   └── outbound/     InventoryReservedEvent, InventoryReservationFailedEvent DTOs
└── exception/        InventoryItemNotFoundException, GlobalExceptionHandler
```