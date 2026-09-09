# Data Service test coverage

This matrix tracks integration coverage of the Sourcepole QWC Data Service.
Coverage is complete when every aspect column has at least one `x`.

Legend: `x` implemented and verified by the named test, `○` planned coverage,
and `—` not applicable to that test class.

## Aspect descriptions

### Collection read

Verifies that a client can retrieve the configured dataset collection endpoint.
The response must contain the expected matching features and collection metadata.

### Feature read

Verifies retrieval of one configured feature by its primary-key identifier.
The response must identify the requested feature and expose only its permitted data.

### Data types

Verifies the GeoJSON response shape and the JSON representation of supported database column types.
It covers values whose JSON type or string encoding is part of the public API contract.

### Spatial geometry

Verifies that configured spatial fields are serialized as GeoJSON geometry with the expected coordinate reference system.
It also covers the behaviour of datasets with null or absent geometry where applicable.

### Spatial

Verifies that supported filter expressions narrow a collection to the correct features.
Verifies spatial filtering, attribute filtering and the combination of both.
The test must show that filtering uses the configured fields and preserves the response contract.

### Create

Verifies that a permitted client can create a feature from a valid GeoJSON request.
The response and a subsequent read must confirm that the expected data was persisted.

### Update

Verifies that a permitted client can change an existing feature with a valid request.
The updated representation and a subsequent read must confirm the persisted change.

### Delete

Verifies that a permitted client can delete an existing feature.
A follow-up read must demonstrate that the feature is no longer available.

### Permissions

Verifies anonymous and authenticated access rules for configured datasets and operations.
It also verifies that responses omit fields which the caller is not permitted to read or write.

### Invalid request and not found

Verifies status codes and response contracts for malformed input, unknown datasets, missing features, and denied operations.
These cases must fail predictably without changing fixture data.

## Tests and aspects

The following table shows the Tests classes and which aspects are covered by each class.

|Test class (DataServiceTest.?)|Collection read|Feature read|Data types|Spatial geometry|Filters|Create|Update|Delete|Permissions|Invalid request and not found|
|---|---|---|---|---|---|---|---|---|---|---|
|NonSpatialTypesTest|x|-|x|-|-|-|-|-|-|-|
|SpatialResponses|x|-|-|x|-|-|-|-|-|-|
|FilterTest|x|-|-|-|x|-|-|-|-|-|
|ModifyFeatureTest|-|x|(x)|(x)|-|x|x|x|-|-|
|AuthorizationTest|x|x|-|-|x|x|x|x|x|
|Errors|—|—|—|—|—|—|—|—|—|x|
