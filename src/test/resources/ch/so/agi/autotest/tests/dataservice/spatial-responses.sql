DROP TABLE IF EXISTS dataservice.spatial_responses;

CREATE TABLE dataservice.spatial_responses (
    id integer PRIMARY KEY,
    label text NOT NULL,
    geometry geometry(POINT, 2056)
);

INSERT INTO dataservice.spatial_responses (id, label, geometry) VALUES
    (1, 'Configured point', ST_SetSRID(ST_Point(2600000, 1200000), 2056)),
    (2, 'No geometry', NULL);
