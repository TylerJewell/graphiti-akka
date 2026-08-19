<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [Views](../index.html)
- [View query syntax](index.html)

<!-- </nav> -->

# View query syntax

The View query language provides a SQL-like syntax for querying and retrieving data from your views. This page provides an overview of the language features and serves as an entry point to the detailed syntax documentation.

## <a href="about:blank#_overview"></a> Overview

The View query language enables you to:

- Select specific data fields or entire objects
- Filter results based on conditions
- Join data from multiple tables
- Sort and limit results
- Create hierarchical data structures
- Paginate large result sets

## <a href="about:blank#_core_query_components"></a> Core query components

For the complete query structure and syntax, see [Query](query.html).

### <a href="about:blank#_query_structure"></a> Query structure

- [Query](query.html) - Complete query structure and composition
- [SELECT](select.html) - Specifying data to retrieve
- [FROM](from.html) - Specifying the data source
- [WHERE](where.html) - Filtering results
- [AS](as.html) - Creating aliases
- [ORDER BY](order-by.html) - Sorting results
- [GROUP BY](group-by.html) - Grouping related data
- [JOIN](join.html) - Combining data from multiple tables

### <a href="about:blank#_operators"></a> Operators

- [Operators overview](operators/index.html) - All available operators
- [Comparison operators](operators/comparison.html) - =, !=, >, <, etc.
- [Logical operators](operators/logical.html) - AND, OR, NOT
- [IN](operators/in.html) - Testing membership in a list
- [= ANY](operators/any.html) - Testing membership in an array
- [LIKE](operators/like.html) - Pattern matching
- [IS NULL / IS NOT NULL](operators/is-null.html) - Testing for null values

### <a href="about:blank#_functions"></a> Functions

- [Functions overview](functions/index.html) - All available functions
- [collect()](functions/collect.html) - Creating collections from grouped rows
- [count(*)](functions/count.html) - Counting rows
- [DISTINCT](functions/distinct.html) - Removing duplicates
- [text_search()](functions/text-search.html) - Advanced text searching

### <a href="about:blank#_pagination"></a> Pagination

- [Pagination](../concepts/pagination.html) - Pagination approaches
- [OFFSET](offset.html) - Skipping results
- [LIMIT](limit.html) - Limiting result count

## <a href="about:blank#_example_queries"></a> Example queries

Here are a few examples of common query patterns:

Basic query with filtering
```sql
SELECT * FROM products
WHERE category = 'Electronics' AND price < 500
ORDER BY price ASC
```
Query with pagination
```sql
SELECT * AS products, next_page_token() AS nextPageToken
FROM products
OFFSET page_token_offset(:pageToken)
LIMIT 10
```
Query with grouping and collection
```sql
SELECT category, collect(*) AS products
FROM products
GROUP BY category
```
Start with the [Query](query.html) reference for more examples and detailed syntax information.

## <a href="about:blank#_related_concepts"></a> Related concepts

- [Data types](../concepts/data-types.html) - Types supported in views
- [Result mapping](../concepts/result-mapping.html) - How queries map to Java types
- [Table updaters](../concepts/table-updaters.html) - Defining view tables

<!-- <footer> -->
<!-- <nav> -->
[Views](../index.html) [Query](query.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->