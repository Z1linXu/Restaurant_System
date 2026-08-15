# Phase A Owner Final Staging Checklist

Status:

```text
PHASE_A_OWNER_ACCEPTANCE = PENDING
```

Use current Staging deployed SHA
`ad4572759e01b5546ec59af24aa36b09e5c2dd00` and Flyway `V16`.

## Checklist

- Confirm St-Denis Store identity is retained.
- Open Frontdesk and confirm order entry still works.
- Open Menu Management and confirm Size, Pricing Rules and Combo
  Configuration remain usable.
- Confirm Store module visibility/gating behaves as expected for current Store
  modules.
- Confirm Printing Settings still shows MOCK topology and no physical-printer
  binding is required for this test.
- Submit one safe Staging order and confirm `GRAB`, `FRONTDESK_RECEIPT`, and
  currently applicable kitchen ticket behavior.
- Confirm Staff login/store access boundaries for Owner, Manager and
  Frontdesk accounts.
- Confirm no Chinatown, Sainte-Catherine or new Production Store provisioning
  has started.

If accepted, the Owner may declare:

```text
PHASE_A_OWNER_FINAL_ACCEPTANCE = PASS
```

Next gate after Owner acceptance:

```text
PHASE_B_OWNER_STORE_PROVISIONING_APPROVAL
```
