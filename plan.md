# Northrail

Northrail is a small money ledger for two pretend banks. Sending money looks instant. The real settle-up between banks happens later, in a nightly job, the way Interac does. The system finds mistakes, refuses edits to history, and keeps personal data out of the ledger. A CI gate outside the app asks whether a code change can reach money, and routes safe changes fast and risky ones to a named human with a trace.

This file is the Project 2 design. Code in this repo follows it. The code is behind the design. A missing piece stays in the design until it is built or this file is changed. Interview notes, week-by-week dates, and task logs stay out.

The longer write-up is the Project 2 section of `wealthsimple-intern-plan.canvas.tsx` in the pay-invariants canvases. When the design changes, change it here.

## The story

A user sends money. Both sides see it immediately. That night the banks settle. A job then compares what each bank recorded and lists the breaks.

A developer opens a pull request. The checker asks whether the change can reach money. If no, it takes the easy path and merges. If yes, it takes the hard path: full checks, a named reviewer, and a trace of which money screen or table it can affect.

## Rules that must hold

- Every money move is two ledger rows, one out and one in. Rows are never edited. A mistake is fixed by adding a reversing pair.
- Saving a transfer writes its outbox row in the same database transaction. A worker later sends that row to the other bank. A crash cannot lose it.
- The receiving bank remembers which transfers it already applied. The same transfer arriving twice moves the balance once.
- Both sides see the money right away. Visible and settled are different states. A nightly job does the settle-up and writes a settlement file.
- Reconciliation compares what each bank recorded. Breaks are missing, wrong amount, or unmatched. A switch can drop one credit on purpose so the job has a break to find.
- The ledger stores a customer id only. Names, SINs, and identity documents live in the `pii` schema under a different database user. The ledger role cannot read that schema.
- The ledger role can `INSERT` and `SELECT` ledger rows. It has no `UPDATE` or `DELETE` on them. A trigger blocks edits as well.
- Library versions are locked, checksums are verified, and CI blocks a new library with a known problem.
- One currency. Money rules live in one place. The ledger module does not know about the web or Spring controllers. Controllers call services.
- The danger checker never runs inside the app. It reads the pull request diff. It asks whether the change can reach a screen or action that moves or shows money, whether it changes rounding, mixes currencies, changes a money table, or adds or changes a library. An AI may explain the trace in the pull request comment. The AI does not choose the path.

## Shape

One Spring Boot app, one Postgres database, one Next.js ops page. Docker Compose starts the api, Postgres, and the web page. This is a modular monolith.

Modules inside `apps/api`:

- `ledger`: accounts, entries, `LedgerService`. Kotlin.
- `transfer`: start a transfer and its state.
- `outbox`: the queue table and the worker that sends to the other bank.
- `settlement`: nightly netting and the settlement file.
- `recon`: break types and the finder.
- `shared`: `Money`, `InstitutionId`, `Clock`, `@MovesMoney`, `@ShowsMoney`.

The checker lives in CI, not in the app:

- `.github/workflows/danger-checker.yml` runs layer 1 and CodeQL, then labels the pull request.
- Layer 1 is a short GitHub Action. A diff that touches `ledger/`, `settlement/`, a money-table migration, the `Money` class, `BigDecimal` rounding, a currency field, or `build.gradle` is hard path.
- Layer 2 marks money sinks (`@MovesMoney` on the ledger post, `@ShowsMoney` on balance controllers) and uses one CodeQL query, `codeql/money-reach.ql`, to ask whether any changed method can reach a sink. CodeQL already exists. Northrail does not build its own code-analysis engine.
- `checker-evals/` holds 20 fixture pull requests, 10 safe and 10 risky, including a rounding change three calls away from the ledger. The target is zero misses. False alarms are reported.

## Tables

| Table | What one row means |
| --- | --- |
| `accounts` | One customer account. A customer id only. |
| `ledger.entries` | One leg of money history. Add only. |
| `transfers` | One send, and its state (visible, then settled). |
| `ledger.outbox_events` | One to-do for the worker. `done` is null until delivery finishes. |
| `settlement_batches` | One nightly settle-up. |
| `recon_breaks` | One difference the recon job found. |
| `audit_log` | Who read which customer, and why. First thing to drop if time runs short. |
| `pii.customers` | Names and identity data. Separate schema and database user. |

## The six tests

These names are the proof. A test with the right name still has to prove the behavior below.

1. `transfer_is_atomic_two_legs` — both rows commit, or neither does.
2. `same_key_never_double_posts` — the same transfer applied twice moves the balance once.
3. `worker_crash_still_applies_once` — kill the worker after the outbox row is saved; after restart the money arrives exactly once.
4. `visible_is_not_settled_until_job` — screen credit and settlement are different states.
5. `missing_credit_becomes_break` — drop one credit on purpose; recon finds it.
6. `rounding_change_is_routed_hard_path` — a rounding edit three calls from the ledger gets the hard-path label and a trace.

## Build order

Finish each step before starting the next. The checker comes after the ledger core.

1. Docker Compose, the Kotlin ledger module, two schemas, two database users.
2. Transfer service, outbox table, worker, exactly-once apply on the receiving bank.
3. Grants and the insert-only trigger. Tests 1 to 4. Audit log only if the core is already solid.
4. Visible versus settled, the nightly settlement job, the settlement file.
5. Recon, the three break types, the inject-a-break switch, test 5. Library checks in CI.
6. Danger checker layer 1: easy path or hard path, required reviewer on the hard path.
7. Layer 2: money-sink annotations, the CodeQL query, the trace comment, test 6, the 20 fixture results.
8. A balance read model that can be deleted and rebuilt from entries. The Next.js ops page: send, and a timeline.
9. README, architecture picture, demo script. Then freeze.

Never drop the ledger, the outbox worker, the six tests, or the injected break.

If time runs short, drop in this order: the audit log and the "too many reads" detector, web page polish, an AI explanation of the trace, checker layer 2 (keep layer 1), the rebuildable read model.

## Left out on purpose

Kafka, Kubernetes, and microservices. A real Interac connection. Real card networks. Login for real users. More than one currency. KYC or identity checks. A custom code-analysis engine.

A Postgres queue table stands in for Kafka. Modules inside one app stand in for services. CodeQL already answers whether changed code can reach a method.

## In the code today

Present, and still short of the design:

- Postgres 16 in `docker-compose.yml`, schema in `db/init/011_schemas.sql`. Compose does not start the api or a web page.
- `ledger` and `pii` schemas. `ledger_admin` cannot read `pii`. The app and `LedgerDatabase` connect as `ledger_admin`.
- `ledger.accounts` (`customer_id`, `institution` text) and `ledger.entries` (integer `amount`, `transfer_id`, `time_posted`). `Account` and `Entry` match those columns. There is no `transfers` table, no `Money` type, and no `InstitutionId`.
- `LedgerService.storeEntry` inserts two legs and rolls back unless the amounts sum to zero and the transfer ids match. `calculateBalance` sums amounts. There is no stored balance.
- `TransferService.storeOutboxEntry` writes the two legs and one outbox row in one transaction.
- `entries_are_add_only` rejects `UPDATE` and `DELETE`. Grants match that: insert and select on entries, no update or delete.
- `OutboxWorker.deliver` locks the oldest undone outbox row and sets `done`. It does not send to the other bank. A second run does not post again because the worker never posts.

Tests 1, 2, and 3 exist by name. Test 1 matches the rule. Tests 2 and 3 do not yet prove receiving-bank exactly-once apply, because nothing applies an inbound transfer. Tests 4, 5, and 6 do not exist. Settlement, recon, the checker, and the web page do not exist.

Next work is step 2: the worker delivers to the other bank, and that bank applies each transfer once. Then tests 2 and 3 should prove that behavior.
