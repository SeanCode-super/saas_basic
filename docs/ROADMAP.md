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

Status: in progress (`V25`–`V30`, `V69`, `V74` delivered; legacy cutover remains open).

Delivered foundations include the `saas-basics-module-organization` bounded context (`V25`–`V30` RFC 9562 UUIDv7, 10 standard org/identity tables, User-Person bindings, assignment-scoped contexts, PC/Mobile profile assignment view) and initial persistent outbox (`V69` `plat_outbox_event`).

Remaining release blockers (tracked in [`M1_ORGANIZATION_IMPLEMENTATION_STATUS.md`](./M1_ORGANIZATION_IMPLEMENTATION_STATUS.md)) include legacy `iam_department` / `iam_position` / `iam_employee` backfill & switchover, organization-scoped RoleBinding/DataScope refactor, public RFC 9457 / OpenAPI 3.1 contract tests, idempotency & CloudEvents 1.0 outbox consumers, OpenTelemetry full tracing, extension installation contracts, and load/upgrade/rollback verification.

## M2: IAM and platform security

Status: substantial core implemented (`V65`–`V70`, `V74`); contract & recovery hardening ongoing.

Delivered capabilities:

- account lifecycle, sessions, self-service active session revocation (`/api/iam/profile/sessions`), credential upgrade, and TOTP MFA (`V67`);
- Vault AES-256-GCM secret management and dynamic X25519 + HKDF-SHA256 + AES-256-GCM transport encryption with replay protection & keyring (`V65`–`V66`);
- menu, API, and capability registries, API endpoint telemetry & alert rules (`V68`), audit before/after diff snapshots (`V70`), rate limits, and tenant/platform administration.

Remaining work includes SMS/email OTP self-service login/recovery, OpenAPI 3.1 + RFC 9457 Problem Details unification, and compatibility contract suites.

## M3: AI-native platform services

Status: core capabilities implemented (`V63`–`V64`, `V71`–`V73`); model evaluation & async job workers evolving.

Delivered capabilities:

- AI Gateway, multi-provider & model config registries (`V63`), credential refs, prompt versions, MCP servers/tools, runs, and tenant quotas (`V64`);
- ⌘K natural-language command router & NL2SQL Data Copilot with schema catalog and history (`V71`);
- enterprise knowledge bases with Hybrid/chunk RAG ingestion and document review (`KnowledgeBaseView`, `V72`), visual Agent Studio (`AgentStudioView`), and fine-tuning dataset curation / LoRA job tracking (`FinetuneStudioView`, `V72`–`V73`).

Remaining work includes live GPU/external fine-tune trainer runners, automated offline RAG evaluation suites, and token/cost billing reconciliation.

## M4: Developer platform and extensions

Make the foundation straightforward to extend and maintain:

- module SDK and extension package contracts;
- metadata-driven scaffolding, AI App Builder, and code generation;
- migration, configuration, feature-flag, and capability registration APIs;
- stable UI shell contracts (including cloud-synced workspace preferences in `V74`), reusable administration workflows, and examples;
- compatibility, extension installation, upgrade, and rollback tooling.

## M5: Operations and ecosystem hardening

Status: major infrastructure delivered (`V62`, `V68`–`V74`); cloud-native adapter & chaos hardening ongoing.

Delivered capabilities:

- configurable multi-storage file engine (`LOCAL`, `S3`/`MinIO`/`OSS`/`COS`/`OBS` config), per-business upload mode & directory routing (`file_upload_route`, `V74`), and missing-storage-source fail-fast guard;
- global sync/async data export queue, saved templates, scheduled report delivery, Download Center (`V71`–`V72`), and document Print Center (`PrintCenterView`);
- tenant disaster-recovery snapshot/restore (`V69` `plat_tenant_snapshot_record`), DevOps service orchestration (`V62`), API stats & alert rules (`V68`).

Remaining hardening work:

- real AWS SigV4 / cloud SDK HTTP signing for non-local object storage direct PUT/GET;
- persistent SMS/Email/Push transport adapters (replacing in-memory `MessageGatewayService` deque);
- full Prometheus/OTel dashboards, load/chaos/tenant-isolation/upgrade tests, versioning policy, and release automation.

## Release policy

Each release must state its maturity, supported runtime versions, migration requirements, known limitations, and verification evidence. Pre-releases may expose evolving APIs, but they must never be described as production-ready. A milestone is not considered delivered solely because a module compiles; its cross-module contracts, security behavior, tests, documentation, and upgrade story must be present.
