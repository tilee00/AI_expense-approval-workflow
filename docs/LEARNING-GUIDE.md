# Learning guide: how this project uses `@PreAuthorize`

`@PreAuthorize` answers one question: **may this authenticated person perform
this method call?** It runs before the method body, and a `false` result throws
`AccessDeniedException`, which this project turns into `403 Forbidden`.

Everything else — whether the action makes sense right now — belongs in the
method body.

## The eleven rules

### 1. Create a draft — `POST /api/expenses`

```java
@PreAuthorize("isAuthenticated()")
public ExpenseResponse create(ExpenseRequest request, Authentication authentication)
```

Any logged-in user may create a draft, and the owner is taken from the token, not
from the request body. That is the point: a caller cannot create an expense in
someone else's name by editing JSON.

Test: `ExpenseAuthorizationTest.createReturns201WithLocationAndOwner`.

### 2. List my expenses — `GET /api/expenses/mine`

```java
@PreAuthorize("isAuthenticated()")
public List<ExpenseResponse> listMine(Authentication authentication)
```

The query filters by `authentication.getName()`. `@PreAuthorize` cannot filter
rows — it only allows or denies the call. This is why there are two list
endpoints instead of one clever one.

Test: `ExpenseAuthorizationTest.employeeListsOnlyOwnExpenses`.

### 3. List all expenses — `GET /api/expenses`

```java
@PreAuthorize("hasAnyRole('MANAGER','FINANCE','ADMIN')")
public List<ExpenseResponse> listAll()
```

A role rule, written where everyone can read it. Note that `ADMIN` is named
explicitly. There is no role hierarchy hiding it, so you can see exactly who is
allowed.

Tests: `employeeCannotListAllExpenses`, `managerCanListAllExpenses`,
`tokenWithoutRolesIsForbidden`.

### 4. Read one expense — `GET /api/expenses/{id}`

```java
@PreAuthorize("@expenseSecurity.canView(#id, authentication)")
public ExpenseResponse get(Long id)
```

`@expenseSecurity` is a Spring bean, and `#id` is the method argument. The bean
allows the owner, plus managers, finance and admins. This is the "role **or**
owner" pattern.

Tests: `ownerCanReadOwnExpense`, `strangerCannotReadExpense`,
`managerCanReadAnyExpense`.

### 5. Edit — `PUT /api/expenses/{id}`

```java
@PreAuthorize("@expenseSecurity.canEdit(#id, authentication)")
public ExpenseResponse update(Long id, ExpenseRequest request)
```

`canEdit` is ownership only. A manager can *see* any expense but cannot rewrite
someone else's claim — a distinction that is easy to get wrong with a single
`hasAnyRole` rule.

Tests: `ownerCanUpdateDraft`, `strangerCannotUpdateExpense`.

### 6. Delete — `DELETE /api/expenses/{id}`

Same annotation as edit, same reasoning. Deleting is editing taken to its
extreme.

Tests: `ownerCanDeleteDraft`, `strangerCannotDeleteExpense`.

### 7. Submit — `POST /api/expenses/{id}/submit`

```java
@PreAuthorize("@expenseSecurity.isOwner(#id, authentication)")
public ExpenseResponse submit(Long id)
```

Only the owner may submit. Managers can approve later, but they cannot submit on
someone's behalf.

Tests: `ownerCanSubmitDraft`, `strangerCannotSubmit`.

### 8. Approve — `POST /api/expenses/{id}/approve`

```java
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public ExpenseResponse approve(Long id, Authentication authentication)
```

Finance cannot approve, even though finance can pay. Those are different jobs,
and the annotations say so.

Tests: `managerCanApproveSubmitted`, `employeeCannotApprove`,
`financeCannotApprove`, `adminCanApprove`.

### 9. Reject — `POST /api/expenses/{id}/reject`

Same rule as approve. A rejection is a decision, and only decision-makers decide.

Test: `managerCanRejectSubmitted`.

### 10. Pay — `POST /api/expenses/{id}/pay`

```java
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
public ExpenseResponse pay(Long id)
```

Only finance (or an admin) may move money. The manager who approved the claim
cannot pay it.

Tests: `financeCanPayApproved`, `employeeCannotPay`.

### 11. List users — `GET /api/admin/users`

```java
@PreAuthorize("hasRole('ADMIN')")
public List<UserResponse> listAll()
```

The simplest rule in the project, and the one most teams write first. The DTO
never contains `passwordHash`.

Tests: `adminCanListUsers`, `employeeCannotListUsers`, `managerCannotListUsers`,
`responseNeverContainsPasswordHash`.

## Why the annotation lives on the service

Put the rule on the controller and it protects one entrance. Put it on the
service and it protects every caller: another controller, a scheduled job, a
message listener, a future CLI. Spring Security checks the proxy on the way in,
so the check happens wherever the call starts.

It also keeps the rule next to the operation it describes. A reviewer reading
`approve()` sees the rule without opening another file.

## 403 vs 409 — authorization vs business rules

```java
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")   // who may act
public ExpenseResponse approve(Long id, Authentication authentication) {
    Expense expense = find(id);
    requireStatus(expense, ExpenseStatus.SUBMITTED);  // is the action legal now
    ...
}
```

A manager approving an already-approved claim passes the annotation and gets
`409 Conflict`. That is correct: they were allowed to try, and the claim's state
said no. Collapsing both into `403` hides a real distinction from the client and
from your logs.

Tests: `approvingTwiceReturns409`, `payingBeforeApprovalReturns409`,
`payingRejectedExpenseReturns409`.

## Why a missing expense is 403, not 404

`canView`, `canEdit` and `isOwner` return `false` when the row does not exist.
A stranger probing `/api/expenses/999999` gets `403` and learns nothing about
whether that ID exists. Reporting `404` would confirm it.

The trade-off: an owner asking for a deleted expense also gets `403`. Where the
role check passes before the row is loaded — `approve`, `reject`, `pay` — the
service reports `404` accurately.

Tests: `employeeReadingMissingExpenseReturns403`,
`managerApprovingMissingExpenseReturns404`.

## What `@PreAuthorize` cannot do

It cannot filter a result set. If a manager calls a list endpoint, the annotation
either lets the whole call through or blocks it; it cannot remove rows from the
answer. That is why the project has `/mine` and `/all` rather than one endpoint
that "returns what you are allowed to see".

If you need row filtering, do it in the query, as `listMine` does, or use
`@PostFilter` — knowing it filters in memory after loading everything, which is
a poor fit for large tables.

## Validation runs before authorization

Request-body validation happens while Spring MVC is still binding the arguments,
before the service is called. So a non-owner sending `{"amount": 0}` to
`PUT /api/expenses/{id}` gets `400`, not `403`.

That is fine, but it is worth knowing when you read a test and wonder why a
forbidden request produced a validation error.

Tests: `zeroAmountReturns400`, `negativeAmountReturns400`,
`moreThanTwoDecimalsReturns400`, `blankDescriptionReturns400`,
`lowercaseCurrencyReturns400`.

## Three things this project does not do

- **No refresh tokens or revocation.** A token stays valid until it expires even
  if the user is deleted. Production systems add short lifetimes and a
  revocation list.
- **No role hierarchy.** `ADMIN` is written into each rule. That is deliberate:
  you can read every permission without chasing a hierarchy.
- **No row-level filtering for managers.** They see all expenses. A real system
  would scope by team or department, which needs an org chart.
