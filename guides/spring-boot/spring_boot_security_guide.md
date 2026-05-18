# Spring Boot Security & Data Protection Guide

This guide establishes production standards for Spring Boot application security: implementing transparent JPA field-level encryption, securing sensitive personally identifiable information (PII) at rest, and leveraging blind indexing for searching encrypted payloads.

---

## 1. Transparent JPA Field-Level Encryption (`AttributeConverter`)

Enterprise regulatory frameworks (GDPR, HIPAA, PCI-DSS) require strict data-at-rest encryption for sensitive customer fields (SSN, medical records, billing data). Encrypting data inside application controllers or database triggers is difficult to maintain and prone to leakage.

```text
+---------------------------------------------------------------------------------------+
|                      Transparent Converter Pipeline Architecture                      |
+---------------------------------------------------------------------------------------+
| [ Entity Field: "123-456-789" ]                                                       |
|             |                                                                         |
|             v (AttributeConverter.convertToDatabaseColumn)                            |
| [ AES-GCM Encrypted Blob: "0x89A1B2C..." ] ---> Stored in Database Column             |
|                                                                                       |
| DB Query ---> (AttributeConverter.convertToEntityAttribute) ---> Cleartext Entity Field|
+---------------------------------------------------------------------------------------+
```

### The Encryption Converter Implementation
Implementing `AttributeConverter` intercepts entity mapping during JPA persistence operations, ensuring plain text values never touch the database disk or logs.

```java
@Component
public class CryptoService {
    private final SecretKey key; // Loaded securely from AWS KMS / HashiCorp Vault

    public CryptoService(@Value("${app.crypto.secret-key}") String secret) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(secret), "AES");
    }

    public String encrypt(String raw) { /* AES-GCM Encryption */ return "ENC:" + raw; }
    public String decrypt(String encrypted) { /* AES-GCM Decryption */ return encrypted.replace("ENC:", ""); }
}

@Converter
@Component
@RequiredArgsConstructor
public class EncryptedFieldConverter implements AttributeConverter<String, String> {
    private final CryptoService crypto;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : crypto.decrypt(dbData);
    }
}
```

### Entity Mapping
```java
@Entity
@Table(name = "customer_records")
public class CustomerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    // Guaranteed transparent encryption/decryption at the JPA boundary
    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "social_security_number")
    private String ssn;
}
```

---

## 2. Searching Encrypted Data (Blind Indexing)

Because standard encryption schemes like AES-GCM produce completely randomized ciphertext on every run (due to cryptographic initialization vectors), executing standard SQL lookups (`WHERE ssn = 'ENC:123'`) fails.

### The Blind Index Pattern
To enable equality searches on encrypted data without compromising security, compute a deterministic, salted cryptographic hash (Blind Index) of the plaintext and store it in a dedicated indexed column.

```java
@Entity
@Table(name = "customer_records", indexes = @Index(name = "idx_ssn_hash", columnList = "ssn_hash"))
public class CustomerRecord {

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "social_security_number")
    private String ssn;

    // Blind index: SHA-256(HMAC_SALT + ssn)
    @Column(name = "ssn_hash", nullable = false, updatable = false)
    private String ssnHash;

    public void setSsn(String ssn, BlindIndexService indexer) {
        this.ssn = ssn;
        this.ssnHash = indexer.computeHash(ssn);
    }
}
```

### Repository Search Query
```java
public interface CustomerRecordRepository extends JpaRepository<CustomerRecord, Long> {
    // High-speed B-Tree index lookup on deterministic blind index!
    Optional<CustomerRecord> findBySsnHash(String ssnHash);
}
```

---

## 3. Security Verification Checklist

```text
[ ] 1. PII Auditing      : Verify all entities storing sensitive personal data map fields using `@Convert`.
[ ] 2. Blind Indexing    : Ensure encrypted columns requiring query searchability utilize deterministic blind index hashes.
```
