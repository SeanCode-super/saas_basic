## Summary

<!-- Describe what this PR changes and which module (`server/`, `web/`, `mobile/`, `docs/`) is affected. -->

## Checklist

- [ ] Backend modules pass `mvn verify` (including ArchUnit & tenant isolation tests)
- [ ] Frontend passes `npm run check` (`build`, `eslint`, `stylelint`, `vitest`)
- [ ] Mobile passes `npm test && npm run build:h5`
- [ ] New schema changes add an incremental Flyway script (never modify historical `V1..V74` migrations)
- [ ] Documentation / `ROADMAP.md` updated if public behavior changed
