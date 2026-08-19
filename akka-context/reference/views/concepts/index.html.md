<!-- <nav> -->
- [Akka](../../../index.html)
- [Reference](../../index.html)
- [Views](../index.html)
- [View concepts](index.html)

<!-- </nav> -->

# View Concepts

This section covers the key concepts that underpin Akka Views, explaining how they work and how to use them effectively in your applications.

## <a href="about:blank#_core_concepts"></a> Core Concepts

### <a href="about:blank#_data_structure_and_access"></a> Data Structure and Access

- [Table Updaters](table-updaters.html) - The fundamental building blocks of Views, defining data sources and table structures
- [Data Types](data-types.html) - The supported data types in Views and how they map between Java and the query language
- [Result Mapping](result-mapping.html) - How query results are transformed into Java objects in your application

### <a href="about:blank#_specialized_data_handling"></a> Specialized Data Handling

- [Optional Fields](optional-fields.html) - Working with values that may or may not be present
- [Array Types](array-types.html) - Handling collections of values within a single view row

### <a href="about:blank#_advanced_features"></a> Advanced Features

- [Advanced Views](advanced-views.html) - Creating sophisticated views with multiple tables, joins, and complex data structures

## <a href="about:blank#_understanding_views"></a> Understanding Views

Views in Akka provide a flexible way to access your data using patterns beyond simple key-based lookups. They create queryable projections of your entities that can be optimized for specific access patterns.

### <a href="about:blank#_how_views_work"></a> How Views Work

1. **Data Consumption**: Views subscribe to events or state changes from entities or topics
2. **Transformation**: These events/changes are transformed into rows in view tables
3. **Indexing**: Views automatically create indexes based on your queries
4. **Querying**: The view query language allows you to retrieve and filter this data

### <a href="about:blank#_view_benefits"></a> View Benefits

- **Flexible Access Patterns**: Access data by any field, not just entity IDs
- **Optimized Queries**: Views create indexes for efficient querying based on your needs
- **Joined Data**: Combine data from multiple entities into unified query results
- **Custom Projections**: Transform entity data into formats optimized for reading

### <a href="about:blank#_view_characteristics"></a> View Characteristics

- **Eventually Consistent**: Views are eventually consistent with their source entities
- **Read Optimized**: Views are designed for efficient reading, not for updates
- **Declarative Definition**: Views are defined by their queries and transformations
- **Automatic Maintenance**: Updates to views happen automatically as entities change

## <a href="about:blank#_from_entity_to_view"></a> From Entity to View

Understanding the relationship between entities and views is key to effective modeling:

### <a href="about:blank#_entity_focus_vs_view_focus"></a> Entity Focus vs. View Focus

- **Entities**: Focus on business logic, command handling, and state consistency
- **Views**: Focus on data access patterns, querying, and read optimizations

### <a href="about:blank#_data_flow"></a> Data Flow

1. Clients send commands to entities
2. Entities process commands and emit events or state changes
3. Views consume these events/changes and update their tables
4. Clients query views to read data in the desired format

### <a href="about:blank#_creating_effective_views"></a> Creating Effective Views

- Define views based on access patterns in your application
- Consider different views for different query needs
- Design your table structure and queries together
- Balance normalization and denormalization based on query patterns

## <a href="about:blank#_related_documentation"></a> Related Documentation

- [View Query Syntax](../syntax/index.html) - Complete reference for the View query language
- [Implementing Views](../../../sdk/views.html) - How to implement Views in your application

<!-- <footer> -->
<!-- <nav> -->
[page_token_offset()](../syntax/functions/page-token-offset.html) [Table updaters](table-updaters.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->