# M1 Identity and Organization Binding

Status: `Implemented foundation; migration cutover pending`

This document defines the standard boundary between IAM accounts and the organization model. It is part of M1 and does not declare the legacy IAM migration or organization-scoped authorization complete.

## Model

- `User` is an account and security principal.
- `Person` is a tenant-scoped natural-person subject.
- `Engagement` relates a Person to an Organization.
- `Assignment` places an Engagement in an OrgUnit and Position for an effective period.
- A User may have zero or one ACTIVE Person binding. Unbound platform and service accounts remain valid.
- A Person may be associated with multiple Users for separate account purposes.
- Assignment selection belongs to a session. It is not copied into the User-Person binding.

IAM owns `iam_user_person_binding`. It stores the IAM-internal `user_id` and the organization-public `person_public_id`; it never stores `org_person.id` and does not create a cross-module foreign key. Public binding contracts expose UUIDv7 identifiers only.

## Session behavior

`iam_session` records the binding public identifier observed at login and, when explicitly selected, one Assignment public identifier. It does not duplicate Organization, Engagement, OrgUnit, or Position identifiers.

On each authenticated request:

1. The current ACTIVE binding is compared with the binding captured by the session.
2. A changed, inactive, expired, or missing captured binding, or a bound Person that is no longer effective, invalidates the session.
3. A selected Assignment is resolved through `OrganizationDirectory.requireEffectiveAssignmentForPerson`.
4. The resolver proves that the Assignment belongs to the bound Person, that its Engagement, Organization, OrgUnit, and Position ownership references are internally consistent, and that every referenced organization resource is effective.
5. A missing, terminated, or otherwise stale Assignment clears the session selection and yields no organization context; it does not revoke an otherwise valid account session.
6. Infrastructure and storage failures propagate as errors and do not mutate the binding or Assignment selection.
7. No primary Assignment is selected implicitly.

The self-service context API derives the Person from the authenticated session:

- `GET /api/auth/context-options`
- `PUT /api/auth/context`
- `DELETE /api/auth/context`

The selection command accepts only an Assignment UUID. Clients cannot submit a separately assembled Organization, Engagement, OrgUnit, Position, and Assignment tuple.

The administrative binding resource exposes the complete governed lifecycle:

- `GET /api/iam/user-person-bindings` and `GET /api/iam/user-person-bindings/{publicId}` query UUIDv7 resources.
- `POST /api/iam/user-person-bindings` creates a DRAFT binding.
- `PUT /api/iam/user-person-bindings/{publicId}` updates account, Person, effective period, and remark only while the binding is DRAFT and requires `expectedVersion`.
- `PATCH /api/iam/user-person-bindings/{publicId}/lifecycle` performs explicit lifecycle transitions with optimistic locking.
- `DELETE /api/iam/user-person-bindings/{publicId}` performs a logical delete only after ARCHIVED.

The endpoint catalog assigns independent `query`, `write` (create), `update`, `lifecycle`, and `delete` permission codes. This keeps HTTP method metadata exact and prevents lifecycle or deletion authority from being implied by general write access.

An ACTIVE binding period must be fully contained within the referenced ACTIVE Person period. A bounded Person cannot be referenced by an open-ended binding, and a future binding can be activated without requiring the Person to be effective at the time the administrative command is issued.

## Authentication response compatibility

The standard identity fields returned by login, `/api/auth/me`, and context selection are `sessionPublicId`, `userPublicId`, `subjectBindingPublicId`, `personPublicId`, and `organizationContext`. The organization context contains only Organization, Engagement, OrgUnit, Position, and Assignment public UUIDs.

The existing `sessionId`, `tenantId`, `userId`, and `roleIds` bigint fields remain temporarily in the response for pre-M1 frontend and API compatibility. They are not standard cross-module identifiers, must not be persisted by new clients, and must not be introduced into new endpoints. Removing them is tied to the remaining public-ID migration of Tenant, Role, File, Integration, Scheduler, and System APIs and requires a separately versioned compatibility release.

## Migration chain

- V26 adds tenant-scoped migration scope, run, resource-map, and sanitized issue structures.
- V27 adds nullable User and Session public identifiers, session binding/context references, the versioned User-Person binding table, and compatibility triggers for writers that do not yet supply public identifiers.
- V28 uses the Java `UuidV7Generator` to backfill historical User and Session rows with RFC 9562 UUIDv7 identifiers.
- V29 requires every non-null identifier to have the RFC 9562 UUIDv7 text form. The columns deliberately remain nullable during the rolling-upgrade and binary-rollback window.
- V30 installs the binding lifecycle permissions for every existing tenant, assigns them to each standard tenant administrator role, and updates the platform tenant that future tenant bootstrap clones.

Login resolves and validates the effective Person binding before it records a successful login or creates an ONLINE session. A concurrent binding or Person change during final principal construction compensates by taking the new session OFFLINE; no successful login audit is written until principal construction succeeds.

V1-V25 remain unchanged. The legacy `iam_user.employee_id`, department, employee, and position paths remain available only as a migration compatibility surface. The standard binding path never writes `employee_id`.

## Migration control rules

- A source department scope must be mapped explicitly to an existing Organization; the migration must not create a default company or legal entity.
- UUIDv7 target identifiers are generated by application code, not MySQL `UUID()`.
- V27 compatibility triggers generate UUIDv7 only when an older writer omits `public_id`; current writers remain responsible for generating identifiers in application code. The triggers must remain installed until every supported writer supplies UUIDv7 and the binary-rollback window has closed.
- Source and target fingerprints prevent a repeated run from overwriting a target changed by an administrator.
- Unknown states, orphan references, department cycles, ambiguous organization ownership, and missing unit or position mappings are blocking issues.
- Unmigrated legacy attributes are reported without copying personal data into issue details.
- Cutover is per tenant and requires a verified run with zero blocking differences and an unchanged source fingerprint.
- Rollback is forward/compensating. Legacy source rows are not deleted or rewritten by the migration control schema.

## Rolling upgrade and contract gate

V27-V29 are an expand-and-backfill sequence, not the final schema contract. Older binaries ignore the added nullable columns and continue to insert User and Session rows. The compatibility triggers fill only omitted identifiers, so new binaries never observe a normal post-V27 write without a UUIDv7. V28 then closes the historical gap, while V29 rejects malformed non-null identifiers without making old insert statements invalid.

Making `iam_user.public_id` or `iam_session.public_id` non-nullable is a later contract migration. It is permitted only after all deployed and rollback-eligible versions generate UUIDv7, the compatibility-window null-count metric has remained zero, a fresh backfill verification succeeds, and rollback no longer includes a binary that omits these columns. The contract migration must be released separately from the expand migration and must not remove the compatibility triggers in the same release.

## Remaining gates

- Implement and verify the repeatable validate, apply, compare, and compensating-cleanup runner on top of the V26 control structures.
- Backfill User-Person bindings from verified employee mappings without dual writing.
- Replace legacy bigint department/employee/position data scopes with Person, Organization, OrgUnit, Engagement, Assignment, Position, Role, and Tenant public identifiers.
- Complete upgrade, write-freeze, rollback, capacity, and concurrent reassignment tests before tenant cutover.
