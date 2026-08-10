# ADR-013: Open-source license

Status: `Accepted`

Date: `2026-08-10`

## Context

SaaS Basics is intended to be a reusable, vendor-neutral development foundation that can be adopted and extended by commercial organizations, public institutions, non-profit organizations, and independent developers. A public repository without an explicit license does not grant the permissions required for open-source use, modification, or redistribution.

The project also needs an explicit patent grant because it is a platform and integration foundation intended for broad downstream use.

## Decision

The repository is licensed under the Apache License, Version 2.0, unless a subdirectory or bundled third-party component contains its own license notice.

Contributions submitted for inclusion in the repository are accepted under Apache-2.0 as described by section 5 of the license, unless a separate written agreement explicitly applies.

## Consequences

- Commercial use, modification, distribution, and private derivative works are permitted under Apache-2.0.
- Distributions must preserve the license and applicable attribution notices.
- Modified files distributed to others must carry prominent change notices.
- The license includes an explicit patent grant and patent-termination provision.
- The license provides no trademark rights and no warranty.
- Third-party dependencies retain their own licenses; Apache-2.0 does not replace them.
- Any future licensing exception, dual-license program, or license change requires a separate architecture decision and legal review.
