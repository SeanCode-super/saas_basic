# Contributing

SaaS Basics is currently in a pre-alpha architecture consolidation. Contributions should align with the accepted `v0.1` execution blueprint.

## Before opening a change

1. Read [docs/V0.1_EXECUTION_BLUEPRINT.md](docs/V0.1_EXECUTION_BLUEPRINT.md).
2. Keep changes inside one module or one explicitly documented cross-module use case.
3. Do not import another module's entity, mapper, repository implementation, or internal service.
4. Treat tenant isolation, authorization, migrations, tests, and documentation as part of the feature.
5. Open an issue before introducing a new runtime dependency, external service, or public extension API.

## Local checks

Backend:

```bash
cd server
mvn clean verify
```

Frontend:

```bash
cd web
npm ci
npm run check
```

All current checks must pass before a pull request is ready for review. Do not hide a failing check; document any temporary exception and its tracking issue.

## Commit and pull request scope

- Keep commits focused and explain behavioral impact.
- Include database migrations for schema changes; never edit a published migration.
- Include negative tests for tenant and permission boundaries.
- Update public API and migration documentation with the implementation.
- Do not commit credentials, local environment files, build output, IDE state, or generated dependency directories.

## AI changes

AI features must preserve tenant, identity, application, and permission context. Tool calls, prompt versions, knowledge sources, usage, cost, and approval decisions must be auditable.
