# QA screenshots

Taken against a local mock of Plately. The API was `http://localhost:8080` on a Postgres database created on this machine (`scan_to_order`). Paystack was a local stand-in on `127.0.0.1:8099`. No production database, no live Paystack key, and no deploy.

Guest shots are a phone-sized window (390×844). Staff shots are 1360×900.

## Mock accounts

Every demo staff password is `local-demo-pass`, except the platform owner.

| Account | Role | Where it lands |
|---|---|---|
| `thecornertable.manager` | Manager, The Corner Table | `/manager` |
| `thecornertable.chef` | Chef, The Corner Table | `/kitchen` |
| `thecornertable.waiter` | Waiter, The Corner Table | `/waiter` |
| `owambeco.manager` | Manager, Owambe Co (event planner) | `/planner` |
| `platformadmin` | Platform owner. Password `local-platform-pass` | `/platform` |
| Ada Guest | Guest at The Corner Table, table 4, seat 1 | `/t/thecornertable/table/4` |
| Tobi Friend | Second phone, wrong join code on the saved seat | same table |

The seeded businesses are The Corner Table (live restaurant), Mama Put Express (live, manager only), Sunset Grill (waiting for approval), and Owambe Co with the event "Ada and Tobi, the wedding".

## Screens

| File | What it shows |
|---|---|
| `01-staff-signin.png` | Staff sign-in, empty form |
| `02-staff-signin-empty.png` | Sign-in with nothing typed |
| `03-staff-signin-error.png` | Wrong password for the chef |
| `04-kitchen-loading.png` | Chef, "Fetching the orders…" |
| `05-kitchen-empty.png` | Chef, nothing paid yet, stock list |
| `06-kitchen-error.png` | Chef, orders failed to load |
| `07-waiter-to-carry-empty.png` | Waiter, nothing to carry |
| `08-waiter-calls-empty.png` | Waiter, no calls |
| `09-waiter-tables-empty.png` | Waiter, tables before anyone sits |
| `10-manager-floor.png` | Manager, floor |
| `11-manager-menu.png` | Manager, menu |
| `12-manager-tables.png` | Manager, tables |
| `13-manager-team.png` | Manager, team |
| `14-manager-refunds-empty.png` | Manager, no refunds due |
| `15-manager-insights.png` | Manager, insights |
| `16-manager-settings-open.png` | Manager, open for orders |
| `17-platform-approvals-pending.png` | Platform owner, Sunset Grill waiting |
| `18-platform-decline-validation.png` | Decline sheet, reason required |
| `19-platform-tenants.png` | Live restaurants and the waiting application |
| `20-platform-earnings.png` | Platform earnings |
| `21-platform-refunds-empty.png` | No refunds to chase |
| `22-planner-events.png` | Owambe Co event list |
| `23-planner-guest-codes.png` | Guest codes and plate allowances |
| `24-planner-plates.png` | Plate combos |
| `25-planner-coverage.png` | Has everyone eaten |
| `26-planner-tables.png` | Event tables |
| `27-planner-details.png` | Event details |
| `28-signup-form.png` | Restaurant signup |
| `29-signup-validation.png` | Signup with missing fields |
| `30-guest-table-missing.png` | Scan of a table that does not exist |
| `31-guest-seat-map.png` | Table 4, pick a chair |
| `32-guest-claim-sheet.png` | Who is sitting here |
| `33-guest-claim-validation.png` | Claim with no name |
| `34-guest-home.png` | Seated, before a hold |
| `35-guest-saved-seat-code.png` | Seat saved, join code `GWQ 6P5` |
| `36-guest-cart.png` | Basket, ₦25,000, pay button |
| `37-guest-pay-local-checkout.png` | Local stand-in for Paystack. Nothing was charged |
| `38-guest-order-status.png` | Paid order, ready, on its way |
| `39-guest-visit.png` | Visit summary and receipt entry |
| `40-guest-call-waiter.png` | Call sheet |
| `41-guest-call-sent.png` | "A waiter is on the way." |
| `42-guest-join-sheet.png` | Friend opening the saved seat |
| `43-guest-join-wrong-code.png` | Wrong code refused |
| `44-kitchen-new-paid-order.png` | Chef, paid order in New |
| `45-kitchen-sold-out-sheet.png` | Can't make this |
| `46-kitchen-cooking.png` | Started |
| `47-kitchen-ready.png` | Marked ready |
| `48-waiter-delivery.png` | Waiter, ready to deliver |
| `49-waiter-calls.png` | Ada's call |
| `50-waiter-free-seat.png` | Free seat on the occupied table |
| `51-waiter-close-table.png` | Close table, with the unpaid-delivery warning |
| `52-manager-settings-closed.png` | Open for orders switched off |
| `53-guest-restaurant-closed.png` | A new scan while the restaurant is closed |
