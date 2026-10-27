# Future Features

Ideas and deferred features to revisit. These are not commitments for the current milestone.

## Authentication status

Duplicate-email registration/login restrictions are implemented, and authentication tests were reported successful. This is not account linking: users must use their original sign-in method. User-initiated linking remains deferred; it was not completed by the duplicate-email policy.

## Event-driven architecture learning path

**Status:** The statistics outbox/RabbitMQ path is implemented. See [the current statistics architecture](eda-architecture-statistics-component.md). The learning sequence below records the original approach; monitoring, replay tooling, publisher batching, and production hardening remain follow-up work.

### Goal and first use case

Keep REST for user-facing commands and queries; use events for work that should happen reliably after a change. EDA complements REST rather than replacing every endpoint or requiring microservices.

Recommended learning slice: record a play through REST, then publish `PlayRecorded` and update a reporting projection asynchronously. This connects directly to planned charts without putting payments or access decisions at risk during the learning phase.

Other candidates: `GameAdded` for background enrichment, `GameDeleted`/`PlayUpdated`/`PlayDeleted` to keep reports accurate, and verified billing events for plan changes later. Do not automatically add games based on uncertain recognition or grant paid access from unverified events.

### Incremental implementation

1. Define event contracts: event ID, type/version, occurrence time, owner ID, aggregate ID/version, and the minimal payload needed. Never include access tokens or unnecessary personal data.
2. Learn Spring in-process application events and after-commit handling for a disposable projection. These alone are not durable delivery: a process failure can lose work.
3. Add a PostgreSQL transactional outbox: save the play and its event in the same database transaction. Rollback must remove both.
4. Add a publisher worker that claims pending outbox rows, sends persistent messages to a durable RabbitMQ queue, waits for publisher confirmation, and marks successful publication. An unavailable broker must not lose a committed event.
5. Add a consumer with manual acknowledgment after its database work commits. Deduplicate by event ID in the same transaction as the projection update. Delivery is at least once, not exactly once.
6. Add bounded retries, dead-letter handling, alerting, and a documented replay/rebuild workflow. Handle publisher crashes after send but before marking the outbox row.
7. Extend events to edits/deletes; protect against duplicate and out-of-order delivery using aggregate versions or recomputation from authoritative data.
8. Prove owner isolation and eventual consistency in the UI. Keep original play records authoritative; projection lag must not change quota/billing authorization.

### Verification and operational cost

- Tests: transaction rollback, broker downtime/recovery, duplicate delivery, stale/out-of-order events, consumer failure before acknowledgment, replay, and cross-user isolation.
- Metrics: outbox backlog/age, retry count, dead-letter count, consumer lag, and projection freshness.
- Start with local RabbitMQ and the existing Spring app; a separate service is optional. Account for broker hosting, durable storage, credentials/TLS, patching, and monitoring before production.
- A single RabbitMQ node is a failure point. Durable queues/persistent messages are not equivalent to a replicated broker or an independent database backup.
- Relative effort: small in-process demonstration, medium-to-large reliable outbox/RabbitMQ slice. Do not add production infrastructure until the value and operating budget are clear.

## Paid tiers and catalog-assisted entry

See [Paid-Tier Business Model](PAID-TIER-BUSINESS-MODEL.md) for proposed collection quotas, purchases, charts, and BGG/camera research.

## Independent database backups

**Status:** Recommended follow-up; not configured by this document.

- Distinguish availability/replication from backup recovery. A replica mirrors accidental deletes; a same-project branch is not independent protection against project/account deletion.
- Enable/verify the available Neon recovery history in the console. Published Free pricing checked October 17, 2026 lists a 6-hour history window with a 1 GB limit and one manual snapshot; verify actual account entitlements. The general backup overview describes different minimum history, so the console and current plan terms need confirmation.
- Export scheduled full custom-format `pg_dump` backups using a direct connection, encrypted storage outside the Neon project, restricted access, retention, and failure alerts. Use a compatible PostgreSQL tool version; the current database is PostgreSQL 18.
- Initial recovery targets to discuss: at most 24 hours of lost data (daily backups) and recovery within a few hours. These are proposals, not service guarantees.
- Keep multiple dated backups and a separate/offline or independently protected copy; sync alone can propagate deletion. Existing hardware may make a local backup no-additional-cost, but cloud storage/automation is not guaranteed free.
- Perform an actual restore drill into an isolated database, verifying schema, counts, game images, plays, and ownership. Never test by overwriting production.
- Database dumps do not back up Auth0 credentials, Action secrets, AWS configuration, or future external image storage. Document their separate recovery procedures.
- References: [Neon pricing](https://neon.com/pricing), [backup strategies](https://neon.com/docs/manage/backups).
