# SaaS Basics Roadmap

This roadmap describes the platform capabilities and release gates for an industry-neutral, multi-tenant SaaS development foundation. Dates are intentionally omitted; a milestone is complete only when its code, tests, documentation, and upgrade path are maintained together.

## M0: Open-source engineering baseline

Status: complete.

- Apache-2.0 license, NOTICE, contribution terms, and package metadata.
- Reproducible backend and frontend quality gates in GitHub Actions.
- Java 17, Spring Boot, Vue 3, TypeScript, MySQL 8.3, Flyway, and local development instructions.
- Tenant context propagation and fail-closed persistence isolation.
- Modular-monolith boundaries, architecture tests, migration conventions, and security reporting process.

Release gate: the default branch must build and test successfully, and a tagged pre-release must identify the exact source revision.

## M1: Standard organization and identity core

Status: in progress. The first V25 organization resources are implemented, but this milestone is not complete.

Remaining work includes identity and security migration, User/Person binding, organization-scoped roles and data permissions, permission and endpoint registry separation, public RFC 9457 and OpenAPI 3.1 contracts, idempotency and outbox events, observability, frontend workflows, extension installation contracts, and load, upgrade, and rollback verification.

The core model remains industry-neutral: tenants, organization subjects, typed relations, organization units, people, engagements, positions, assignments, accounts, roles, permissions, and audit records. Legal registration, taxation, healthcare, education, and other vertical concepts belong in optional extension packages.

## M2: IAM and platform security

Deliver a complete identity and access foundation shared by every module:

- account lifecycle, sessions, credential upgrade, MFA, and recovery;
- organization-scoped role bindings and data-permission evaluation;
- menu, API, endpoint, and capability registries with a stable public contract;
- audit, security events, rate limits, and tenant/platform administration;
- OpenAPI 3.1, RFC 9457 errors, idempotency, and compatibility tests.

## M3: AI-native platform services

Provide provider-neutral AI capabilities under the same tenant, identity, authorization, and audit context as other platform APIs:

- AI Gateway and Provider SPI with model and capability registries;
- prompt templates, conversations, runs, tools, MCP servers, and policy checks;
- knowledge sources, retrieval, evaluation, cost and usage accounting;
- deterministic execution records, human approval points, and data-boundary controls.

No model vendor, hosted service, or vertical workflow is a required dependency of the core.

## M4: Developer platform and extensions

Make the foundation straightforward to extend and maintain:

- module SDK and extension package contracts;
- metadata-driven scaffolding and code generation;
- migration, configuration, feature-flag, and capability registration APIs;
- stable UI shell contracts, reusable administration workflows, and examples;
- compatibility, extension installation, upgrade, and rollback tooling.

## M5: Operations and ecosystem hardening

Harden the platform for sustained open-source use:

- files, notifications, scheduling, integrations, and webhook primitives;
- metrics, traces, structured logs, health checks, and operational runbooks;
- performance, tenant-isolation, security, dependency, and upgrade testing;
- versioning policy, changelog automation, release artifacts, and support boundaries;
- contributor documentation, reference extensions, and community feedback loops.

## Release policy

Each release must state its maturity, supported runtime versions, migration requirements, known limitations, and verification evidence. Pre-releases may expose evolving APIs, but they must never be described as production-ready. A milestone is not considered delivered solely because a module compiles; its cross-module contracts, security behavior, tests, documentation, and upgrade story must be present.
