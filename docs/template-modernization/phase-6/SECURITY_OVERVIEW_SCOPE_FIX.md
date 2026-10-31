# P6-SEC-OVERVIEW-001 — scoped platform overview

Classification: BUG / SECURITY / CONFIRMED. Discovered during real Test browser acceptance; not a Restaurant Profile customization. Related audit inputsF28/F29; neither finding nor this fix closesF31/STOMP or device-credential gaps.

## Verified trigger and exposure

The Owner logged into SYNTHETIC Org1/Store1 saw Org2/Store2 in Menu Management. `MenuManagementPage` first calls the existing platform overview endpoint and only falls back to scoped management-context after403. Fixed-source `PlatformAdminController.getOverview` authorized the requested Store, then `PlatformAdminServiceImpl.getOverview` returned global organization/template/store arrays. Source restaurant template entities include default configuration JSON.

Direct HTTPS proof on App1a07e1f is in ignored security-overview-before.json: a solely Org1 Owner received stores[1,2] and organizations[1,2]. That Test had0 restaurant_template rows; no actual foreign template configuration or real transaction/credential disclosure is claimed. Separate scoped menu/template read/write endpoints still denied foreign Store access; this was metadata/config exposure rather than proof of their write gates failing.

## Minimum repair

Keep requireForStore and STORE_ADMINISTRATION gates. Reuse existing StoreAccessService.accessibleStores/accessibleOrganizations for response arrays; nonADMIN templates require explicit accessible Org plus an accessible source Store if specified. Unknown/global ownership is not implicit permission to disclose config. Existing ADMIN global view remains; same-Org authorized multi-store access remains. No role/capability model, DB/schema/data, pricing/order/printing/Pad implementation changes.

Controller unit coverage: Owner/Manager serialized response excludes foreign/global configuration markers, authorized same-Org multiple stores, ADMIN global view, forbidden Store gate before data load, and an owned template without a source Store. HTTP coverage adds both roles own-store200 scoped arrays and both foreign-overview403. A bare unit ObjectMapper initially lacked time modules; fixture was corrected to the existing registered-module behavior without removing serialization/leakage assertions. Full regression, independent review and new Test candidate results are linked in release evidence; do not infer Production deployment.

Production/Staging were not patched. Whether today's Production has the fixed-source defect is not independently established by this Test observation. Additional global APIs/permission assignment paths have not received a complete tenant-security audit; this is not SaaS security clearance.
