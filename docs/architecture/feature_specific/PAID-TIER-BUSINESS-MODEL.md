# Paid-Tier Business Model - Build Plan

**Status:** Proposal, not an implemented entitlement or pricing policy.
**Research date:** October 17, 2026. Confirm provider terms and costs before implementation.

## Goal

Offer a useful free personal collection tracker, a paid Pro tier for enthusiasts, and a Business tier for larger libraries. Keep the current REST API and enforce paid features on the backend, not just by hiding React controls.

## Initial tier ideas

| Capability | Free | Pro | Business |
| --- | --- | --- | --- |
| Stored board games | 5-10; exact limit undecided | 50 proposed | 1,000 proposed |
| Manual game entry and core play tracking | Yes | Yes | Yes |
| Basic visual summaries | Yes; chart set undecided | Yes | Yes |
| Additional charts and filters | Limited | Expanded | Expanded; business reporting to be defined |
| Catalog-assisted game entry | Not initially | Candidate | Candidate |
| Camera-assisted identification | Not initially | Research candidate with usage quota | Research candidate with usage quota |

These limits are examples, not commitments. Prices, currency, subscription versus one-time payment, tax handling, and scanning quotas remain undecided. Recurring infrastructure/API costs favor evaluating subscriptions before promising lifetime access.

## Phase 1 - Product rules and cost model

- Decide whether quotas apply per person, collection, or business organization.
- Define what counts as a game: editions, duplicate physical copies, archived entries, imports, and expansions.
- Choose a free limit of 5 or 10 after testing whether onboarding is useful.
- Decide grandfathering rules for current users and accounts already above a new limit.
- On downgrade, preserve existing records and access to core reads/exports. Proposed behavior: block additional games until below the limit, never silently delete customer data.
- Estimate database/image storage, backups, Auth0, payments, external catalog licensing, recognition, and support costs per active account.
- Define Business meaning: a larger personal quota alone is not multi-user business support. Shared libraries, staff roles, inventory copies, lending, and audit trails are separate scope decisions.

**Exit:** approved entitlement matrix, quota semantics, downgrade behavior, and sustainable pricing assumptions.

## Phase 2 - Backend entitlements and usage

- Persist the app account's plan and billing reference; keep provider-specific payment details outside game records.
- Centralize permission/limit checks for all game-creation routes, future imports, and scan-assisted entry.
- Enforce counts atomically in a transaction, serializing creation against the account/organization owner so concurrent requests cannot exceed the quota.
- Return a consistent machine-readable quota error and readable explanation; React shows usage and upgrade guidance.
- Do not trust a client-supplied plan, price, user ID, or editable profile metadata as proof of purchase.
- Test owner isolation, limit boundaries, concurrent creation, downgrade/upgrade, and bypass attempts through direct API calls.

**Relative effort:** Medium. Organization-based ownership would expand this considerably.

## Phase 3 - Purchase and subscription lifecycle

- Select a payment provider and confirm business/tax requirements before integrating.
- Use provider-hosted checkout and customer portal rather than handling card data.
- Verify webhook signatures; process duplicate and out-of-order notifications safely; reconcile authoritative subscription state.
- Handle cancellation, failed payments, grace periods, refunds, expiration, and reactivation explicitly.
- Activate paid access from verified backend billing state, not the checkout success URL.
- Add operational logs and a documented administrative recovery path without exposing payment secrets.

**Relative effort:** Medium to large. Test with provider sandbox accounts before any live payment.

## Phase 4 - Catalog-assisted entry and camera research

### BoardGameGeek feasibility

BGG provides an XML API, including game-name search and game-detail retrieval. This is not an image-recognition service. Treat access registration/authorization and commercial licensing as prerequisites for a paid integration.

Official starting points:

- [BGG XML API2](https://boardgamegeek.com/wiki/page/BGG_XML_API2)
- [Using the XML API](https://boardgamegeek.com/using_the_xml_api)
- [Commercial use](https://boardgamegeek.com/wiki/page/BGG_XML_API_Commercial_Use)
- [Registration and authorization announcement](https://boardgamegeek.com/thread/3492262/registration-and-authorization-coming-to-the-xml-a)

Automated retrieval of the BGG documentation returned HTTP 403 during research. Search results identify registration/authorization and commercial-use requirements, but exact current terms, fees, attribution, image rights, cache permissions, and quotas must be checked directly with BGG. Do not sell this feature or assume free commercial access before approval. Third-party wrappers do not grant data rights or bypass authorization.

### Recommended progression

1. **Name search first:** user enters a title, picks a catalog result, previews mapped fields, and confirms the new game. Keep edition, language, and expansion ambiguities visible.
2. **OCR-assisted camera entry:** extract title text from a photo, then use the same search-and-confirm flow. OCR recognizes text, not reliably the exact game or edition.
3. **Optional UPC/EAN scanning:** decode packaging barcodes and evaluate a licensed product-to-game lookup dataset. A barcode reader alone cannot map a UPC to a BGG game; do not assume BGG provides that mapping.
4. **Cover recognition experiment:** evaluate an image model or licensed visual index. Measure correct top suggestions, wrong matches, latency, and per-scan cost on varied boxes, editions, glare, and lighting.

Existing app-generated QR labels identify games already in the user's collection. They are different from recognizing a new retail box.

### Integration safeguards

- Keep BGG credentials on the backend. Use timeouts, bounded retries/backoff, rate limits, and caching only where permitted.
- Parse external XML safely with external entities disabled; validate and sanitize imported fields and links.
- Map only supported fields, store the catalog source/ID, and avoid overwriting user edits.
- Require user confirmation and normal quota checks before persistence. Low confidence or missing catalog results should return to manual entry.
- Ask consent before sending photos to external recognition services; define retention/deletion and avoid uploading unrelated people or private background content.
- Apply separate scan/API usage quotas; game count alone cannot bound recognition costs.

**Relative effort:** Name lookup medium; OCR/barcode lookup medium to large; reliable visual recognition high and uncertain. Licensing can block the entire catalog feature.

## Phase 5 - Visual reporting

- Start with basic play counts and plays over time for all users; choose the actual free set before coding.
- Candidate paid views: date-filtered trends, most/least played, unplayed games, player-count distributions, and comparative reports.
- Business-specific reports need meaningful business data; lending/utilization reports require lending/inventory features first.
- Choose a chart library after checking React support, accessibility, licensing, and mobile performance.
- Define metrics from authoritative play records and test timezone/date boundaries, zero-data states, deleted records, and account isolation.
- Provide accessible text/table alternatives. Only introduce asynchronous projections when measured performance justifies them.

**Relative effort:** Small to medium for basic charts, medium to large for richer reporting.

## Suggested delivery order

1. Approve tier rules and investigate BGG commercial access.
2. Implement/test quotas with manually assigned plans, without accepting money.
3. Add checkout, verified billing updates, downgrade behavior, and support procedures.
4. Deliver basic versus paid chart sets.
5. Add catalog name lookup if licensing permits.
6. Prototype camera identification separately and assess accuracy/cost before offering it.

No billing provider, paid access, BGG integration, or recognition service is configured by this document.
