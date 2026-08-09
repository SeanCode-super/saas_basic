# SaaS Basics

SaaS Basics is an open-source-oriented, AI-native foundation for building enterprise applications across industries.

The project is not a vertical SaaS product or an admin dashboard template. It aims to provide the reusable platform capabilities that CRM, ERP, WMS, manufacturing, healthcare, education, government, and other enterprise applications repeatedly need:

- tenant and platform operations
- enterprise groups, legal entities, organizations, people, and assignments
- identity, sessions, roles, menus, API permissions, and data permissions
- applications, portals, configuration, feature flags, and audit
- module scaffolding, metadata, migrations, and code generation
- AI gateway, prompts, knowledge, agents, tools, MCP, evaluation, and governance

## Status

The repository is currently **pre-alpha** and is undergoing an architecture consolidation for `v0.1`.

It is not production-ready. In particular, tenant isolation, authentication security, automated testing, and deployment workflows are being rebuilt before new platform features are expanded.

The current execution baseline is [docs/V0.1_EXECUTION_BLUEPRINT.md](docs/V0.1_EXECUTION_BLUEPRINT.md).

## Architecture

The `v0.1` target is a modular monolith:

- Java 17 and Spring Boot 3.3
- Vue 3, TypeScript, Vite, Pinia, and Element Plus
- MySQL 8.3 and Flyway
- one deployable backend with module-owned tables
- tenant isolation enforced below the API layer
- AI as a first-class platform module with the same identity and permission context as business APIs

Repository layout:

```text
server/   Spring Boot multi-module backend
web/      Vue administration console
sql/      MySQL migration source files
docs/     product, domain, architecture, and delivery documentation
```

## Build

Backend compile and package:

```bash
cd server
mvn clean package -DskipTests
```

Frontend install and build:

```bash
cd web
npm ci
npm run build
```

The current backend requires a MySQL database when running. A repeatable Docker Compose and Testcontainers development environment is part of the M0 work in progress.

## Documentation

- [Documentation index](docs/README.md)
- [v0.1 execution blueprint](docs/V0.1_EXECUTION_BLUEPRINT.md)
- [Backend architecture history](docs/backend-enterprise-architecture.md)
- [Frontend architecture history](docs/frontend-vue-architecture.md)
- [Database module boundary history](docs/database-module-boundaries.md)

## Contributing

The public extension API and contribution process are being stabilized during M0. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

Security issues must not be reported in public issues. Follow [SECURITY.md](SECURITY.md).

## License

An open-source license has not been selected yet. Apache-2.0, AGPL-3.0, and a dual-license model are under evaluation. Until a license is published, the repository is source-available for review but no redistribution rights are granted.
