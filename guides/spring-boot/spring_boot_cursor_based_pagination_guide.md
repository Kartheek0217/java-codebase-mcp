# Spring Boot Pagination & Cursor-Based Pagination Guide

Handling large datasets efficiently is critical for modern web applications. Displaying millions of records at once leads to slow API response times, excessive server memory usage, and a terrible user experience. This guide covers how to implement robust pagination in Spring Boot using Spring Data JPA, exploring traditional offset-based pagination (`Page`, `Slice`) and high-performance cursor-based pagination for massive datasets.

---

## 1. Introduction & Benefits of Pagination

Pagination divides large datasets into smaller, manageable chunks (pages) so applications only retrieve and send the data required at any given moment.

### Key Benefits
- **Better Performance:** APIs respond significantly faster by querying and serializing less data per request.
- **Lower Memory Usage:** Applications consume less RAM and database resources by keeping object allocation strictly bounded.
- **Enhanced User Experience:** Client applications load quickly and enable smooth navigation.
- **Scalability:** System architecture can effortlessly handle millions of database rows without performance degradation.
- **Network Efficiency:** Considerably reduces payload sizes transmitted over the network.

---

## 2. Fundamental Pagination Concepts

- **Page Number:** The current page index. Spring Data JPA uses **0-based indexing** (Page 0 is the first page, Page 1 is the second, etc.).
- **Page Size:** The exact number of records requested per page (e.g., 10, 25, 50).
- **Offset:** The exact starting position in the dataset. For instance, on Page 2 with a Page Size of 10, the offset is 20 (skipping the first 20 records).
- **Total Elements:** The absolute total number of matching records in the database table.
- **Total Pages:** The total number of available pages, calculated as `ceil(Total Elements / Page Size)`.

---

## 3. Spring Data JPA Return Types: Page vs. Slice vs. List

Spring Data JPA provides three primary interfaces for returning paginated query results:

```text
+-------------------+---------------------------------------------------------+------------------------+
| Return Type       | Key Features                                            | Best Use Case          |
+-------------------+---------------------------------------------------------+------------------------+
| Page<T>           | Includes total record count, total pages, and navigation| Classic web dashboards |
|                   | metadata. Executes an extra COUNT() query on the DB.    | with page numbers.     |
+-------------------+---------------------------------------------------------+------------------------+
| Slice<T>          | Knows only if a next/previous page exists. Fetches      | Infinite scrolling (e.g|
|                   | N + 1 records without executing a COUNT() DB query.     | mobile feeds, socials).|
+-------------------+---------------------------------------------------------+------------------------+
| List<T>           | Raw list of entities matching limit/offset without any  | Internal batch processing|
|                   | pagination metadata.                                    | or lightweight APIs.   |
+-------------------+---------------------------------------------------------+------------------------+
```

> [!TIP]  
> Use `Page` when UI navigation requires explicit page numbers and total counts. Use `Slice` for infinite scroll feeds to save database query overhead.

---

## 4. Project Setup & Configuration

Ensure your `pom.xml` includes the necessary Spring Data JPA and database driver dependencies:

```xml
<dependencies>
    <!-- Spring Boot Data JPA Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- H2 Database (Runtime dependency for demonstration) -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

## 5. Implementing Traditional Offset-Based Pagination

### Step 1: Create the Entity

```java
package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String description;
    private Double price;
    
    public Product() {}
    
    public Product(String name, String description, Double price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
```

### Step 2: Create the Repository

The `JpaRepository` interface natively inherits methods supporting `Pageable`.

```java
package com.example.demo.repository;

import com.example.demo.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Standard pagination returning full Page metadata
    Page<Product> findAll(Pageable pageable);
    
    // Optimized pagination returning Slice metadata
    Slice<Product> findSliceBy(Pageable pageable);
    
    // Custom query method with pagination
    Page<Product> findByNameContaining(String name, Pageable pageable);
}
```

### Step 3: Create the Service Layer

```java
package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Basic Pageable request
    public Page<Product> getAllProducts(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return productRepository.findAll(pageable);
    }
    
    // Search with Pageable request
    public Page<Product> searchProducts(String name, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return productRepository.findByNameContaining(name, pageable);
    }

    // Pagination with dynamic Sorting
    public Page<Product> getAllProductsSorted(int pageNo, int pageSize, String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(direction, sortBy));
        return productRepository.findAll(pageable);
    }

    // Using Slice for optimized queries (no COUNT query)
    public Slice<Product> getAllProductsSlice(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        return productRepository.findSliceBy(pageable);
    }
}
```

### Step 4: Create the REST Controller

```java
package com.example.demo.controller;

import com.example.demo.entity.Product;
import com.example.demo.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProducts(
            @RequestParam(defaultValue = "0") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        
        try {
            Page<Product> page = productService.getAllProductsSorted(pageNo, pageSize, sortBy, sortDirection);
            
            Map<String, Object> response = new HashMap<>();
            response.put("products", page.getContent());
            response.put("currentPage", page.getNumber());
            response.put("totalItems", page.getTotalElements());
            response.put("totalPages", page.getTotalPages());
            response.put("hasNext", page.hasNext());
            response.put("hasPrevious", page.hasPrevious());
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/slice")
    public ResponseEntity<Map<String, Object>> getProductsSlice(
            @RequestParam(defaultValue = "0") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        try {
            Slice<Product> slice = productService.getAllProductsSlice(pageNo, pageSize);
            
            Map<String, Object> response = new HashMap<>();
            response.put("products", slice.getContent());
            response.put("hasNext", slice.hasNext());
            response.put("hasPrevious", slice.hasPrevious());
            // Note: No totalItems or totalPages available in Slice
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
```

---

## 6. The Problem with Large Datasets (Offset Fatigue)

Traditional offset-based pagination (`OFFSET X LIMIT Y`) suffers from massive performance degradation as datasets scale into millions of rows.

### Why Offset is Slow
When requesting Page 100 with 10 items per page (`OFFSET 990 LIMIT 10`), relational databases do not jump directly to row 991. Instead, the database engine **must scan and count the first 990 rows** before discarding them and returning the next 10. As the offset number grows, query response times increase linearly.

---

## 7. Implementing Cursor-Based Pagination

Cursor-based (or keyset) pagination solves the offset performance bottleneck. Instead of specifying an offset number, queries use a unique indexed marker (the "cursor", typically an incremental `id` or a high-precision timestamp) from the last seen record.

**How it works:** `SELECT * FROM products WHERE id > :cursor ORDER BY id ASC LIMIT :pageSize`

Because the query utilizes an index on the cursor column (`id`), the database seeks directly to the exact starting record instantly, regardless of table size.

### Step 1: Create the Cursor Response DTO

```java
package com.example.demo.dto;

import java.util.List;

public class CursorPageResponse<T> {
    private List<T> content;
    private Integer pageSize;
    private Long nextCursor;
    private Boolean hasNext;
    
    public CursorPageResponse(List<T> content, Integer pageSize, Long nextCursor, Boolean hasNext) {
        this.content = content;
        this.pageSize = pageSize;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
    
    public List<T> getContent() { return content; }
    public Integer getPageSize() { return pageSize; }
    public Long getNextCursor() { return nextCursor; }
    public Boolean getHasNext() { return hasNext; }
}
```

### Step 2: Custom Repository Query

```java
package com.example.demo.repository;

import com.example.demo.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Query("SELECT p FROM Product p WHERE (:cursor IS NULL OR p.id > :cursor) ORDER BY p.id ASC")
    List<Product> findNextPage(@Param("cursor") Long cursor, Pageable pageable);
}
```

### Step 3: Cursor Pagination Service Logic

To determine if a next page exists without a count query, request `pageSize + 1` records. If the returned list size exceeds `pageSize`, `hasNext` is true.

```java
package com.example.demo.service;

import com.example.demo.dto.CursorPageResponse;
import com.example.demo.entity.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    public CursorPageResponse<Product> getProductsCursorPagination(Long cursor, int pageSize) {
        // Fetch pageSize + 1 to efficiently determine if there is a next page
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        
        List<Product> products = productRepository.findNextPage(cursor, pageable);
        
        boolean hasNext = products.size() > pageSize;
        
        // Remove the extra peeked record before returning to client
        if (hasNext) {
            products = products.subList(0, pageSize);
        }
        
        Long nextCursor = products.isEmpty() ? null : products.get(products.size() - 1).getId();
        
        return new CursorPageResponse<>(products, pageSize, nextCursor, hasNext);
    }
}
```

### Step 4: Cursor REST Endpoint

```java
package com.example.demo.controller;

import com.example.demo.dto.CursorPageResponse;
import com.example.demo.entity.Product;
import com.example.demo.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class CursorProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponse<Product>> getProductsCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        try {
            CursorPageResponse<Product> response = productService.getProductsCursorPagination(cursor, pageSize);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
```

---

## 8. Summary & Comparison

| Metric / Feature | Offset Pagination (`Page`) | Offset Pagination (`Slice`) | Cursor Pagination |
| :--- | :--- | :--- | :--- |
| **DB Query Efficiency** | Moderate (Runs COUNT query) | Good (No COUNT query) | **Excellent** (Direct index seek) |
| **Large Table Scalability** | Poor (Degrades at deep offsets) | Poor (Degrades at deep offsets) | **Exceptional** (Constant time $O(1)$) |
| **Random Page Jumping** | Supported (e.g., jump to page 50) | Not supported (Sequential only) | Not supported (Sequential only) |
| **Real-time Data Stability**| Susceptible to missing/duplicate items on insert/delete | Susceptible to missing/duplicate items | **Stable** (Immune to page drift) |
| **Best Used For** | Admin tables with page numbers | Small/Medium infinite scroll feeds | Massive data streams & high-load APIs |
