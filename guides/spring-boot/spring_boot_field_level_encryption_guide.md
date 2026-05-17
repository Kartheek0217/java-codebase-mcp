# Spring Boot JPA Field-Level Encryption Guide

When securing enterprise database architectures against data breaches, encrypting entire disk volumes (Transparent Data Encryption — TDE) protects against physical drive theft but provides zero protection against SQL injection or unauthorized database access. Once an attacker gains access to the running database instance or unencrypted SQL dumps, all data is exposed in plain text.

**Field-Level Encryption (FLE)** addresses this vulnerability by encrypting only highly sensitive individual entity attributes (such as Social Security Numbers, Credit Card details, or medical records) at the application runtime layer before the payload ever reaches the database driver.

```text
+---------------------------------------------------------------------------------------+
|                       Transparent JPA Field-Level Encryption Flow                     |
+---------------------------------------------------------------------------------------+
| [ Application Memory ]                [ JPA Converter Layer ]        [ Database Disk ]|
| Entity: Customer                      @Convert(converter=...)        Table: customer  |
| - ssn = "123-45-6789" (Plain)  --->   convertToDatabaseColumn() ---> ssn = "7FhGhsd7="|
|                                                                                       |
| Entity: Customer                      @Convert(converter=...)        Table: customer  |
| - ssn = "123-45-6789" (Plain)  <---   convertToEntityAttribute() <-- ssn = "7FhGhsd7="|
+---------------------------------------------------------------------------------------+
```

---

## 1. Architectural Comparison of Encryption Scopes

```text
+--------------------+------------------------------------+-------------------------------------+
| Encryption Scope   | Architectural Security Target      | Performance & Operational Impact    |
+--------------------+------------------------------------+-------------------------------------+
| Volume TDE         | Entire physical disk / storage     | Zero app overhead; no SQLi defense. |
| Table / Column     | DB engine level encryption         | High DB CPU overhead; key tied to DB|
| Field-Level (FLE)  | Specific entity attributes in JVM  | Minimal CPU overhead; full app control|
+--------------------+------------------------------------+-------------------------------------+
```

### 🎯 Primary Domain Use Cases
- **Personally Identifiable Information (PII):** Social Security Numbers (SSN), Government IDs, Dates of Birth.
- **Financial & Banking Records:** Credit Card Numbers (PAN), Bank Account Routing codes.
- **Protected Health Information (PHI):** HIPAA Medical diagnostic codes, Patient treatment histories.

---

## 2. Spring Boot Implementation Patterns

```text
+-----------------------+---------------------------------------------------------------+
| Implementation Pattern| Architectural Characteristics                                 |
+-----------------------+---------------------------------------------------------------+
| Manual Service Crypt  | Explicit AES calls inside service layer; high boilerplate.    |
| JPA @Converter        | Automated, transparent entity attribute conversion.           |
| Hibernate Interceptors| Global entity lifecycle event hooks; advanced multi-tenancy.  |
+-----------------------+---------------------------------------------------------------+
```

---

## 3. Complete JPA `@Converter` Implementation

### 🔐 1. Cryptographic Utility (`AESUtil.java`)

```java
package com.example.demo.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AESUtil {

    private static final String ALGORITHM = "AES";
    // 16-byte key for AES-128 (In production, inject securely via KMS / Vault)
    private static final String KEY = "MySecretKey12345"; 

    private AESUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static String encrypt(String data) {
        if (data == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Cryptographic encryption failure", e);
        }
    }

    public static String decrypt(String encryptedData) {
        if (encryptedData == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            return new String(cipher.doFinal(decodedBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cryptographic decryption failure", e);
        }
    }
}
```

---

### 🔄 2. JPA Attribute Converter (`EncryptionConverter.java`)

```java
package com.example.demo.security.converter;

import com.example.demo.security.crypto.AESUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return AESUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return AESUtil.decrypt(dbData);
    }
}
```

---

### 🏛️ 3. Secured Domain Entity (`Customer.java`)

```java
package com.example.demo.domain;

import com.example.demo.security.converter.EncryptionConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain text: Non-sensitive data remains unencrypted for fast querying
    private String name;  

    // Highly sensitive: Will be transparently encrypted before DB insertion
    @Convert(converter = EncryptionConverter.class)
    private String ssn;   

    // Highly sensitive: Stored as Base64 encrypted cipher in DB
    @Convert(converter = EncryptionConverter.class)
    private String creditCard;  

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }
    public String getCreditCard() { return creditCard; }
    public void setCreditCard(String creditCard) { this.creditCard = creditCard; }
}
```

---

### 📦 4. Spring Data Repository (`CustomerRepository.java`)

```java
package com.example.demo.repository;

import com.example.demo.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
```

---

### 🚀 5. Execution Runner (`DemoRunner.java`)

```java
package com.example.demo.runner;

import com.example.demo.domain.Customer;
import com.example.demo.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoRunner implements CommandLineRunner {

    private final CustomerRepository repo;

    public DemoRunner(CustomerRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setSsn("123-45-6789");
        customer.setCreditCard("4111111111111111");

        // 1. Entity is persisted; JPA executes convertToDatabaseColumn()
        repo.save(customer);
        System.out.println("Persisted to DB (Encrypted in storage): " + customer.getId());

        // 2. Entity is fetched; JPA executes convertToEntityAttribute()
        Customer fetchedCustomer = repo.findById(customer.getId()).orElseThrow();
        System.out.println("Fetched from DB (Decrypted transparently in JVM): " + fetchedCustomer.getSsn());
    }
}
```

---

## 4. Database Table Inspection

When inspecting the underlying relational database table directly via SQL client, sensitive plain text attributes are entirely masked:

```text
+----+-----------+------------------------------+------------------------------+
| id | name      | ssn                          | credit_card                  |
+----+-----------+------------------------------+------------------------------+
| 1  | John Doe  | 7FhGhsd7a8xYz2=              | KJH78sdhJshd9pKq1=           |
+----+-----------+------------------------------+------------------------------+
```

---

## 5. Architectural Trade-Offs

```text
+---------------------------------------+---------------------------------------+
| Architectural Pros                    | Architectural Cons                    |
+---------------------------------------+---------------------------------------+
| [x] Extreme CPU Efficiency            | [x] Hard / Impossible SQL Indexing    |
| [x] Clean Code via @Converter         | [x] Key Rotation & Storage Management |
| [x] Database Vendor Agnostic          | [x] Base64 Column Storage Overhead    |
+---------------------------------------+---------------------------------------+
```

### 🚨 The Querying & Indexing Trap
Because encrypted values produce randomized cipher output (especially when using secure initialization vectors), executing direct SQL WHERE clauses on encrypted fields is impossible:

```sql
-- ❌ Fails completely: DB cannot match unencrypted plain text against Base64 ciphers
SELECT * FROM customers WHERE ssn = '123-45-6789';
```

> [!TIP]  
> **Blind Indexing Solution:** If querying on an encrypted field is strictly required, compute a secure, salted SHA-256 hash of the plain text field and store it in an unencrypted index column (`ssn_hash`). Execute SQL queries against the hash column rather than the encrypted payload column.

---

## 6. Production Best Practices

```text
[ ] 1. Key Management   : Never hardcode encryption keys; inject via AWS KMS or HashiCorp Vault.
[ ] 2. Cryptographic Alg: Upgrade from AES-128 (ECB) to AES-256 (GCM mode with non-repeating IVs).
[ ] 3. Query Boundaries : Never apply @Convert to fields frequently used in sorting or range queries.
[ ] 4. Key Rotation     : Implement automated key rotation schedules and multi-key decryption support.
```
