<!-- <nav> -->
- [Akka](../../../../index.html)
- [Reference](../../../index.html)
- [Views](../../index.html)
- [View query syntax](../index.html)
- [Operators](index.html)

<!-- </nav> -->

# Operators

This page provides an overview of all operators available in the View query language. Operators are special symbols or keywords that perform operations on values and return results.

## <a href="about:blank#_comparison_operators"></a> Comparison Operators

Comparison operators compare values and return boolean results.

| Operator | Description | Example |
| --- | --- | --- |
| <a href="comparison.html#equals">`=`</a> | Equals | `name = 'John'` |
| <a href="comparison.html#not-equals">`!=`</a> | Not equals | `category != 'Electronics'` |
| <a href="comparison.html#greater-than">`>`</a> | Greater than | `price > 100` |
| <a href="comparison.html#greater-equals">`>=`</a> | Greater than or equal to | `price >= 100` |
| <a href="comparison.html#less-than">`<`</a> | Less than | `price < 50` |
| <a href="comparison.html#less-equals">`⇐`</a> | Less than or equal to | `price ⇐ 50` |
| <a href="is-null.html">`IS NULL`</a> | Tests if a value is NULL (missing) | `phoneNumber IS NULL` |
| <a href="is-null.html">`IS NOT NULL`</a> | Tests if a value is not NULL (present) | `email IS NOT NULL` |

## <a href="about:blank#_logical_operators"></a> Logical Operators

Logical operators combine boolean expressions.

| Operator | Description | Example |
| --- | --- | --- |
| <a href="logical.html#and">`AND`</a> | Logical AND - true if both conditions are true | `category = 'Books' AND price < 20` |
| <a href="logical.html#or">`OR`</a> | Logical OR - true if either condition is true | `category = 'Books' OR category = 'Magazines'` |
| <a href="logical.html#not">`NOT`</a> | Logical NOT - negates a condition | `NOT price > 100` |

## <a href="about:blank#_set_membership_operators"></a> Set Membership Operators

Set membership operators check if a value is part of a set.

| Operator | Description | Example |
| --- | --- | --- |
| <a href="in.html">`IN`</a> | Tests if a value matches any value in a list | `category IN ('Books', 'Magazines', 'Comics')` |
| <a href="any.html">`= ANY`</a> | Tests if a value matches any element in an array | `tag = ANY(tags)` or `:tag = ANY(tags)` |

## <a href="about:blank#_pattern_matching_operators"></a> Pattern Matching Operators

Pattern matching operators test if a string matches a pattern.

| Operator | Description | Example |
| --- | --- | --- |
| <a href="like.html">`LIKE`</a> | Pattern matching with wildcards | `name LIKE 'Jo%'` |

## <a href="about:blank#_operator_precedence"></a> Operator Precedence

Operators are evaluated in the following order of precedence (from highest to lowest):

1. Parentheses `()`
2. Unary operators (`NOT`)
3. Comparison operators (`=`, `!=`, `<`, `>`, etc.)
4. `AND`
5. `OR`
Use parentheses to override the default precedence.

## <a href="about:blank#_related_features"></a> Related Features

- [WHERE clause](../where.html) - Using operators in query filters
- [JOIN](../join.html) - Using operators in join conditions
- [Data Types](../../concepts/data-types.html) - Type information for operators

<!-- <footer> -->
<!-- <nav> -->
[JOIN](../join.html) [Comparison operators](comparison.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->