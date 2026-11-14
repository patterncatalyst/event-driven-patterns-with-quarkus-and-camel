-- Reference data that shipping-service owns: how each carrier moves a parcel and where its tracking page lives.
CREATE TABLE shipping.carrier (
    carrier               text PRIMARY KEY,
    mode                  text NOT NULL,
    tracking_url_template text NOT NULL
);

INSERT INTO shipping.carrier (carrier, mode, tracking_url_template) VALUES
    ('FASTLINE', 'ROAD', 'https://track.fastline.example/{trackingNumber}'),
    ('AIRCO', 'AIR', 'https://track.airco.example/{trackingNumber}');
