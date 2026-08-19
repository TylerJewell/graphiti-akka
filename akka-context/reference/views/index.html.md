<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [Views](index.html)

<!-- </nav> -->

# Views

This reference guide provides comprehensive documentation for Akka Views and the View query language, which enables you to query and filter data from your Views using SQL-like syntax.

## <a href="about:blank#_introduction_to_view_queries"></a> Introduction to View queries

Akka Views provide a flexible way to query entities by attributes other than their entity ID. Views allow you to create specialized read models that are optimized for specific access patterns and queries.

The View query language is designed to be:

- **Familiar** - Uses SQL-like syntax that should be intuitive to most developers
- **Powerful** - Supports filtering, joining, sorting, and creating complex data structures
- **Flexible** - Adapts to various data access patterns and requirements
- **Efficient** - Creates appropriate indexes based on your query patterns

## <a href="about:blank#_getting_started"></a> Getting started

A basic View query has the following structure:

```sql
SELECT <select_expressions>
FROM <table_name>
WHERE <filter_conditions>
```
For example:

```sql
SELECT * FROM customers
WHERE region = 'Europe' AND active = true
ORDER BY name
LIMIT 10
```
This query returns up to 10 active customers from the Europe region, ordered by name.

## <a href="about:blank#_using_this_reference"></a> Using this reference

This reference guide is organized into two main sections:

### <a href="about:blank#_view_query_syntax_reference"></a> View query syntax reference

The [View query syntax](syntax/index.html) section documents the language elements, operators, and functions that make up the View query language:

- **Core Clauses** - SELECT, FROM, WHERE, etc.
- **Operators** - Comparison, logical, and special operators
- **Functions** - Aggregation, pagination, and utility functions
Start with the [Query](syntax/query.html) page for a complete overview of the query structure.

### <a href="about:blank#_view_concepts_reference"></a> View concepts reference

The [View concepts](concepts/index.html) section explains the fundamental concepts and patterns for working with Views:

- **Data Structure** - How data is organized and accessed in Views
- **Data Types** - Type system and mapping between Java and query language
- **Advanced Features** - Complex querying capabilities and patterns

## <a href="about:blank#_common_use_cases"></a> Common use cases

### <a href="about:blank#_filtering_data"></a> Filtering data

Filter entities based on field values:

```sql
SELECT * FROM products
WHERE category = 'Electronics' AND price < 1000
```

### <a href="about:blank#_accessing_nested_fields"></a> Accessing nested fields

Access fields within nested objects:

```sql
SELECT * FROM customers
WHERE address.country = 'USA' AND address.state = 'California'
```

### <a href="about:blank#_joining_related_data"></a> Joining related data

Combine data from multiple tables:

```sql
SELECT c.name, o.id, o.amount
FROM customers AS c
JOIN orders AS o ON o.customerId = c.id
WHERE c.id = :customerId
```

### <a href="about:blank#_creating_nested_structures"></a> Creating nested structures

Build hierarchical data structures:

```sql
SELECT
  category,
  collect(*) AS products
FROM products
GROUP BY category
```

### <a href="about:blank#_pagination"></a> Pagination

Implement pagination for large result sets:

```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```

## <a href="about:blank#_view_implementation_in_java"></a> View implementation in Java

Views are implemented in Java by extending the `akka.javasdk.view.View` class and defining:

1. **Table updaters** - Define how entity events or state changes update the view
2. **Query methods** - Define the queries that can be executed against the view
Here is a simple example:

```java
@Component(id = "customers-view")
public class CustomerView extends View {

  @Consume.FromKeyValueEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> { }

  @Query("SELECT * FROM customers WHERE region = :region ORDER BY name")
  public QueryEffect<CustomerList> getCustomersByRegion(String region) {
    return queryResult();
  }

  public record CustomerList(List<Customer> customers) { }
}
```
For detailed information, see [implementing Views in your application](../../sdk/views.html).

## <a href="about:blank#_related_documentation"></a> Related documentation

- [Implementing Views](../../sdk/views.html) - Comprehensive guide to implementing Views
- [Advanced Views](concepts/advanced-views.html) - Advanced usage patterns and features

<!-- <footer> -->
<!-- <nav> -->
[Policies](../specify/policies.html) [View query syntax](syntax/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->