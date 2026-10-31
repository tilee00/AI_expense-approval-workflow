# Expense Approval API with `@PreAuthorize` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Spring Boot expense-approval API that teaches `@PreAuthorize` through role, ownership, and state rules, each proven by a test.

**Architecture:** Stateless JWT authentication feeds a `SecurityFilterChain`; authorization lives on `ExpenseService` methods as `@PreAuthorize`, with a small `ExpenseSecurity` bean for ownership checks. H2 + JPA persist users and expenses; controllers stay thin and only translate HTTP.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Spring Security 7.1.2, Spring Data JPA, H2, Gradle (Groovy DSL), JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-31-expense-approval-preauthorize-design.md`

## Global Constraints

- Java toolchain **21**; Boot **4.1.2**; Security **7.1.2**; Gradle wrapper from Initializr.
- Boot 4 artifact names: `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `com.h2database:h2`, and the matching `*-test` starters. Do not substitute Boot 3 names.
- Boot 4 test import: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`. Do **not** use `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`.
- No Lombok. Explicit constructors and getters.
- DTOs are Java `record`s.
- All business endpoints live under `/api/**`; only `POST /api/auth/login` and `/error` are public.
- JWT: HS256, `sub` = username, `roles` = list of `ROLE_*` strings, 60-minute expiry, secret from `app.jwt.secret`.
- Roles: `EMPLOYEE`, `MANAGER`, `FINANCE`, `ADMIN`. `ADMIN` is named explicitly in every rule; there is no role hierarchy.
- Seed users: `alice/password123` (EMPLOYEE), `bob/password123` (MANAGER), `carol/password123` (FINANCE), `dave/password123` (ADMIN).
- `spring.jpa.open-in-view=false`; entity-to-DTO mapping happens inside service transactions.
- Compile with `-parameters` so SpEL can use `#id`.
- Tests run with `app.seed=false` and create their own fixtures; test classes are `@Transactional` and roll back.
- All JSON request bodies in tests are text blocks; do not depend on a Jackson `ObjectMapper` (Boot 4 ships Jackson 3 under `tools.jackson`).
- Work happens on branch `feature/expense-demo` in a freshly initialized repository.

## Review Focus

Inputs and conditions the spec implies but no feature test naturally covers. Each has a test in the task that owns the code.

1. **Expired token** — a reasonable user expects `401`, not `500`. Test `expiredTokenReturns401` in Task 3.
2. **Token with no `roles` claim** — expects `403` on role-gated endpoints and no crash on plain authenticated ones. Tests `tokenWithoutRolesIsAuthenticated` (Task 3) and `tokenWithoutRolesIsForbidden` (Task 5).
3. **Approving an already-approved claim** — expects `409`, never a second `200`. Test `approvingTwiceReturns409` in Task 6.
4. **Zero/negative amount or more than two decimals** — expects `400` with a field message. Tests `zeroAmountReturns400`, `negativeAmountReturns400`, `moreThanTwoDecimalsReturns400` in Task 5.
5. **Whitespace-only description** — expects `400`, not a saved blank row. Test `blankDescriptionReturns400` in Task 5.

---

## File Structure

```
build.gradle  settings.gradle  gradlew  gradlew.bat  gradle/wrapper/
src/main/java/com/example/expense/
  ExpenseApplication.java
  config/SecurityConfig.java
  security/ExpenseSecurity.java
  auth/AuthController.java  AuthService.java  LoginRequest.java  TokenResponse.java  MeResponse.java
  user/User.java  Role.java  UserRepository.java  UserResponse.java  UserService.java
  expense/Expense.java  ExpenseStatus.java  ExpenseRepository.java  ExpenseService.java
          ExpenseController.java  ExpenseRequest.java  ExpenseResponse.java
  common/ApiError.java  ApiExceptionHandler.java  JsonErrors.java  NotFoundException.java
         ConflictException.java  InvalidCredentialsException.java
         RestAuthenticationEntryPoint.java  RestAccessDeniedHandler.java
  seed/DataSeeder.java
src/main/resources/application.properties
src/test/java/com/example/expense/
  AuthTest.java  ExpenseSecurityTest.java  ExpenseAuthorizationTest.java
  ExpenseStateTest.java  AdminUserTest.java  TestTokens.java
requests.http  README.md  docs/LEARNING-GUIDE.md
```

---

### Task 1: Toolchain, scaffold, and repository

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`, `.gitattributes`, `src/main/java/com/example/expense/ExpenseApplication.java`, `src/main/resources/application.properties`, `src/test/java/com/example/expense/ExpenseApplicationTests.java`

**Interfaces:**
- Consumes: nothing.
- Produces: a compiling Gradle project with root package `com.example.expense` and the `-parameters` compiler flag every later task depends on.

- [ ] **Step 1: Install JDK 21**

Run: `winget install --id EclipseAdoptium.Temurin.21.JDK --silent --accept-package-agreements --accept-source-agreements`
Expected: exit code 0. Then locate it and pin it for this session:

```powershell
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
       Where-Object Name -like "jdk-21*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$env:Path"
java -version
```

Expected: `openjdk version "21...`

- [ ] **Step 2: Generate the project from Spring Initializr**

Run from the repo root:

```powershell
Invoke-WebRequest -UseBasicParsing -OutFile starter.zip `
  "https://start.spring.io/starter.zip?type=gradle-project&language=java&bootVersion=4.1.2&javaVersion=21&groupId=com.example&artifactId=expense-demo&name=expense-demo&packageName=com.example.expense&dependencies=web,security,data-jpa,validation,h2,oauth2-resource-server"
Expand-Archive starter.zip -DestinationPath . -Force
Remove-Item starter.zip
```

Expected: `build.gradle` contains `spring-boot-starter-webmvc` and `spring-boot-starter-security-oauth2-resource-server`.

- [ ] **Step 3: Add the compiler flag**

Append to `build.gradle`:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-parameters'
}
```

- [ ] **Step 4: Set application properties**

Replace `src/main/resources/application.properties` with:

```properties
spring.application.name=expense-demo
spring.datasource.url=jdbc:h2:mem:expenses;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
app.jwt.secret=demo-only-secret-change-me-0123456789abcdef0123456789abcdef
app.jwt.ttl-minutes=60
app.seed=true
```

- [ ] **Step 5: Run the generated test**

Run: `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`; first run downloads Gradle and dependencies (several minutes).

- [ ] **Step 6: Initialize git and branch**

```powershell
git init -b main
git config user.name  # if empty, set a repo-local identity first
git add .
git commit -m "chore: scaffold Spring Boot 4.1.2 project"
git switch -c feature/expense-demo
```

If `user.name` or `user.email` is unset, run
`git config user.name "Expense Demo"` and
`git config user.email "demo@example.com"` before committing.
Expected: `Switched to a new branch 'feature/expense-demo'`.

---

### Task 2: Domain model and repositories

**Files:**
- Create: `src/main/java/com/example/expense/user/User.java`, `Role.java`, `UserRepository.java`
- Create: `src/main/java/com/example/expense/expense/Expense.java`, `ExpenseStatus.java`, `ExpenseRepository.java`
- Test: `src/test/java/com/example/expense/DomainModelTest.java`

**Interfaces:**
- Consumes: project scaffold from Task 1.
- Produces:
  - `enum Role { EMPLOYEE, MANAGER, FINANCE, ADMIN }`
  - `enum ExpenseStatus { DRAFT, SUBMITTED, APPROVED, REJECTED, PAID }`
  - `User(String username, String passwordHash, String displayName, Role... roles)`, getters `getId()`, `getUsername()`, `getPasswordHash()`, `getDisplayName()`, `getRoles()`
  - `Expense(User owner, BigDecimal amount, String currency, String description)`, getters/setters for `status`, `submittedAt`, `decidedAt`, `decidedBy`, `paidAt`
  - `UserRepository.findByUsername(String): Optional<User>`
  - `ExpenseRepository.findByOwnerUsernameOrderByIdAsc(String): List<Expense>`, `findAllByOrderByIdAsc(): List<Expense>`

The seeder is deliberately **not** in this task: it needs a `PasswordEncoder` bean, which Task 3 creates. It is built in Task 3 Step 6.

- [ ] **Step 1: Write the failing test**

`DomainModelTest` — `@SpringBootTest(properties = "app.seed=false")`, `@Transactional`:

```java
@Test
void savesUserWithRolesAndExpenseOwnedByThem() {
    User alice = users.save(new User("alice", "x", "Alice", Role.EMPLOYEE));
    Expense saved = expenses.save(new Expense(alice, new BigDecimal("42.50"), "EUR", "Taxi"));

    Expense found = expenses.findById(saved.getId()).orElseThrow();
    assertThat(found.getOwner().getUsername()).isEqualTo("alice");
    assertThat(found.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
    assertThat(users.findByUsername("alice").orElseThrow().getRoles())
        .containsExactly(Role.EMPLOYEE);
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `.\gradlew.bat test --tests "com.example.expense.DomainModelTest"`
Expected: FAIL — `cannot find symbol: class User`.

- [ ] **Step 3: Implement the model and repositories**

`User` — `@Entity @Table(name = "users")` (the table name `users` avoids H2's reserved `USER`); `@ElementCollection(fetch = FetchType.EAGER)` + `@CollectionTable(name = "user_roles")` + `@Enumerated(EnumType.STRING)` for roles; protected no-arg constructor for JPA; public constructor as in Interfaces; no setters.

`Expense` — `@Entity @Table(name = "expenses")`; `@ManyToOne(fetch = FetchType.LAZY, optional = false)` owner; `@Column(precision = 12, scale = 2)` amount; `@Column(length = 3)` currency; `@Column(length = 500)` description; `@Enumerated(EnumType.STRING)` status defaulting to `DRAFT`; `Instant` fields; `@ManyToOne(fetch = FetchType.LAZY)` `decidedBy`.

Repositories are plain `JpaRepository` interfaces with the derived queries above.

- [ ] **Step 4: Run the test and make it pass**

Run: `.\gradlew.bat test --tests "com.example.expense.DomainModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/expense/user src/main/java/com/example/expense/expense src/test/java/com/example/expense/DomainModelTest.java
git commit -m "feat: add user and expense domain model"
```

---

### Task 3: JWT login, filter chain, and 401/403 JSON

**Files:**
- Create: `config/SecurityConfig.java`, `auth/AuthController.java`, `AuthService.java`, `LoginRequest.java`, `TokenResponse.java`, `MeResponse.java`
- Create: `common/ApiError.java`, `JsonErrors.java`, `InvalidCredentialsException.java`, `RestAuthenticationEntryPoint.java`, `RestAccessDeniedHandler.java`, `ApiExceptionHandler.java`
- Create: `seed/DataSeeder.java` (moved from Task 2 if not yet created)
- Test: `src/test/java/com/example/expense/AuthTest.java`, `TestTokens.java`

**Interfaces:**
- Consumes: `User`, `Role`, `UserRepository`.
- Produces:
  - Beans: `PasswordEncoder`, `SecretKey jwtSecretKey`, `JwtEncoder jwtEncoder`, `JwtDecoder jwtDecoder`, `Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter`, `SecurityFilterChain filterChain`
  - `AuthService.login(LoginRequest): TokenResponse`
  - `POST /api/auth/login` → `TokenResponse(String token, long expiresIn, List<String> roles)`
  - `GET /api/auth/me` → `MeResponse(String username, List<String> roles)`
  - `ApiError(Instant timestamp, int status, String error, String message, String path)`
  - `JsonErrors.write(HttpServletResponse, int, String, String, String)`
  - `ApiExceptionHandler` handles `InvalidCredentialsException` → 401

- [ ] **Step 1: Write the failing tests**

`AuthTest` — `@SpringBootTest(properties = "app.seed=false")`, `@AutoConfigureMockMvc`, `@Transactional`. `@BeforeEach` saves `alice` with `passwordEncoder.encode("password123")`.

```java
@Test void loginReturnsTokenAndRoles()          // POST login → 200, $.token non-empty, $.roles[0] = "ROLE_EMPLOYEE"
@Test void loginWithWrongPasswordReturns401()   // password "nope" → 401
@Test void loginWithUnknownUserReturns401()     // username "ghost" → 401
@Test void protectedEndpointWithoutTokenReturns401()   // GET /api/auth/me → 401
@Test void expiredTokenReturns401()             // TestTokens.expired(alice) → 401
@Test void tokenSignedWithDifferentKeyReturns401()     // token from a second encoder → 401
@Test void tokenWithoutRolesIsAuthenticated()   // TestTokens.withoutRoles(alice) → GET /me → 200, $.username = "alice"
@Test void validTokenReachesProtectedEndpoint() // TestTokens.forUser(alice) → 200
```

`TestTokens` — plain class, constructor takes `JwtEncoder`:
`String forUser(User)`, `String expired(User)`, `String withoutRoles(User)`. Each builds `JwtClaimsSet` with `subject`, `issuedAt`, `expiresAt`, and a `roles` claim of `ROLE_`-prefixed names (`withoutRoles` omits the claim).

- [ ] **Step 2: Run and watch them fail**

Run: `.\gradlew.bat test --tests "com.example.expense.AuthTest"`
Expected: FAIL — no `AuthService`/`SecurityConfig`, requests return `403` or fail to compile.

- [ ] **Step 3: Implement the security beans**

`SecurityConfig` with `@Configuration` and `@EnableMethodSecurity`:

```java
@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

@Bean SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
}

@Bean JwtEncoder jwtEncoder(SecretKey key) { return NimbusJwtEncoder.withSecretKey(key).build(); }

@Bean JwtDecoder jwtDecoder(SecretKey key) {
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
}
```

If `NimbusJwtEncoder.withSecretKey` is unavailable at compile time, use `new NimbusJwtEncoder(new ImmutableSecret<>(key))` from `com.nimbusds.jose.jwk.source`.

Converter:

```java
@Bean
Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return jwt -> {
        List<String> roles = jwt.getClaimAsStringList("roles");
        var authorities = (roles == null ? List.<String>of() : roles).stream()
                .map(SimpleGrantedAuthority::new).toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    };
}
```

Filter chain: CSRF disabled, `SessionCreationPolicy.STATELESS`, permit `/api/auth/login` and `/error`, everything else authenticated, `oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))`, and the two JSON handlers wired through `exceptionHandling`.

- [ ] **Step 4: Implement login and `/me`**

`AuthService.login` — find by username or throw `InvalidCredentialsException`; `passwordEncoder.matches` or throw the same exception; build claims (`sub`, `roles`, `iat`, `exp`, `app.jwt.ttl-minutes`) and encode with `JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)`.

`AuthController` — `POST /api/auth/login` with `@Valid @RequestBody LoginRequest`; `GET /api/auth/me` reads `Authentication` and returns `new MeResponse(auth.getName(), authorities)`.

- [ ] **Step 5: Add the JSON error plumbing**

`JsonErrors.write` sets status and `application/json`, then writes:
`{"timestamp":"<Instant.now()>","status":<n>,"error":"<...>","message":"<...>","path":"<uri>"}`.
Escape only the path by replacing `"` with `'`; all other strings are constants.

`RestAuthenticationEntryPoint` → 401 `Unauthorized` / `Authentication required`.
`RestAccessDeniedHandler` → 403 `Forbidden` / `Access denied`.
`ApiExceptionHandler` (`@RestControllerAdvice`) handles `InvalidCredentialsException` → 401 `Bad credentials`, using `HttpServletRequest.getRequestURI()`.

- [ ] **Step 6: Add the seeder**

As described in Task 2 Step 5, now that `PasswordEncoder` exists. Seed only when the user table is empty.

- [ ] **Step 7: Run the tests**

Run: `.\gradlew.bat test --tests "com.example.expense.AuthTest"`
Expected: PASS, 8 tests.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/example/expense/config src/main/java/com/example/expense/auth src/main/java/com/example/expense/common src/main/java/com/example/expense/seed src/test/java/com/example/expense/AuthTest.java src/test/java/com/example/expense/TestTokens.java
git commit -m "feat: add JWT login and stateless security filter chain"
```

---

### Task 4: `ExpenseSecurity` ownership bean

**Files:**
- Create: `src/main/java/com/example/expense/security/ExpenseSecurity.java`
- Test: `src/test/java/com/example/expense/ExpenseSecurityTest.java`

**Interfaces:**
- Consumes: `ExpenseRepository`, `Expense`, `User`.
- Produces: bean `expenseSecurity` with
  `boolean isOwner(Long, Authentication)`, `boolean canView(Long, Authentication)`, `boolean canEdit(Long, Authentication)`.

- [ ] **Step 1: Write the failing test**

`ExpenseSecurityTest` — plain JUnit + Mockito (no Spring context). Build an `Expense` owned by `alice`, stub `expenseRepository.findById(1L)`. Use `TestingAuthenticationToken("alice", null, "ROLE_EMPLOYEE")` and role variants.

```java
@Test void ownerCanEditOwnExpense()                 // canEdit(1L, alice) == true
@Test void strangerCannotEdit()                     // canEdit(1L, bob) == false
@Test void managerCanViewButNotEdit()               // canView true, canEdit false
@Test void managerCannotEdit()                      // canEdit(1L, manager) == false
@Test void missingExpenseDeniesEveryone()           // canView(99L, manager) == false
@Test void employeeCannotViewSomeoneElsesExpense()  // canView(1L, otherEmployee) == false
```

- [ ] **Step 2: Run and watch it fail**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseSecurityTest"`
Expected: FAIL — `cannot find symbol: class ExpenseSecurity`.

- [ ] **Step 3: Implement the bean**

`@Component("expenseSecurity")`, constructor takes `ExpenseRepository`.

- `isOwner`: load expense; return `false` if absent; compare `expense.getOwner().getUsername()` with `authentication.getName()`.
- `canView`: `isOwner(...)` OR authorities contain `ROLE_MANAGER`, `ROLE_FINANCE`, or `ROLE_ADMIN`.
- `canEdit`: `isOwner(...)` only.

- [ ] **Step 4: Run the test**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseSecurityTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/expense/security src/test/java/com/example/expense/ExpenseSecurityTest.java
git commit -m "feat: add ownership security bean for expense access"
```

---

### Task 5: Expense CRUD with role and ownership rules

**Files:**
- Create: `expense/ExpenseRequest.java`, `ExpenseResponse.java`, `ExpenseService.java`, `ExpenseController.java`
- Create: `common/NotFoundException.java`, `ConflictException.java`
- Modify: `common/ApiExceptionHandler.java`
- Test: `src/test/java/com/example/expense/ExpenseAuthorizationTest.java`

**Interfaces:**
- Consumes: `expenseSecurity` bean, repositories, `UserRepository`.
- Produces:
  - `ExpenseRequest(@NotNull @Positive @Digits(integer=10, fraction=2) BigDecimal amount, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotBlank @Size(max=500) String description)`
  - `ExpenseResponse(Long id, String owner, BigDecimal amount, String currency, String description, ExpenseStatus status, Instant submittedAt, Instant decidedAt, String decidedBy, Instant paidAt)` with `static ExpenseResponse from(Expense)`
  - `ExpenseService.create(ExpenseRequest, Authentication): ExpenseResponse`
  - `listMine(Authentication): List<ExpenseResponse>`, `listAll(): List<ExpenseResponse>`, `get(Long): ExpenseResponse`, `update(Long, ExpenseRequest): ExpenseResponse`, `delete(Long): void`
  - Endpoints `POST /api/expenses`, `GET /api/expenses/mine`, `GET /api/expenses`, `GET|PUT|DELETE /api/expenses/{id}`

- [ ] **Step 1: Write the failing tests**

`ExpenseAuthorizationTest` — `@SpringBootTest(properties = "app.seed=false")`, `@AutoConfigureMockMvc`, `@Transactional`. Fixtures in `@BeforeEach`: alice (EMPLOYEE), bob (MANAGER), erin (EMPLOYEE), one DRAFT expense owned by alice, one owned by erin. Tokens from `TestTokens`.

```java
@Test void createReturns201WithLocationAndOwner()
@Test void employeeListsOnlyOwnExpenses()
@Test void employeeCannotListAllExpenses()          // GET /api/expenses → 403
@Test void managerCanListAllExpenses()              // 200
@Test void ownerCanReadOwnExpense()                 // 200
@Test void strangerCannotReadExpense()              // 403
@Test void managerCanReadAnyExpense()               // 200
@Test void ownerCanUpdateDraft()                    // 200, description changed
@Test void strangerCannotUpdateExpense()            // 403
@Test void ownerCanDeleteDraft()                    // 204
@Test void strangerCannotDeleteExpense()            // 403
@Test void employeeReadingMissingExpenseReturns403()
@Test void zeroAmountReturns400()
@Test void negativeAmountReturns400()
@Test void moreThanTwoDecimalsReturns400()
@Test void blankDescriptionReturns400()
@Test void lowercaseCurrencyReturns400()
@Test void tokenWithoutRolesIsForbidden()           // GET /api/expenses with no roles claim → 403
```

- [ ] **Step 2: Run and watch them fail**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseAuthorizationTest"`
Expected: FAIL — `ExpenseService` does not exist; requests hit 404/401.

- [ ] **Step 3: Implement DTOs and the service**

`ExpenseRequest` carries the validation annotations shown above; `ExpenseResponse.from` maps owner/decidedBy usernames and leaves `decidedBy` null when absent.

`ExpenseService` is `@Service @Transactional` with `@Transactional(readOnly = true)` on reads. Annotations:

```java
@PreAuthorize("isAuthenticated()")
public ExpenseResponse create(ExpenseRequest request, Authentication authentication)

@PreAuthorize("isAuthenticated()")
public List<ExpenseResponse> listMine(Authentication authentication)

@PreAuthorize("hasAnyRole('MANAGER','FINANCE','ADMIN')")
public List<ExpenseResponse> listAll()

@PreAuthorize("@expenseSecurity.canView(#id, authentication)")
public ExpenseResponse get(Long id)

@PreAuthorize("@expenseSecurity.canEdit(#id, authentication)")
public ExpenseResponse update(Long id, ExpenseRequest request)

@PreAuthorize("@expenseSecurity.canEdit(#id, authentication)")
public void delete(Long id)
```

`create` resolves the owner with `userRepository.findByUsername(authentication.getName())`, throws `NotFoundException` if absent, saves a `DRAFT`. `update` loads, requires `DRAFT` (else `ConflictException`), copies amount/currency/description. `delete` loads, requires `DRAFT`. `get`/`update`/`delete` throw `NotFoundException` when the row is missing — unreachable for owner-checked endpoints in practice, but correct.

- [ ] **Step 4: Implement the controller**

Thin delegation; `POST` returns `ResponseEntity.created(location).body(response)` where location is `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri()`. `DELETE` returns `204` via `@ResponseStatus(HttpStatus.NO_CONTENT)`.

- [ ] **Step 5: Extend the exception handler**

Add handlers: `NotFoundException` → 404 `Not Found`; `ConflictException` → 409 `Conflict`; `MethodArgumentNotValidException` → 400 with the first field error's default message; `AccessDeniedException` → 403 `Forbidden`. Keep the `ApiError` shape.

- [ ] **Step 6: Run the tests**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseAuthorizationTest"`
Expected: PASS, 18 tests.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/expense/expense src/main/java/com/example/expense/common src/test/java/com/example/expense/ExpenseAuthorizationTest.java
git commit -m "feat: add expense CRUD with role and ownership rules"
```

---

### Task 6: Workflow transitions

**Files:**
- Modify: `expense/ExpenseService.java`, `expense/ExpenseController.java`
- Test: `src/test/java/com/example/expense/ExpenseStateTest.java`

**Interfaces:**
- Consumes: `ExpenseService` CRUD from Task 5, `ExpenseStatus`.
- Produces: `submit(Long)`, `approve(Long, Authentication)`, `reject(Long, Authentication)`, `pay(Long)` and endpoints `POST /api/expenses/{id}/submit|approve|reject|pay`, all returning `ExpenseResponse`.

- [ ] **Step 1: Write the failing tests**

`ExpenseStateTest` — same harness as Task 5. Fixtures: alice EMPLOYEE, bob MANAGER, carol FINANCE, dave ADMIN, plus expenses in DRAFT, SUBMITTED, APPROVED, REJECTED.

```java
@Test void ownerCanSubmitDraft()                  // 200, status SUBMITTED, submittedAt set
@Test void strangerCannotSubmit()                 // 403
@Test void managerCanApproveSubmitted()           // 200, status APPROVED, decidedBy "bob"
@Test void employeeCannotApprove()                // 403
@Test void financeCannotApprove()                 // 403
@Test void adminCanApprove()                      // 200
@Test void managerCanRejectSubmitted()            // 200, status REJECTED
@Test void financeCanPayApproved()                // 200, status PAID, paidAt set
@Test void employeeCannotPay()                    // 403
@Test void managerApprovingMissingExpenseReturns404()
@Test void editingSubmittedExpenseReturns409()
@Test void submittingTwiceReturns409()
@Test void approvingTwiceReturns409()
@Test void payingBeforeApprovalReturns409()
@Test void payingRejectedExpenseReturns409()
```

- [ ] **Step 2: Run and watch them fail**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseStateTest"`
Expected: FAIL — 404 on `/submit`, `/approve`, `/reject`, `/pay`.

- [ ] **Step 3: Implement the transitions**

Add the four methods with annotations:

```java
@PreAuthorize("@expenseSecurity.isOwner(#id, authentication)")  // submit
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")                   // approve, reject
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")                   // pay
```

Each loads the expense (`NotFoundException` if absent), calls a private `requireStatus(expense, expected)` that throws `ConflictException` on mismatch, then applies the change and timestamps. `approve`/`reject` set `decidedBy` from `userRepository.findByUsername(authentication.getName())`.

- [ ] **Step 4: Add the controller endpoints**

`POST /{id}/submit`, `/approve`, `/reject`, `/pay` → `200` with `ExpenseResponse`.

- [ ] **Step 5: Run the tests**

Run: `.\gradlew.bat test --tests "com.example.expense.ExpenseStateTest"`
Expected: PASS, 15 tests.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/expense/expense src/test/java/com/example/expense/ExpenseStateTest.java
git commit -m "feat: add submit, approve, reject, and pay transitions"
```

---

### Task 7: Admin user listing

**Files:**
- Create: `user/UserResponse.java`, `user/UserService.java`, `user/AdminUserController.java`
- Test: `src/test/java/com/example/expense/AdminUserTest.java`

**Interfaces:**
- Consumes: `UserRepository`.
- Produces: `UserResponse(Long id, String username, String displayName, Set<Role> roles)`, `UserService.listAll(): List<UserResponse>`, `GET /api/admin/users`.

- [ ] **Step 1: Write the failing tests**

`AdminUserTest` — same harness. Fixtures: alice EMPLOYEE, bob MANAGER, dave ADMIN.

```java
@Test void adminCanListUsers()                 // 200, body contains "alice"
@Test void employeeCannotListUsers()           // 403
@Test void managerCannotListUsers()            // 403
@Test void responseNeverContainsPasswordHash() // 200 as admin, body does not contain "passwordHash"
```

- [ ] **Step 2: Run and watch them fail**

Run: `.\gradlew.bat test --tests "com.example.expense.AdminUserTest"`
Expected: FAIL — 404 on `/api/admin/users`.

- [ ] **Step 3: Implement the service and controller**

`UserService.listAll()` — `@PreAuthorize("hasRole('ADMIN')")`, `@Transactional(readOnly = true)`, maps every user to `UserResponse` (never `passwordHash`). `AdminUserController` — `@RestController @RequestMapping("/api/admin/users")`, `@GetMapping` delegates.

- [ ] **Step 4: Run the tests**

Run: `.\gradlew.bat test --tests "com.example.expense.AdminUserTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/expense/user src/test/java/com/example/expense/AdminUserTest.java
git commit -m "feat: add admin-only user listing"
```

---

### Task 8: README, learning guide, and request file

**Files:**
- Create: `README.md`, `docs/LEARNING-GUIDE.md`, `requests.http`

**Interfaces:**
- Consumes: every endpoint and the seed users.
- Produces: runnable instructions and the teaching narrative.

- [ ] **Step 1: Write `README.md`**

Sections: what this project teaches; prerequisites (JDK 21); `.\gradlew.bat bootRun`; the four seed users and their passwords; how to log in with `curl`; pointer to `requests.http` and `docs/LEARNING-GUIDE.md`; how to run tests.

- [ ] **Step 2: Write `docs/LEARNING-GUIDE.md`**

For each of the eleven rules: the endpoint, the annotation, the test that proves it, the status codes it produces, and the mistake it prevents. Then four short sections: "Why the annotation lives on the service", "403 vs 409", "Why a missing expense is 403, not 404", and "What `@PreAuthorize` cannot do" (row filtering — hence `/mine` vs `/all`).

- [ ] **Step 3: Write `requests.http`**

Variables `@host = http://localhost:8080`; login blocks named `alice`, `bob`, `carol`, `dave`; then requests that demonstrate: alice creates and submits, bob approves, carol pays, alice tries to approve (403), erin reads alice's expense (403). Use `Authorization: Bearer {{alice.response.body.$.token}}`.

- [ ] **Step 4: Verify the guide's claims by running the app**

Start `.\gradlew.bat bootRun` in the background. Then:

```powershell
$token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body '{"username":"alice","password":"password123"}').token
Invoke-RestMethod -Uri http://localhost:8080/api/expenses/mine -Headers @{ Authorization = "Bearer $token" }
```

Expected: a JSON array of alice's seeded expenses. Then confirm a manager-only call as alice returns 403:

```powershell
try { Invoke-RestMethod -Uri http://localhost:8080/api/expenses -Headers @{ Authorization = "Bearer $token" } } catch { $_.Exception.Response.StatusCode.value__ }
```

Expected: `403`. Stop the server.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/LEARNING-GUIDE.md requests.http
git commit -m "docs: add README, learning guide, and request collection"
```

---

### Task 9: Full verification

**Files:**
- Modify: only if a test exposes a defect.

**Interfaces:**
- Consumes: everything.
- Produces: evidence the project works from a clean state.

- [ ] **Step 1: Run the whole suite**

Run: `.\gradlew.bat clean test`
Expected: `BUILD SUCCESSFUL`, no failures. Record the test count.

- [ ] **Step 2: Start the app and smoke-test the real flow**

`.\gradlew.bat bootRun` in the background, then with `curl`/`Invoke-RestMethod`: alice logs in, creates an expense, submits it; bob logs in and approves it; carol pays it; alice's approve attempt returns 403; a request with no token returns 401.

- [ ] **Step 3: Stop the server and check the working tree**

Run: `git status --short`
Expected: empty.

- [ ] **Step 4: Commit any fixes**

If Step 1 or 2 required a change, commit it with a message naming the defect. Otherwise there is nothing to commit.

---

## Self-Review Notes

- **Spec coverage:** all eleven rules from the spec's table have a task and at least one test; login, seeding, error shape, docs, and the `requests.http` deliverable are covered.
- **Type consistency:** `ExpenseResponse.from`, `TestTokens.forUser/expired/withoutRoles`, and `expenseSecurity.isOwner/canView/canEdit` are used with the same signatures everywhere.
- **Deviation from spec:** one extra endpoint, `GET /api/auth/me`, so Task 3 can prove authentication before the expense endpoints exist; it is documented in the guide as a debugging aid. Validation tests live in `ExpenseAuthorizationTest` rather than a separate class.
- **Known accepted behavior:** a token stays valid for its lifetime even if the user row is deleted; the guide notes this and points at token revocation as the production answer.
