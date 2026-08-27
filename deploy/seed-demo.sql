-- Dati dimostrativi per ReceiptHub.
--
-- Ripetibile: prodotti e utenti finti usano ON CONFLICT DO NOTHING, e le
-- ricevute vengono generate solo se non ce ne sono già con il prefisso SEED-.
-- Non tocca gli utenti reali se non per assegnare loro delle ricevute.
--
-- Gli utenti finti non hanno firebase_uid: non possono fare login, esistono solo
-- come intestatari di ricevute (e per esercitare la ricerca fuzzy lato admin).

INSERT INTO product (name, code) VALUES
    ('Pasta',      'PRD001'),
    ('Latte',      'PRD002'),
    ('Pane',       'PRD003'),
    ('Caffe',      'PRD004'),
    ('Detersivo',  'PRD005'),
    ('Shampoo',    'PRD006'),
    ('Uova',       'PRD007'),
    ('Olio',       'PRD008'),
    ('Zucchero',   'PRD009'),
    ('Biscotti',   'PRD010')
ON CONFLICT (code) DO NOTHING;

INSERT INTO user_table (name, surname, email, phone, codice_fiscale, role, created_at, updated_at) VALUES
    ('Mario',  'Rossi',   'mario.rossi@test.local',    '+39320000001', 'RSSMRA80A01H501U', 'ROLE_USER', now(), now()),
    ('Luigi',  'Verdi',   'luigi.verdi@test.local',    '+39320000002', 'VRDLGU85B02H501Z', 'ROLE_USER', now(), now()),
    ('Anna',   'Bianchi', 'anna.bianchi@test.local',   '+39320000003', 'BNCNNA90C41H501W', 'ROLE_USER', now(), now()),
    ('Giulia', 'Neri',    'giulia.neri@test.local',    '+39320000004', 'NRIGLI92D42H501K', 'ROLE_USER', now(), now()),
    ('Marco',  'Ferrari', 'marco.ferrari@test.local',  '+39320000005', 'FRRMRC88E03H501L', 'ROLE_USER', now(), now()),
    ('Sara',   'Romano',  'sara.romano@test.local',    '+39320000006', 'RMNSRA91F43H501M', 'ROLE_USER', now(), now()),
    ('Davide', 'Colombo', 'davide.colombo@test.local', '+39320000007', 'CLMDVD87G04H501N', 'ROLE_USER', now(), now()),
    ('Elisa',  'Ricci',   'elisa.ricci@test.local',    '+39320000008', 'RCCLSE93H44H501O', 'ROLE_USER', now(), now())
ON CONFLICT (email) DO NOTHING;

DO $$
DECLARE
    utenti        bigint[];
    admin_id      bigint;
    prodotti      bigint[];
    metodi        text[] := ARRAY['CASH','CREDIT_CARD','DEBIT_CARD','PAYPAL','BANK_TRANSFER'];
    i             int;
    r             int;
    id_ricevuta   bigint;
    id_utente     bigint;
    righe         int;
    subtotale     numeric(10,2);
    prezzo        numeric(10,2);
    quantita      int;
    data_ricevuta timestamptz;
BEGIN
    IF EXISTS (SELECT 1 FROM receipt WHERE code LIKE 'SEED-%') THEN
        RAISE NOTICE 'Ricevute SEED gia presenti: non genero nulla.';
        RETURN;
    END IF;

    SELECT array_agg(user_id) INTO utenti
        FROM user_table WHERE email LIKE '%@test.local';
    SELECT array_agg(product_id) INTO prodotti FROM product;
    SELECT user_id INTO admin_id
        FROM user_table WHERE role = 'ROLE_ADMIN' ORDER BY user_id LIMIT 1;

    FOR i IN 1..40 LOOP
        -- Un terzo delle ricevute va all'admin, così la sua dashboard e le
        -- statistiche personali hanno qualcosa da mostrare invece di stati vuoti.
        IF admin_id IS NOT NULL AND i % 3 = 0 THEN
            id_utente := admin_id;
        ELSE
            id_utente := utenti[1 + floor(random() * array_length(utenti, 1))::int];
        END IF;

        data_ricevuta := now() - (random() * 90)::int * interval '1 day'
                                - (random() * 24)::int * interval '1 hour';

        INSERT INTO receipt (code, amount, tax, date, payment_method, user_id)
        VALUES (
            'SEED-' || to_char(now(), 'YYYYMMDD') || '-' || i || '-' || substr(md5(random()::text), 1, 6),
            0, 0, data_ricevuta,
            metodi[1 + floor(random() * array_length(metodi, 1))::int],
            id_utente
        )
        RETURNING receipt_id INTO id_ricevuta;

        subtotale := 0;
        righe := 1 + floor(random() * 3)::int;

        FOR r IN 1..righe LOOP
            quantita := 1 + floor(random() * 5)::int;
            prezzo   := round((1 + random() * 20)::numeric, 2);
            INSERT INTO purchase (product_id, quantity, price, receipt_id)
            VALUES (
                prodotti[1 + floor(random() * array_length(prodotti, 1))::int],
                quantita, prezzo, id_ricevuta
            );
            subtotale := subtotale + (quantita * prezzo);
        END LOOP;

        -- Stessa regola che applica la validazione dell'applicazione:
        -- importo = subtotale delle righe + 10% di tasse.
        UPDATE receipt
           SET tax    = round(subtotale * 0.10, 2),
               amount = round(subtotale * 1.10, 2)
         WHERE receipt_id = id_ricevuta;
    END LOOP;

    RAISE NOTICE 'Generate 40 ricevute con le relative righe.';
END $$;

SELECT
    (SELECT count(*) FROM product)                              AS prodotti,
    (SELECT count(*) FROM user_table)                           AS utenti,
    (SELECT count(*) FROM receipt)                              AS ricevute,
    (SELECT count(*) FROM purchase)                             AS righe;
