# Security Policy

## Supported versions

SaaS Basics is pre-alpha and does not yet have a supported production release. Security fixes are applied to the default branch while `v0.1` is being developed.

## Reporting a vulnerability

Do not disclose vulnerabilities in public GitHub issues, discussions, pull requests, or chat logs.

Use GitHub private vulnerability reporting for this repository when it is enabled. Until a dedicated security contact is published, contact the repository owner privately through GitHub.

Include:

- affected component and version or commit
- reproduction steps or proof of concept
- expected impact, especially tenant-boundary impact
- any known workaround

Do not access data that does not belong to you, degrade shared services, or publish exploit details before a fix is available.

## Security priorities before v0.1

- mandatory tenant isolation
- Spring Security authentication and authorization
- migration from unsalted SHA-256 password hashes
- credential encryption and secret handling
- AI knowledge and tool permission enforcement
- automated dependency, migration, and security tests
