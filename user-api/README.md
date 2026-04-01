# User API

## Overview

Domain API module for user identity and management in the hexagonal architecture. Defines contracts for user lookup, listing, and admin status checking—all technology-agnostic.

## Purpose

Defines contracts for:
- User lookup by subject identifier (email or anonymous ID)
- User lookup by internal UUID
- User listing with pagination
- Admin privilege checking

## Architecture Role

**Hexagonal Layer**: Ports (Contracts)
- **Inbound Ports**: Use cases for user operations
- **Outbound Ports**: Repository SPI for user persistence

## Module Structure

```
user-api/
├── port/
│   ├── inbound/
│   │   └── UserUseCase.java
│   └── outbound/
│       └── UserRepositoryPort.java
└── dto/
    └── UserDTO.java
```

## Inbound Ports (Use Cases)

Located in `port/inbound/`:

### UserUseCase
```java
public interface UserUseCase {
    /**
     * Find user by subject identifier (email or anon_*)
     */
    Optional<UserDTO.UserResponse> findBySub(String sub);

    /**
     * Find user by internal UUID
     */
    Optional<UserDTO.UserResponse> findById(UUID id);

    /**
     * List all users with pagination
     */
    UserDTO.PagedUsersResponse listUsers(int offset, int limit);

    /**
     * Check if user has admin privileges
     */
    UserDTO.AdminStatusResponse isAdmin(UUID userId);
}
```

## Outbound Ports (Persistence)

Located in `port/outbound/`:

### UserRepositoryPort
```java
public interface UserRepositoryPort {
    /**
     * Find user by subject identifier
     */
    Optional<User> findBySub(String sub);

    /**
     * Find user by UUID
     */
    Optional<User> findById(UUID id);

    /**
     * List users with pagination
     */
    List<User> findAll(int offset, int limit);

    /**
     * Count total users
     */
    long countAll();
}
```

**Implementations:**
- JPA with PostgreSQL
- MongoDB
- DynamoDB
- In-memory (for testing)

## DTOs

Located in `dto/`:

### UserDTO

**UserResponse:**
```java
record UserResponse(
    UUID id,
    String sub,             // Email or "anon_<uuid>"
    String email,           // Nullable for anonymous users
    String name,            // Display name
    String userType,        // "REGISTERED", "ADMIN", "ANONYMOUS"
    Instant createdAt
)
```

**AdminStatusResponse:**
```java
record AdminStatusResponse(boolean isAdmin)
```

**PagedUsersResponse:**
```java
record PagedUsersResponse(
    List<UserResponse> users,
    long total,
    int offset,
    int limit
)
```

## Domain Model

**User Entity** (internal, not exposed via API):
```java
class User {
    UUID id;
    String sub;           // Unique identifier
    String email;         // Nullable for anonymous
    String name;
    String passwordHash;  // Nullable for anonymous
    UserType userType;    // Enum: REGISTERED, ADMIN, ANONYMOUS
    Instant createdAt;
    Instant lastLoginAt;
}
```

## Subject Identifier Pattern

### Registered Users
- **sub**: User's email address (e.g., `john@example.com`)
- **email**: Same as sub
- **userType**: `REGISTERED`

### Anonymous Users
- **sub**: Generated UUID prefixed with `anon_` (e.g., `anon_550e8400-e29b-41d4-a716-446655440000`)
- **email**: `null`
- **userType**: `ANONYMOUS`

### Admin Users
- **sub**: Admin's email address
- **email**: Admin's email
- **userType**: `ADMIN`

## Dependencies

```xml
<!-- Self-contained module -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

**No** dependencies on other domain APIs.

## Hexagonal Architecture Patterns

### 1. Independent Bounded Context

User domain is completely isolated:
- Zero dependencies on itinerary, geo, or AI domains
- Can be deployed as standalone microservice
- Clear separation of identity concerns

### 2. Simple Repository Pattern

```
Application Service (implements UserUseCase)
    ↓ depends on
UserRepositoryPort (interface)
    ↑ implemented by
JPA/MongoDB/DynamoDB Adapter
```

### 3. DTO Projection

Use case returns DTOs, not domain entities:
- Prevents leaking `passwordHash` to API layer
- Allows different representations for different consumers
- Clean separation between domain and API models

### 4. Technology Agnostic

Repository port is pure Java:
- No JPA annotations
- No Spring Data interfaces
- No database-specific types

## Usage by Other Modules

### Domain Implementations

**user-impl-core**: Business logic implementing `UserUseCase`

**user-impl-jpa**: JPA adapter implementing `UserRepositoryPort`

**user-impl-grpc-server**: gRPC server exposing user operations

### Cross-Domain Integration

**itinerary-api** → **AuthPort**:
```java
AuthDTO.AuthStatus getCurrentUserStatus();
```

Auth port returns:
- Current user's UUID
- User type (REGISTERED, ADMIN, ANONYMOUS)
- Subject identifier

**Implementation** (in monolith):
```java
@Component
class AuthPortAdapter implements AuthPort {
    private final UserUseCase userUseCase;

    @Override
    public AuthDTO.AuthStatus getCurrentUserStatus() {
        String sub = SecurityContextHolder.getContext().getAuthentication().getName();
        UserDTO.UserResponse user = userUseCase.findBySub(sub).orElseThrow();
        return new AuthDTO.AuthStatus(user.id(), user.userType(), user.sub());
    }
}
```

### Application Assembly

**app-monolith**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>user-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>user-impl-jpa</artifactId>
</dependency>
<!-- In-process user management -->
```

**app-microservice-user**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>user-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>user-impl-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>user-impl-grpc-server</artifactId>
</dependency>
<!-- Standalone user microservice -->
```

## Authentication Flow

### Registration
```
1. Client POST /api/users/register
   ↓
2. UserUseCase.register(email, password, name)
   ↓
3. UserRepositoryPort.save(user)
   ↓
4. Return JWT with sub=email
```

### Anonymous Login
```
1. Client GET /api/users/anonymous
   ↓
2. UserUseCase.createAnonymous()
   ├─ Generate UUID
   ├─ Create sub = "anon_" + uuid
   └─ UserRepositoryPort.save(user)
   ↓
3. Return JWT with sub=anon_<uuid>
```

### Authenticated Request
```
1. Client sends JWT in Authorization header
   ↓
2. Spring Security extracts sub from JWT
   ↓
3. UserUseCase.findBySub(sub)
   ↓
4. User loaded into SecurityContext
```

## Admin Access Control

**Check Admin Status:**
```java
UserDTO.AdminStatusResponse status = userUseCase.isAdmin(userId);
if (status.isAdmin()) {
    // Allow admin operation
}
```

**Upgrade User to Admin:**
```java
// Implementation detail (not in API contract)
user.setUserType(UserType.ADMIN);
userRepository.save(user);
```

## Pagination Example

```java
// List first 20 users
PagedUsersResponse page1 = userUseCase.listUsers(0, 20);

// List next 20 users
PagedUsersResponse page2 = userUseCase.listUsers(20, 20);

// Calculate total pages
int totalPages = (int) Math.ceil((double) page1.total() / 20);
```

## Security Considerations

### Password Hashing
- Never expose `passwordHash` via DTOs
- Use BCrypt or Argon2 for hashing
- Hash verification happens in impl layer, not API

### Subject Uniqueness
- `sub` field has unique constraint in database
- Prevents duplicate email registrations
- Anonymous users guaranteed unique via UUID

### JWT Claims
```json
{
  "sub": "john@example.com",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_type": "REGISTERED",
  "exp": 1735689600
}
```

## Future Enhancements

Potential additional operations:
- **UpdateUserUseCase**: Update profile information
- **DeleteUserUseCase**: GDPR-compliant user deletion
- **UserPreferencesPort**: Store travel preferences
- **UserActivityPort**: Track login history, itinerary views
