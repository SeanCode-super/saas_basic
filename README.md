# SaaS Basics

SaaS Basics is an open-source-oriented, AI-native foundation for building enterprise applications across industries.

The project is not a vertical SaaS product or an admin dashboard template. It aims to provide the reusable platform capabilities that CRM, ERP, WMS, manufacturing, healthcare, education, government, and other enterprise applications repeatedly need:

- tenant and platform operations
- domain-neutral organizations, relations, units, people, engagements, and assignments
- identity, sessions, roles, menus, API permissions, and data permissions
- applications, portals, configuration, feature flags, and audit
- module scaffolding, metadata, migrations, and code generation
- AI gateway, prompts, knowledge, agents, tools, MCP, evaluation, and governance

## Status

The repository is currently **pre-alpha** and is undergoing an architecture consolidation for `v0.1`.

It is not production-ready. Tenant isolation and the initial automated quality gate are in place. The standard organization core is under implementation; identity security, legacy migration, organization-scoped authorization, interoperability, and deployment workflows remain release blockers.

The current execution baseline is [docs/V0.1_EXECUTION_BLUEPRINT.md](docs/V0.1_EXECUTION_BLUEPRINT.md).
All new capabilities must follow [docs/STANDARDIZATION_POLICY.md](docs/STANDARDIZATION_POLICY.md).
The M1 identity/organization boundary is documented in [docs/M1_IDENTITY_ORGANIZATION_BINDING.md](docs/M1_IDENTITY_ORGANIZATION_BINDING.md).

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

## Local development

Prerequisites:

- Java 17
- Maven 3.9+
- Node.js 20+
- Docker with Compose support

Start MySQL:

```bash
make dev-db-up
```

The development database listens on port `3307` by default so it does not collide with a local MySQL installation.

Start the backend and frontend in separate terminals:

```bash
make dev-backend
make dev-frontend
```

Backend: `http://127.0.0.1:8080`

Frontend: `http://127.0.0.1:9527`

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

Run the current quality gate:

```bash
make verify
```

Flyway migration tests use Testcontainers when Docker is available and skip with an explicit reason when Docker is not installed.

## Documentation

- [Documentation index](docs/README.md)
- [Roadmap](docs/ROADMAP.md)
- [v0.1 execution blueprint](docs/V0.1_EXECUTION_BLUEPRINT.md)
- [Backend architecture history](docs/backend-enterprise-architecture.md)
- [Frontend architecture history](docs/frontend-vue-architecture.md)
- [Database module boundary history](docs/database-module-boundaries.md)

## Contributing

The public extension API and contribution process are being stabilized during M0. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

Security issues must not be reported in public issues. Follow [SECURITY.md](SECURITY.md).

## License

SaaS Basics is licensed under the [Apache License 2.0](LICENSE). Attribution information is provided in [NOTICE](NOTICE).
