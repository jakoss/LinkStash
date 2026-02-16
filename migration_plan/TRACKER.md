# Migration Tracker (Convex → Ktor + Raindrop)

Last updated: 2026-02-15

## Phases
- [x] Phase 0 — Product + repo alignment (`PHASE_0_PRODUCT_REPO_ALIGNMENT.md`)
- [x] Phase 1 — Contracts foundation (`PHASE_1_CONTRACTS.md`)
- [x] Phase 2 — Ktor API skeleton + DB (`PHASE_2_SERVER_SKELETON_DB.md`)
- [x] Phase 3 — Auth (Raindrop OAuth + LinkStash sessions) (`PHASE_3_AUTH_SESSIONS.md`)
- [x] Phase 4 — Raindrop LinkStash bootstrap (`PHASE_4_RAINDROP_BOOTSTRAP.md`)
- [ ] Phase 5 — Domain endpoints (spaces + links) (`PHASE_5_DOMAIN_API.md`)
- [ ] Phase 6 — Android integration (`PHASE_6_ANDROID_INTEGRATION.md`)
- [ ] Phase 7 — Web (Compose for Web) (`PHASE_7_WEB_COMPOSE.md`)
- [ ] Phase 8 — Decommission Convex/Bun web (`PHASE_8_DECOMMISSION_CONVEX.md`)

## Working Notes
- Current focus: Phase 5 — Domain endpoints (spaces + links) (`PHASE_5_DOMAIN_API.md`).
- Next up: Phase 5 — Domain endpoints (spaces + links) (`PHASE_5_DOMAIN_API.md`).
- Validation: Phase 3 auth E2E passed on 2026-02-15 (Raindrop OAuth start/login/consent/exchange + `/v1/me` + `/v1/auth/logout` session revocation check).
- Validation: Phase 4 implementation compiled on 2026-02-15 (`./gradlew :server:compileKotlin`) with new per-user `linkstash_config` ids bootstrap/repair flow and authenticated `GET /v1/spaces`.
- Blockers: No active blockers for Phase 4; Phase 5 endpoint expansion is pending.
