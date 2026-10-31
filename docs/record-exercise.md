# Record vs DTO vs Bean — 10 Industry-Level Coding Exercises

These questions test whether you know when to use a Java `record`, a DTO (data transfer object), or a bean/entity, and just as importantly, when NOT to use each one.

Each question shows a real-world scenario with a common (but wrong) code example. Your job is to decide: **record? DTO? Bean/Entity? Or none of the above?**

---

## Question 1 — User Registration Request

A new user signs up via `POST /api/auth/register`. The JSON body contains `username`, `email`, and `password`. You need to validate that none are blank and that the email looks valid.

Common (wrong) approach seen in many tutorials:

```java
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    // getters and setters (Lombok @Data)
}
```

**Question:** Should `RegisterRequest` be a record, a DTO class, or a bean/entity? Write the correct version and explain why.

---

### My Answer

```java
public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$") @Size(max = 50) String email,
        @NotBlank @Size(max = 12) String password) {
}
```

## Question 2 — User Profile Response (Security Leak)

Your database has a `User` entity with fields: `id`, `username`, `email`, `passwordHash`, `createdAt`, `role`. You build `GET /api/users/{id}` to return profile info to the caller.

Common (wrong) approach:

```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Question:** Is returning the `User` entity directly safe? If not, what should you return instead? Write the correct response type and controller snippet.

---

### My Answer

```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    var user = userRepository.findById(id).orElseThrow();
    return mapper.toResp(user);
}
```


## Question 3 — Updating a User Profile

A logged-in user can change their `displayName` and `bio` via `PUT /api/users/me`. The request body contains only those two fields. The user record in the database is mutable — it changes over time.

Common (wrong) approach:

```java
public record UpdateProfileRequest(String displayName, String bio) {}

// Used like this inside a mutable User entity:
user.setDisplayName(request.displayName()); // works fine
```

**Question:** Is it correct to use a `record` for the update request? Is it correct to use a `record` for the `User` entity itself? Write the correct types for both the request and the entity.

---

## Question 4 — Shopping Cart That Changes

A customer adds items to a cart, removes items, and changes quantities. The cart lives in the database and changes constantly until checkout.

Common (wrong) approach:

```java
public record CartItem(Long productId, int quantity) {}
public record Cart(Long id, List<CartItem> items) {} // immutable!
```

**Question:** Why is a `record` the wrong choice for the `Cart` object itself? What should the cart be, and what can still be a record inside it?

---

## Question 5 — Application Configuration

At startup, your app reads database settings from a file: `url`, `username`, `password`, `poolSize`. These values never change while the application is running.

Common (wrong) approach:

```java
@Component
public class DbConfig {
    private String url;
    private String username;
    // setters allow anyone to change these at runtime
}
```

**Question:** What type should `DbConfig` be? Write it so it cannot be changed after construction, and explain why that matters for safety.

---

## Question 6 — Paginated Product Search

A caller searches products with `GET /api/products?page=0&size=20&sort=price&keyword=shoes`. The response contains the page of products plus metadata: total elements, total pages, current page.

Common (wrong) approach:

```java
// Passing 6 separate parameters to the controller method
@GetMapping
public List<Product> search(
    @RequestParam int page,
    @RequestParam int size,
    @RequestParam String sort,
    @RequestParam String keyword,
    ... ) { ... }
```

**Question:** How should you model the search input and the paginated response? Write both types.

---

## Question 7 — Audit Log Entry

Every time an expense changes state, your system writes an audit row: who changed it, when, old status, new status. Once written, the row never changes.

Common (wrong) approach:

```java
@Entity
public class AuditLog {
    @Id @GeneratedValue private Long id;
    private String changedBy;
    private LocalDateTime changedAt;
    private String oldStatus;
    private String newStatus;
    // setters allow the row to be mutated after creation
}
```

**Question:** Should `AuditLog` be an entity, a record, or both? How do you enforce that an audit entry cannot be altered after it is created?

---

## Question 8 — File Upload Metadata

A user uploads a file. You capture metadata: `filename`, `contentType`, `sizeInBytes`, `uploadedBy`, `uploadedAt`. This metadata travels from the controller to the service to the repository.

Common (wrong) approach:

```java
// Passing Spring's MultipartFile through all layers
public void process(MultipartFile file, String uploadedBy) { ... }
```

**Question:** Should `MultipartFile` cross into your service and repository layers? What should you create instead, and what type should it be?

---

## Question 9 — Money Transfer

A user transfers money: `fromAccount`, `toAccount`, `amount`. The request is validated (accounts exist, amount positive, sufficient balance), then a transfer row is inserted into the database.

Common (wrong) approach:

```java
public class TransferRequest {
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    // getters/setters allow the amount to be changed after validation
}
```

**Question:** What type should `TransferRequest` be? What about the `Transfer` row that gets saved to the database? Explain the difference.

---

## Question 10 — Internal Error Detail (Not for the Client)

When a database constraint fails, your service catches the exception and needs to carry an internal error code and a debug message to a logging service. This object is never serialized to JSON for the API caller.

Common (wrong) approach:

```java
public record ErrorDetail(String code, String debugMessage) {} // looks fine, but...
```

**Question:** Is a record appropriate here? When would a record be the wrong choice for an internal object that never leaves your service layer? Write the rule.

---

*Turn the page to `record-answer.md` for the efficient and safe answers.*
