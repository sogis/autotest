# json2qgs Tests

The system under test (sut) is the [json2qqs](https://github.com/sogis/json2qgs) 
commandline tool. The only way to verify whether correct qgs is generated is 
by starting a qgis server with the generated qgs and verifiying, that 
featureclasses and layers are returned / rendered as expected.

## Scope

In scope for the tests are only postgis backed vector layers and raster layers
from both a image catalogue and a single geotiff file.

## Test aspects

Tests generate a `.qgs` project and load it in QGIS Server. Verify vector
feature classes through WFS `GetCapabilities`, `DescribeFeatureType`, and
`GetFeature`. Verify layers through WMS `GetCapabilities` and `GetMap`.
Use synthetic, versioned, deterministic fixtures for all scenarios.

|Group|Aspect|Expected behavior|
|---|---|---|
|fclass|attribute inclusion (ai)|only included attributes are returned in the response.|
|fclass|nonspatial attribute serialization (nas)|Attribute types are serialized into the json output as expected. True also for "special" types like json, date, time, uuid, boolean and null values.|
|fclass|spatial attribute serialization (sas)|Spatial Attribute types are serialized into the json output as expected. True also for tables having more than one geometry column, for optional geometries, and for multipart geometries.|
|layer|Single GeoTIFF rendering (gtr)|WMS publishes and renders the expected raster.|
|layer|Image catalogue rendering (icr)|WMS publishes and renders the expected raster.|
|layer|Vector geometry rendering (vcr)|WMS GetMap renders point, line, and polygon layers, including multipart geometries.|
|cli|CLI error handling (ceh)|Invalid arguments, missing or unreadable input, malformed configuration, invalid required settings, and unwritable output produce a nonzero exit status and useful diagnostics.|

Styling, labeling, and scale rules are outside this focused scope.

## Tests classes and covered aspects

The matrix shows, which test class covers the above noted aspects 

|Test Class|ai?|nas?|sas?|gtr?|icr?|vcr?|ceh?|
|---|---|---|---|---|---|---|---|
|Test Class|ai?|nas?|sas?|gtr?|icr?|vcr?|ceh?|
