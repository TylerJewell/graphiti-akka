<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [Views](../../index.html)
- [View query syntax](../index.html)
- [Functions](index.html)

<!-- </nav> -->

# Functions

This page provides an overview of all functions available in the View query language. Functions perform operations on data and return results that can be used in queries.

## <a href="about:blank#_aggregation_functions"></a> Aggregation Functions

Aggregation functions perform calculations across groups of rows.

| Function | Description | Example |
| --- | --- | --- |
| <a href="collect.html">`collect([distinct`</a> `expression)` ] | Creates a collection of values from multiple rows | `collect(name) AS productNames` |
| <a href="count.html">`count(*)`</a> | Counts the number of matching rows | `count(*) AS totalCustomers` |

## <a href="about:blank#_pagination_functions"></a> Pagination Functions

Functions that support pagination and result set management.

| Function | Description | Example |
| --- | --- | --- |
| <a href="next-page-token.html">`next_page_token()`</a> | Generates a token for retrieving the next page of results | `next_page_token() AS nextPageToken` |
| <a href="page-token-offset.html">`page_token_offset(token)`</a> | Uses a token to determine the starting position for pagination | `OFFSET page_token_offset(:pageToken)` |
| <a href="has-more.html">`has_more()`</a> | Indicates if more results exist beyond the current page | `has_more() AS hasMoreResults` |
| <a href="total-count.html">`total_count()`</a> | Returns the total count of rows matching the query | `total_count() AS totalCount` |

## <a href="about:blank#_text_search_functions"></a> Text Search Functions

Functions for advanced text searching capabilities.

| Function | Description | Example |
| --- | --- | --- |
| <a href="text-search.html">`text_search(column, query, [config])`</a> | Performs language-aware text search | `text_search(description, :searchText, 'english')` |

## <a href="about:blank#_function_usage"></a> Function Usage

Functions can be used in different parts of a query:

- In the `SELECT` clause to compute values for the result
- With the `OFFSET` clause for token-based pagination
- In the `WHERE` clause for filtering (text_search only)

## <a href="about:blank#_function_categories_by_return_type"></a> Function Categories by Return Type

### <a href="about:blank#_boolean_functions"></a> Boolean Functions

- `has_more()` - Returns true if more results exist beyond the current page
- `text_search()` - Returns true if the text contains the search terms

### <a href="about:blank#_numeric_functions"></a> Numeric Functions

- `count(*)` - Returns the number of rows
- `total_count()` - Returns the total number of matching rows

### <a href="about:blank#_string_functions"></a> String Functions

- `next_page_token()` - Returns a string token for pagination

### <a href="about:blank#_collection_functions"></a> Collection Functions

- `collect()` - Returns a collection of values from multiple rows

## <a href="about:blank#_related_features"></a> Related Features

- [SELECT clause](../select.html) - Using functions in result projections
- [Paging](../../concepts/pagination.html) - Using pagination functions
- [WHERE clause](../where.html) - Using functions in filters
- [Result Mapping](../../concepts/result-mapping.html) - How function results map to Java types

<!-- <footer> -->
<!-- <nav> -->
[IS NULL/IS NOT NULL](../operators/is-null.html) [collect()](collect.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->