-- Login: dispatcher / password123 (the value below is the BCrypt hash of "password123")
INSERT INTO users (username, password_hash)
VALUES ('dispatcher', '$2y$10$x7jt/kA9.pm9NfRMuCEjpuP.KA5NYK9xk1Vq3KlTAeUZTkcYY3N8K');

INSERT INTO customers (name, email, city) VALUES
    ('Anna Jensen',     'anna@example.com',    'Copenhagen'),
    ('Bo Nielsen',      'bo@example.com',      'Aarhus'),
    ('Clara Hansen',    'clara@example.com',   'Odense'),
    ('David Pedersen',  'david@example.com',   'Aalborg'),
    ('Emma Andersen',   'emma@example.com',    'Copenhagen'),
    ('Frederik Larsen', 'frederik@example.com','Esbjerg'),
    ('Gry Sorensen',    'gry@example.com',     'Roskilde'),
    ('Henrik Madsen',   'henrik@example.com',  'Copenhagen'),
    ('Ida Kristensen',  'ida@example.com',     'Aarhus'),
    ('Jonas Olsen',     'jonas@example.com',   'Odense'),
    ('Karen Thomsen',   'karen@example.com',   'Vejle'),
    ('Lars Poulsen',    'lars@example.com',    'Copenhagen');

INSERT INTO couriers (name, vehicle, phone) VALUES
    ('Mads Bike',    'BICYCLE',    '+4511111111'),
    ('Nina Van',     'VAN',        '+4522222222'),
    ('Oscar Truck',  'TRUCK',      '+4533333333'),
    ('Pia Scooter',  'SCOOTER',    '+4544444444'),
    ('Rasmus Van',   'VAN',        '+4555555555'),
    ('Sofie Cargo',  'CARGO_BIKE', '+4566666666');

INSERT INTO deliveries (customer_id, courier_id, address, status) VALUES
    (1,  1,    'Norrebrogade 10, Copenhagen', 'DELIVERED'),
    (1,  2,    'Norrebrogade 10, Copenhagen', 'IN_TRANSIT'),
    (2,  3,    'Park Alle 5, Aarhus',         'IN_TRANSIT'),
    (3,  NULL, 'Vestergade 22, Odense',       'PENDING'),
    (4,  4,    'Boulevarden 3, Aalborg',      'DELIVERED'),
    (5,  1,    'Amagerbrogade 77, Copenhagen','PENDING'),
    (6,  5,    'Torvet 1, Esbjerg',           'IN_TRANSIT'),
    (7,  NULL, 'Algade 14, Roskilde',         'PENDING'),
    (8,  6,    'Vesterbrogade 99, Copenhagen','DELIVERED'),
    (9,  3,    'Strandvejen 8, Aarhus',       'CANCELLED'),
    (10, 2,    'Kongensgade 40, Odense',      'IN_TRANSIT'),
    (11, NULL, 'Havnegade 2, Vejle',          'PENDING'),
    (12, 6,    'Osterbrogade 55, Copenhagen', 'IN_TRANSIT'),
    (2,  5,    'Park Alle 5, Aarhus',         'DELIVERED'),
    (5,  4,    'Amagerbrogade 77, Copenhagen','PENDING');
