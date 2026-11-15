# Allô immutable sales/printing batch — 2026-11-15

Owner authorization: attachment461d7ddd-09bc-48b2-a52d-7de6eb4bde38 explicitly
requests this compatible Allô-only release and bounded Takeout/spice repair.
No additional ordinary-step confirmation; all safety/review gates remain required.

- Application SHA: `079b1759f8bfd7be9177c6b6b7b134d16e090167`; PR315, isolated Allô delivery line; not main.
- Ops SHA: `9d9954080501fc635210f04400e6e3b132792e97`; reviewed build-reference-only fix. Executable
  backend/frontend/Android source is byte-identical to the application SHA.
- JAR SHA256: `923b22de0631bf6617498c77938707b331098918c554f02a9bdcf414fd593da8`.
- APK1.1.0/code5 SHA256: `1e213b39659dc654f3d8c846e7027db07dd0d243f4e7308c97b4c09671ce21d7`, 4376156 bytes,
  applicationId `com.allonouilles.pos`, existing Debug signing certificate
  `eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106`.
- Frozen final manifest SHA256: `a15471f0c94e3e4aaafe928d2a8eb5e688564102924f2f30fd1c61a09381456f`.
- Initial base transfer SHA256: `3c07832be1a33848e3817f1bb1afedb82d8f01beaba723bb438c45b2cf5a8f21` (application artifacts
  retained; reviewed small ops delta replaces only helper/manifest).
- Target: `43.166.242.250`, project `allo-nouilles`, singleton Store1/org1,
  code `ALLO_NOUILLES_TEST`, actual name Allô Nouilles, Toronto timezone.
- Previous backend4c75f960d1ad00a799c1d5054f22286149429447;
  previous frontend b78ab21d9d4b97bf2b6526ccc489503f486b866e.
- Migration: additive V44 only. No applied migration edited. No database container
  or data-volume restart/replacement, no shared ingress or Lanzhou mutation.
- Backup: `/srv/allo-nouilles/backups/sales-printing-20261115/PRODUCTION_RECOVERY_ONLY_20261115T062255Z.dump`; SHA256 `a0ce6a905c1615203d21a41ea7ba90b4d21b156f534a6b224fb455d6355a54d0`.
  87 tables restored in a socket-only recovery cluster, all row hashes and
  constraints/indexes checked; cluster removed; never used as QA data.
- Acceptance: 325 backend tests,522 frontend tests,TypeScript,18 Native unit
  tests; exact-SHA local synthetic financial/edge/repair/Pad-report API suites;
  real browser1280x800 confirmed amounts2.29→3.43 after added item. Agent6 PASS.
- Actions: stage local-copy backend/frontend images, preserve baseline;
  guard physical leases before/after stopping backend; start exact backend for
  Flyway44; one bounded stop for pre-repair target snapshot + transactional
  Takeout/spice repair + zero-write replay; always restart same new backend in
  finally; publish Web only after health. Keep existing pairing/endpoints.
- Post-release: exact image/SHA/health/V44, public Web hashes, old origin CORS,
  authenticated Store isolation, raw-order/report reconciliation, repair date
  aggregate rebuild, original items/options/print/jobs/tasks/stock and static
  configuration fingerprints. No synthetic Production orders or physical print.
- Recovery: preserve V44 and all new business data. After a financial adjustment,
  do not revert to code that ignores voids/discount rules; use forward repair.
  Frontend-only rollback keeps the compatible backend. Target-specific original
  repair rows/config are retained for revision-guarded compensating ledger work;
  never restore a pre-release database over later orders or replay old tickets.

TOOLING_REASSESSMENT: First stage failed before runtime mutation because Docker
FROM interpreted a bare image ID as a repository name. The11-line repair returns
to the prior reviewed local tag build path, verifies tags against manifest image
IDs, retains failed build logs and reuses only an unchanged protected baseline.
No application rebuild or new deployment architecture was required.

Status: DEPLOYED_AND_SMOKE_PASS; final image IDs, activation and post-release results are recorded in
[delivery evidence](SALES_PRINTING_20261115.md).


## Observed main release

Activated 2026-11-15T07:06:43.021101Z. Backend image `sha256:387f1be90c3de6b4400887735da41e7bdb67d6e811eec29c749e1fe3228bed43`; frontend `sha256:882bd57a45f91a3eba9da7fd71182d9457806e10daad0e1e708b548c189f9941`. V44 all44 success.8 Takeout repaired at original business dates,8 spice groups/32 options soft-disabled,menu_revision98; zero-write replay. Public API/browser acceptance PASS.

## Supplemental immutable backend-only XS original-copy repair

- Backend exact SHA `8b4f5bc61ed182d200e1755f5397f5277152b38e`; same PR315 and Owner repair authority. Only application delta from079b is PrintJobService5lines; no frontend/Android/migration/data repair delta.
- JAR SHA256 `2d1092aeb5cad74114933931604b44ed875868fa82a0f6e674844b4952ecca32`.
- Archive SHA256 `0f606a4364840d95b82d3e397aab6e4cde20e119962c54e163d9c6fd23a269e0`; manifest SHA256 `7303f0437659568a3969f34dc17397ee9372a03ad8b4b007095a02781c4a2bdf`.
- Fresh backup `/srv/allo-nouilles/backups/sales-printing-20261115/PRODUCTION_RECOVERY_ONLY_20261115T071843Z.dump`, SHA256 `66303cdba0fe65beb1b623944b99bbe5b6c360fa3a79898f97daea4823f702e0`;90table row hashes and all constraints/indexes verified socket-only; removed recovery copy.
- 63focused tests PASS, including6new cases; actual signed local original→copy API preserved bytes/provenance/source,0TCP; Agent6 PASS.
- Reused reviewed backend-only stage/activation helpers, current image/config hashes pinned, known base tag→exact image9f476e checked, double CLAIMED/PRINTING guard. Only BACKEND_IMAGE/APP_BUILD_SHA changed; no compose schema delta or SQL, no queue reset, no automatic physical retry.
- New image `sha256:d760071080481d61486f6f9c1a94fb20e3aacfee560fe11bd496d88a373b08de`; activated `2026-11-15T07:22:15.490919Z`. Frontend container `b7027cf8489a0f35b0ef1a1663c0626b3bc3afffe4bd1ef83eb9fe35892d00df` and DB `cbd0c6cf504d3b746f880879db3f0beb9da3c3ce212e35c8d2b0a21061820d37` IDs/start times unchanged. V44 retained; protected12domains+device versions unchanged.
- Final public read-only smoke PASS: backend8b4f vs Web/APK079b separated;5CORS cases,Store403/anonymous401,3date raw/API reconciliation and public bundlehash. Final post-ready logs ERROR0;3containershealthy.
- Rollback: saved baseline runtime.env→backend079b only, retainV44 and all business data. Prior079b supports adjustments, no DB rollback. Quiesce physical leases before backend stop. Original XS compatibility would regress; forward repair preferred.
- No additional APK/frontend rebuild: source unchanged. No Production print/sample/adjustment API called. Actual Pad/paper remains hardware pending.
