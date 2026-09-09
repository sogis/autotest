# Data Service test coverage

This matrix tracks integration coverage of the Sourcepole QWC Data Service.
Coverage is complete when every aspect column has at least one `✓`.

Legend: `✓` implemented and verified by the named test, `○` planned coverage,
and `—` not applicable to that test class.

## Aspect descriptions

### Collection read

Verifies that a client can retrieve the configured dataset collection endpoint.
The response must contain the expected matching features and collection metadata.

### Feature read

Verifies retrieval of one configured feature by its primary-key identifier.
The response must identify the requested feature and expose only its permitted data.

### GeoJSON and data types

Verifies the GeoJSON response shape and the JSON representation of supported database column types.
It covers values whose JSON type or string encoding is part of the public API contract.

### Spatial geometry

Verifies that configured spatial fields are serialized as GeoJSON geometry with the expected coordinate reference system.
It also covers the behaviour of datasets with null or absent geometry where applicable.

### Filters

Verifies that supported filter expressions narrow a collection to the correct features.
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

### Permissions and attribute restrictions

Verifies anonymous and authenticated access rules for configured datasets and operations.
It also verifies that responses omit fields which the caller is not permitted to read or write.

### Invalid request and not found

Verifies status codes and response contracts for malformed input, unknown datasets, missing features, and denied operations.
These cases must fail predictably without changing fixture data.

### Attachments and configured extensions

Verifies upload, retrieval, update, and deletion of attachments for attachment-enabled datasets.
It also checks that configured file-extension and validation rules are enforced.

| Test class | Collection read | Feature read | GeoJSON and data types | Spatial geometry | Filters | Create | Update | Delete | Permissions and attribute restrictions | Invalid request and not found | Attachments and configured extensions |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `DataServiceTest.ColumnTypes` | ✓ | — | ✓ | — | — | — | — | — | — | — | — |
| `CollectionReadTest` | ○ | — | ○ | — | ○ | — | — | — | ○ | ○ | — |
| `FeatureReadTest` | — | ○ | ○ | — | — | — | — | — | ○ | ○ | — |
| `SpatialFeatureTest` | ○ | ○ | ○ | ○ | ○ | ○ | ○ | ○ | — | ○ | — |
| `CreateFeatureTest` | — | — | ○ | ○ | — | ○ | — | — | ○ | ○ | — |
| `UpdateFeatureTest` | — | ○ | ○ | ○ | — | — | ○ | — | ○ | ○ | — |
| `DeleteFeatureTest` | — | ○ | — | — | — | — | — | ○ | ○ | ○ | — |
| `AuthorizationTest` | ○ | ○ | ○ | — | — | ○ | ○ | ○ | ○ | ○ | — |
| `ErrorResponseTest` | ○ | ○ | — | — | ○ | ○ | ○ | ○ | ○ | ○ | — |
| `AttachmentTest` | — | ○ | — | — | — | ○ | ○ | ○ | ○ | ○ | ○ |

## Existing coverage

`DataServiceTest.ColumnTypes` verifies an anonymous public `GET` of the configured
`dataservice.attribute_types` dataset. It asserts the GeoJSON `FeatureCollection`
shape and result counts, plus serialization of integer, bigint, decimal,
boolean, UUID, date, datetime, varchar, text, JSON, JSONB, and nullable values.

## Criteria for future coverage

A matrix cell is covered only by an integration test that creates its required
deterministic fixture and asserts the observable HTTP response. Read tests
should cover configured-dataset access, feature lookup, and supported filter
expressions. Mutation tests should assert the persisted create, update, or
delete result.

Permission tests require public and restricted/writable fixtures, including
attribute-level restrictions where configured. Attachment coverage requires an
attachment-enabled dataset. Each error scenario should assert the relevant
status and response contract for invalid requests, missing features, or denied
operations.
