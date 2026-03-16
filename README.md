# Transaction API

REST API backend for a day trading journal. Track your stock and option trades, calculate realized P&L, and view performance statistics.

## What it does

This API provides endpoints to:
- **Log trades** - Record stock and option trades with entry/exit prices, quantities, and fees
- **Calculate P&L** - Automatically compute realized profit and loss for each trade
- **Track performance** - View daily and monthly P&L summaries with best day/month statistics
- **Multi-currency support** - Handle USD and CAD trades with automatic FX conversion
- **Share trades** - Generate shareable links for individual trades

Trades are stored per-user in PostgreSQL. Authentication is handled via Neon Auth (JWT tokens) with Google as the only provider for now. The API supports both long and short positions for stocks and options (calls/puts).

## Key Features

- **Aggregate Statistics** - Database-optimized queries for total P&L, trade counts, and best performing days/months across all time
- **Monthly Calendar View** - Browse trades by month with daily P&L rollups
- **Admin Panel** - View all users and their activity (admin-only)
- **Currency Conversion** - Automatic CAD to USD conversion using live exchange rates
- **Performance Indexed** - Optimized database indexes for fast aggregate queries on large trade datasets

## Tech Stack

- Spring Boot 3 / Java 21
- PostgreSQL with Flyway migrations
- JWT authentication (Neon Auth)
- Deployed on AWS App Runner

## Development

See [DEV.md](DEV.md) for setup instructions, API documentation, and deployment details.
