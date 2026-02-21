# Migration Tracker (Convex → Ktor + Raindrop)

Last updated: 2026-02-21

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
- Current focus: Phase 6 — manual emulator end-to-end validation for Android auth/share/link flows (`PHASE_6_ANDROID_INTEGRATION.md`).
- Next up: close Phase 6 with manual validation evidence, then Phase 7 — Web (Compose for Web) (`PHASE_7_WEB_COMPOSE.md`).
- Validation: Phase 3 auth E2E passed on 2026-02-15 (Raindrop OAuth start/login/consent/exchange + `/v1/me` + `/v1/auth/logout` session revocation check).
- Validation: Phase 4 implementation compiled on 2026-02-15 (`./gradlew :server:compileKotlin`) with per-user `linkstash_config` bootstrap/repair and authenticated `GET /v1/spaces`.
- Validation: Phase 5 endpoint implementation compiled on 2026-02-21 (`./gradlew :contracts:compileKotlinMetadata :server:compileKotlin`) with all planned spaces/links routes and LinkStash subtree guardrails.
- Validation: Phase 6 Android implementation compiled and installed on emulator on 2026-02-21 (`./gradlew :androidApp:compileDebugKotlin :server:compileKotlin`, `./gradlew :androidApp:installDebug`) with OAuth deep-link handling, connectivity retry, and spaces/links move-delete UI wired to the new domain API.
- Validation: Phase 6 storage refactor completed on 2026-02-21 with Room offline queue + DataStore bearer token storage and shared-module extraction of LinkStash repository/state (`./gradlew :androidApp:assembleDebug`).
- Validation: Phase 6 orchestration refactor completed on 2026-02-21 with shared `LinkStashController` state machine; Android ViewModel now delegates to shared controller (`./gradlew :shared:compileKotlinJvm :androidApp:compileDebugKotlin :androidApp:assembleDebug`).
- Blockers: No code blockers. Pending manual emulator confirmation of authenticated end-to-end flow to finalize Phase 6.
