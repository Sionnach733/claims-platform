-- Synthetic reference data for local/demo use. Contains NO real PHI.
-- NPIs are fictitious but pass the CMS Luhn check (prefix 80840).

INSERT INTO members (member_id, first_name, last_name, date_of_birth, plan_code, coverage_start, coverage_end) VALUES
    ('AET100000001', 'Aoife',   'Murphy',   DATE '1985-03-14', 'PPO-GOLD',   DATE '2024-01-01', NULL),
    ('AET100000002', 'Ciaran',  'Walsh',    DATE '1990-07-22', 'HMO-SILVER', DATE '2025-01-01', DATE '2026-12-31'),
    ('AET100000003', 'Niamh',   'O''Brien', DATE '1978-11-02', 'PPO-GOLD',   DATE '2023-06-01', NULL),
    ('AET100000004', 'Declan',  'Kelly',    DATE '1965-01-30', 'HMO-BRONZE', DATE '2022-01-01', DATE '2024-12-31'),  -- lapsed coverage
    ('AET100000005', 'Siobhan', 'Ryan',     DATE '2001-09-09', 'EPO-SILVER', DATE '2026-01-01', NULL);

INSERT INTO providers (npi, name, specialty, in_network) VALUES
    ('1234567893', 'Galway Family Practice',     'Family Medicine',  TRUE),
    ('1987654328', 'Western Orthopaedic Clinic', 'Orthopaedics',     TRUE),
    ('1456789019', 'Corrib Cardiology',          'Cardiology',       TRUE),
    ('1678901236', 'Atlantic Imaging Centre',    'Radiology',        FALSE),  -- out of network
    ('1321098760', 'Salthill Physiotherapy',     'Physical Therapy', FALSE);  -- out of network
