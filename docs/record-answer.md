# Record vs DTO vs Bean — Answers

These answers focus on the **most efficient and safest** way, not the most popular way.  
Popular is not always safe. Popular often means "return the entity directly" or "use Lombok @Data for everything." That leaks database internals and allows accidental mutation.

Use this decision rule:

| If the data... | Use... |
|---|---|
| Arrives from the client or leaves to the client, and never changes after creation | **record** |
| Lives in the database and changes over time (draft → submitted → paid) | **bean / entity (JPA)** |
| Carries data between layers inside your app but is not saved directly | **DTO** (often a record) |

---

## Answer 1 — User Registration Request

**Correct choice: `record`**

The registration data arrives from the client, is validated once, and then handed to the service. It does not need to change after it arrives.

```java
public record RegisterRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password) {
}
```

**Why this is efficient and safe:**

- **Immutable:** Once the controller receives it, nothing can change the fields halfway through processing.
- **Concise:** No getters/setters/equals/hashCode boilerplate.
- **Fail-fast:** `@NotBlank` and `@Email` run before your method body executes. Invalid requests never reach your service.

**What NOT to do (popular but unsafe):**

```java
// Don't do this: a mutable class with setters
public class RegisterRequest {
    private String username;
    public void setUsername(String username) { this.username = username; } // can be changed later
}
```

A setter lets someone (or a future bug) change the username after validation but before saving.

---

## Answer 2 — User Profile Response (Security Leak)

**Correct choice: `record` as the response DTO. Never return the entity.**

```java
public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String role) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name());
    }
}

@GetMapping("/{id}")
public UserProfileResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return UserProfileResponse.from(user);
}
```

**Why this is efficient and safe:**

- **No password leak:** The `passwordHash` field never leaves your service layer.
- **No internal leak:** `createdAt` and database IDs you do not want exposed stay inside.
- **Stable API:** If you add a column to the `User` table tomorrow, the API response does not automatically change and break callers.

**What NOT to do (popular but unsafe):**

```java
// Don't do this: returning the entity directly
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow(); // passwordHash goes to the client!
}
```

Serializing the entity sends every field to the client, including secrets.

---

## Answer 3 — Updating a User Profile

**Request: `record`. Entity: bean/entity.**

```java
// The request arrives, is validated, and never changes during this request
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 500) String bio) {}

// The database row is mutable and has identity
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String displayName;   // changes over time

    private String bio;           // changes over time

    // getters/setters, protected no-arg constructor for JPA
}
```

Service usage:

```java
@Service
public class UserService {

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setDisplayName(request.displayName()); // entity mutates here
        user.setBio(request.bio());
        userRepository.save(user);
        return UserProfileResponse.from(user);
    }
}
```

**Why this is efficient and safe:**

- The **request** is a record because it is a snapshot of input for this one HTTP call. It does not make sense for the request to change mid-request.
- The **entity** is a bean because a user profile changes over its lifetime (displayName today, different bio next week). A record cannot model that lifecycle.

**What NOT to do:**

```java
// Don't make the User entity a record — records cannot model mutable state over time
public record User(Long id, String username, String displayName) { // wrong for an entity
    // You cannot call user.setDisplayName(...) on a record
}
```

---

## Answer 4 — Shopping Cart That Changes

**Cart: bean/entity. CartItem: record (if it is just a snapshot passed around).**

```java
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL)
    private List<CartItem> items = new ArrayList<>();

    public void addItem(Product product, int quantity) {
        items.add(new CartItem(this, product, quantity));
    }

    public void removeItem(Long itemId) {
        items.removeIf(item -> item.getId().equals(itemId));
    }

    // getters/setters
}

public record CartItemSnapshot(Long productId, String productName, int quantity) {
    // read-only view for the API response
}
```

**Why this is efficient and safe:**

- A **cart changes** (add, remove, update quantity). It has an identity and a lifecycle. Only a bean/entity can model that.
- A `CartItem` used purely as a read-only response snippet can be a record, but the mutable cart itself cannot.

**What NOT to do:**

```java
// Don't do this: immutable record for a mutable cart
public record Cart(Long id, List<CartItem> items) {
    // You cannot add or remove items. records have no setters.
}

// You would be forced to do this on every change:
cart = new Cart(cart.id(), newItems); // inefficient, breaks JPA identity
```

---

## Answer 5 — Application Configuration

**Correct choice: `record` (or an immutable final class).**

```java
@ConfigurationProperties(prefix = "db")
public record DbConfig(
        String url,
        String username,
        char[] password,   // char[] is safer than String for secrets
        int poolSize) {

    public DbConfig {
        Objects.requireNonNull(url);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        if (poolSize <= 0) throw new IllegalArgumentException("poolSize must be positive");
    }
}
```

**Why this is efficient and safe:**

- **Immutable after construction:** No runtime code can change the database URL or pool size.
- **char[] for passwords:** You can overwrite the array after use; a `String` stays in memory until garbage collected.
- **Validation in the compact constructor:** Invalid config fails at startup, not at runtime when a connection is needed.

**What NOT to do:**

```java
// Don't do this: mutable @Component with setters
@Component
public class DbConfig {
    private String url;
    public void setUrl(String url) { this.url = url; } // any bean can change this at runtime
}
```

Mutable configuration is a security and stability risk. Any component can silently change the database connection.

---

## Answer 6 — Paginated Product Search

**Input: `record`. Response: `record` wrapping a list.**

```java
// Search input — immutable criteria for this request
public record ProductSearchRequest(
        int page,
        int size,
        String sort,
        String keyword) {

    public ProductSearchRequest {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be 1-100");
    }
}

// Paginated response
public record ProductPageResponse(
        List<ProductDto> content,
        int totalElements,
        int totalPages,
        int currentPage) {}

// Controller
@GetMapping
public ProductPageResponse search(@ModelAttribute ProductSearchRequest request) {
    Page<Product> page = productService.search(request);
    return new ProductPageResponse(
            page.getContent().stream().map(ProductDto::from).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber());
}
```

**Why this is efficient and safe:**

- One object holds all search criteria. Adding a new filter later means adding one field, not changing every controller method signature.
- Validation of `page` and `size` happens once in the record constructor.
- The response record explicitly shapes what the client receives.

**What NOT to do:**

```java
// Don't pass 6+ individual parameters — hard to validate, hard to extend
@GetMapping
public List<Product> search(@RequestParam int page, @RequestParam int size, ...) { ... }
```

---

## Answer 7 — Audit Log Entry

**Entity in the database (bean), but treat it as immutable after write. Response DTO: `record`.**

```java
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String changedBy;

    private LocalDateTime changedAt;

    private String oldStatus;

    private String newStatus;

    // No setters for oldStatus/newStatus after construction
    // JPA uses field access or a protected no-arg constructor

    protected AuditLog() {} // JPA only

    public AuditLog(String changedBy, LocalDateTime changedAt,
                    String oldStatus, String newStatus) {
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}

// API response (read-only view)
public record AuditLogDto(
        Long id,
        String changedBy,
        LocalDateTime changedAt,
        String oldStatus,
        String newStatus) {

    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(), log.getChangedBy(), log.getChangedAt(),
                log.getOldStatus(), log.getNewStatus());
    }
}
```

**Why this is efficient and safe:**

- The **entity** is needed because audit rows are saved to the database.
- But you omit setters for historical fields so application code cannot rewrite history.
- The **DTO record** exposes the audit trail to callers without exposing the entity.

**What NOT to do:**

```java
// Don't make AuditLog a record if you are persisting it with JPA —
// JPA needs mutability or a specific model for entities.
// And don't add setters to oldStatus/newStatus — that allows falsifying history.
```

---

## Answer 8 — File Upload Metadata

**Correct choice: `record` for metadata. `MultipartFile` stays in the controller layer only.**

```java
public record FileMetadata(
        String filename,
        String contentType,
        long sizeInBytes,
        String uploadedBy) {}

// Controller — MultipartFile does not cross this boundary
@PostMapping("/upload")
public ResponseEntity<Void> upload(
        @RequestParam("file") MultipartFile file,
        Authentication authentication) {

    FileMetadata meta = new FileMetadata(
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            authentication.getName());

    fileStorageService.store(file.getInputStream(), meta);
    return ResponseEntity.ok().build();
}

@Service
public class FileStorageService {

    public void store(InputStream stream, FileMetadata meta) {
        // service never sees MultipartFile
    }
}
```

**Why this is efficient and safe:**

- **`MultipartFile` is tied to the web request.** Passing it into services makes them impossible to unit test without a mock servlet context and ties your business logic to Spring Web.
- A `record` metadata object is lightweight, serializable, and easy to test.

**What NOT to do:**

```java
// Don't do this: service depends on Spring Web
@Service
public class FileStorageService {
    public void store(MultipartFile file, String uploadedBy) { ... } // hard to unit test
}
```

---

## Answer 9 — Money Transfer

**Request: `record`. Database transfer row: bean/entity.**

```java
// Request — immutable input for one transfer attempt
public record TransferRequest(
        @NotBlank String fromAccount,
        @NotBlank String toAccount,
        @NotNull @Positive BigDecimal amount) {}

// Database row — mutable entity with lifecycle (pending → completed → failed)
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fromAccount;

    private String toAccount;

    private BigDecimal amount;

    private TransferStatus status = TransferStatus.PENDING; // changes over time

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    // getters/setters
}
```

**Why this is efficient and safe:**

- The **request** is a record because a transfer request is a single, frozen instruction. It should not change after validation.
- The **entity** is a bean because its status changes (pending → completed → failed). A record cannot represent that lifecycle.

**What NOT to do:**

```java
// Don't use BigDecimal in a mutable bean for the request and allow changes after validation
public class TransferRequest {
    private BigDecimal amount;
    public void setAmount(BigDecimal amount) { this.amount = amount; } // can be tampered after validation
}
```

---

## Answer 10 — Internal Error Detail

**Correct choice: `record` is fine for internal, short-lived objects — but only if they are truly immutable value objects. Do NOT use a record if the object needs state changes or identity.**

```java
public record ErrorDetail(String code, String debugMessage) {
    public ErrorDetail {
        Objects.requireNonNull(code);
        Objects.requireNonNull(debugMessage);
    }
}
```

**The rule:**

Use a record when the object is a **transparent data carrier** with no behavior beyond accessors.

Do **not** use a record when:

1. The object needs to change state after creation (use a bean/class).
2. The object has database identity and a lifecycle (use an entity).
3. The object needs complex behavior or invariants enforced by methods, not just constructor validation.

**Why this matters:**

Records are efficient because they are immutable and concise. But if you force a mutable concept into a record, you create awkward workarounds (copying the whole object on every change) that are less efficient and less safe than a simple class.

---

## Quick Reference Cheat Sheet

| Scenario | Type | Why |
|---|---|---|
| JSON request body | `record` | Immutable input, validated once |
| JSON response body | `record` | Stable shape, no internal leaks |
| Database table row | bean/entity | Mutable, has identity, changes over time |
| Internal config that never changes | `record` | Safe from runtime tampering |
| Search/filter criteria | `record` | Groups parameters, validates once |
| Object passed between service layers | `record` or DTO | Lightweight, no web framework coupling |
| Shopping cart, order, user profile | bean/entity | Changes state over its lifetime |
| Audit/history entry | entity + omit setters | Persisted, but historically immutable |

**The safety rule:** If you find yourself writing `setX()` on a record (by copying and recreating it constantly), you are using the wrong type. Use a bean.
