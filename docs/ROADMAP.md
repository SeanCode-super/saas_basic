# SaaS Basics Roadmap

This roadmap describes the platform capabilities and release gates for an industry-neutral, multi-tenant SaaS development foundation. Dates are intentionally omitted; a milestone is complete only when its code, tests, documentation, and upgrade path are maintained together.

## M0: Open-source engineering baseline

Status: complete.

- Apache-2.0 license, NOTICE, contribution terms, and package metadata.
- Reproducible backend and frontend quality gates in GitHub Actions.
- Java 27, Spring Boot 4.1, Vue 3, TypeScript, MySQL 8.3, Flyway, and local development instructions.
- Tenant context propagation and fail-closed persistence isolation.
- Modular-monolith boundaries, architecture tests, migration conventions, and security reporting process.

Release gate: the default branch must build and test successfully, and a tagged pre-release must identify the exact source revision.

## M1: Standard organization and identity core

Status: core & legacy cutover delivered (`V25`–`V30`, `V69`, `V74`–`V75`).

Delivered foundations include the `saas-basics-module-organization` bounded context (`V25`–`V30` RFC 9562 UUIDv7, 10 standard org/identity tables, User-Person bindings, assignment-scoped contexts, PC/Mobile profile assignment view), persistent outbox (`V69` `plat_outbox_event`), signed outbound **CloudEvents 1.0** webhook subscriptions with delivery audit & DLQ replay (`V75`), repeatable legacy `iam_department`/`iam_position`/`iam_employee` migration & `UserPersonBinding` backfill engine (`OrganizationLegacyMigrationService`, `/api/iam/org-migration` with `DRY_RUN` / `APPLY` / `VERIFY` and configurable fallback disablement), organization-scoped `DataScope` (`ORGANIZATION`, `ORG_UNIT`, `ORG_UNIT_AND_SUB`, `ORG_SELF`), and contract suites (`PlatformStandardsContractTest`). See [`M1_ORGANIZATION_IMPLEMENTATION_STATUS.md`](./M1_ORGANIZATION_IMPLEMENTATION_STATUS.md).

## M2: IAM and platform security

Status: delivered (`V65`–`V70`, `V74`–`V75`, `V91`).

Delivered capabilities:

- account lifecycle, sessions, self-service active session revocation (`/api/iam/profile/sessions`), credential upgrade, and TOTP MFA (`V67`);
- **WebAuthn / FIDO2 Passkey** (`V91` `iam_webauthn_credential`, `/api/auth/passkey/*`, `/api/iam/profile/passkeys/*`) with ES256/EdDSA/RS256 signature, origin/RP-hash, and monotonic `signCount` anti-replay verification;
- Vault AES-256-GCM secret management and dynamic X25519 + HKDF-SHA256 + AES-256-GCM transport encryption with replay protection & keyring (`V65`–`V66`);
- **OIDC PKCE (`RFC 7636`) + **SCIM 2.0 (`RFC 7643`/`7644`)** user directory provisioning (`/scim/v2/*`), self-service OTP password reset (`/api/auth/password-reset/*`), and tenant member invite onboarding (`/api/auth/invites/*`, `/api/iam/invites`, `V75`);**
- content-negotiated **RFC 9457 Problem Details** (`application/problem+json`), HMAC Open API replay guard & tenant-scoped CRM/ERP endpoints, tenant-isolated low-code DDL tables (`biz_dyn_t<tenantId>_*`), SSRF outbound URI validation across storage/SSO/MCP/webhooks, menu/API registries, API endpoint telemetry & alert rules (`V68`), audit before/after diff snapshots (`V70`), rate limits, and tenant/platform administration.

## M3: AI-native platform services

Status: delivered (`V63`–`V64`, `V71`–`V73`, `V75`, `V92`).

Delivered capabilities:

- AI Gateway with OpenTelemetry GenAI semantic spans (`gen_ai.system`, `gen_ai.request.model`, token metrics), multi-provider & model config registries (`V63`), credential refs, prompt versions, **bidirectional MCP Streamable HTTP Client + Server (`2025-03-26`, `/api/ai/mcp/server`)** with tool governance, runs, and tenant quotas (`V64`);
- ⌘K natural-language command router & tenant-scoped **NL2SQL Data Copilot** with schema catalog and history (`V71`);
- enterprise knowledge bases with Hybrid/chunk RAG ingestion, document review (`KnowledgeBaseView`, `V72`), automated offline **RAG evaluation suite** (`V92` `ai_rag_eval_run`, `Recall@K`, `MRR`, `NDCG@K`, `HitRate`), and **token/cost billing reconciliation** (`V92` `ai_billing_reconcile`);
- visual Agent Studio (`AgentStudioView`) and fine-tuning dataset curation / LoRA job tracking (`FinetuneStudioView`, `V72`–`V73`) with configurable external HTTP trainer gateway (SSRF-checked and fail-closed when unconfigured). External physical GPU cluster execution depends on the target deployment environment.

## M4: Developer platform and extensions

The first build-time extension path ships a Spring Boot application SPI, a standalone Maven scaffold, PC menu and API registration, and tenant-specific transactional revisions. See the [course example](DEVELOPING_INDUSTRY_APPS.md). Independently deployed services can also publish a versioned manifest for PC and mobile, with per-tenant subscriptions, action permissions, **extension dependency DAG resolution with cycle detection**, **host SemVer compatibility checks**, and **active-dependent disable guards** (`ExtensionCompatibilityResolver`).

The developer platform roadmap covers:

- module SDK and extension package contracts;
- metadata-driven scaffolding, AI App Builder (with tenant-prefixed isolated dynamic tables), and code generation;
- migration, configuration, feature-flag, and capability registration APIs;
- stable UI shell contracts (Spatial 3-Zone Topbar + `QuickLaunchpadPopover` hub, Dark/Light/System theme store, XSS-safe Markdown AI streaming drawer, `GlobalErrorBoundary`, live `IntegrationCallbackSection`, and cloud-synced workspace preferences in `V74`), reusable administration workflows, and examples;
- compatibility, extension installation, upgrade, and rollback tooling.

## M5: Operations and ecosystem hardening

Status: delivered (`V62`, `V68`–`V75`); multi-region physical cluster chaos & device-farm validation run on target infrastructure.

Delivered capabilities:

- configurable multi-storage file engine (`LOCAL`, `S3`/`MinIO`/`OSS`/`COS`/`OBS` config) with real **AWS Signature Version 4 (`AWS4-HMAC-SHA256`)** direct upload & presigned GET/PUT URLs, fail-closed upload error propagation, per-business upload mode & directory routing (`file_upload_route`, `V74`), and missing-storage-source fail-fast guard;
- persistent **SMS/Email/Bot message gateway ledger** (`int_message_delivery_log`, `V75`) with TLS/SSL SMTP mail transport, Aliyun/Tencent SMS HMAC signing, and local deterministic SVG payment QR codes (`PayCenterService`);
- global sync/async data export queue, saved templates, scheduled report delivery, Download Center (`V71`–`V72`), and document Print Center (`PrintCenterView`);
- tenant disaster-recovery snapshot/restore (`V69` `plat_tenant_snapshot_record`), DevOps service orchestration (`V62`), API stats & alert rules (`V68`), hardened Docker production compose defaults, and external observability stack configs (`deploy/observability/` for Prometheus, Alertmanager rules, Grafana dashboard, and Tempo);
- deep-tree organization subtree moves, concurrent assignment contention guards, and tenant snapshot backup-restore integration tests (`EnterpriseScaleAndResilienceIntegrationTest`).

## Release policy

Each release must state its maturity, supported runtime versions, migration requirements, known limitations, and verification evidence. Pre-releases may expose evolving APIs, but they must never be described as production-ready. A milestone is not considered delivered solely because a module compiles; its cross-module contracts, security behavior, tests, documentation, and upgrade story must be present.
