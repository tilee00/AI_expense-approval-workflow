# Expense Approval API — learning `@PreAuthorize`

A small Spring Boot 4 REST API that models an expense-approval workflow, built
to show how working Java teams enforce authorization with
`org.springframework.security.access.prepost.PreAuthorize`.

Each rule lives on the service method that performs the action. Each rule has a
test that proves it, including the cases that must fail.

## What you need

- JDK 21 (`java -version` should print 21.x)
- Nothing else — the Gradle wrapper downloads Gradle itself.

## Run it

```powershell
.\gradlew.bat bootRun
```

The API starts on `http://localhost:8080`. Stop it with `Ctrl+C`.

## Seed users

| Username | Password | Role | What they can do |
|---|---|---|---|
| `alice` | `password123` | EMPLOYEE | Create, edit, delete and submit her own drafts |
| `bob` | `password123` | MANAGER | See every expense, approve or reject submitted ones |
| `carol` | `password123` | FINANCE | See every expense, pay approved ones |
| `dave` | `password123` | ADMIN | Everything, including listing users |

Seeded expenses: two drafts, one submitted, one approved, one paid — all owned
by alice. They exist so you can try every transition immediately.

## Try it with curl (PowerShell)

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body '{"username":"alice","password":"password123"}'
$token = $login.token

Invoke-RestMethod -Uri http://localhost:8080/api/expenses/mine `
  -Headers @{ Authorization = "Bearer $token" }
```

Now watch authorization refuse the same user:

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8080/api/expenses `
    -Headers @{ Authorization = "Bearer $token" }
} catch {
  $_.Exception.Response.StatusCode.value__   # 403
}
```

Log in as `bob` and the same call returns every expense.

## Try it in your editor

Open `requests.http` in IntelliJ IDEA or VS Code (with the REST Client
extension). It logs in as all four users and walks the whole workflow, including
the requests that return `403`.

## Run the tests

```powershell
.\gradlew.bat test
```

54 tests. They cover the happy path, every forbidden case, the invalid inputs,
the illegal state transitions, and one run with no test transaction — the way
the real application behaves.

## Where things live

```
src/main/java/com/example/expense/
  config/SecurityConfig.java      JWT wiring and the filter chain
  security/ExpenseSecurity.java   ownership checks used from SpEL
  auth/                           login and token issuing
  expense/                        the workflow and every @PreAuthorize rule
  user/                           users, roles, admin listing
  common/                         JSON errors and exception mapping
  seed/DataSeeder.java            demo users and expenses
src/test/java/com/example/expense/
  AuthTest.java                   login, 401s, token edge cases
  ExpenseSecurityTest.java        ownership bean, in isolation
  ExpenseAuthorizationTest.java   who may do what, plus validation
  ExpenseStateTest.java           legal and illegal transitions
  AdminUserTest.java              the admin-only endpoint
  ExpenseWithoutTestTransactionTest.java  the same code path without a test transaction
```

## Read next

- `docs/LEARNING-GUIDE.md` — rule by rule, with the mistake each one prevents.
- `docs/superpowers/specs/2026-10-31-expense-approval-preauthorize-design.md` —
  the design and the decisions behind it.
