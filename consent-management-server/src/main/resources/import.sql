
-- Changeset db.changelog-1.0.xml::2018-11-05-1::dgo@adorsys.de
-- Fill in default values for crypto algorithms
INSERT INTO crypto_algorithm (algorithm_id, external_id, algorithm, version) VALUES ('1000500', 'nML0IXWdMa', 'AES/GCM/NoPadding', '1');

INSERT INTO crypto_algorithm (algorithm_id, external_id, algorithm, version) VALUES ('1000501', 'bS6p6XvTWI', 'AES/ECB/PKCS5Padding', '2');

INSERT INTO crypto_algorithm (algorithm_id, external_id, algorithm, version) VALUES ('1000502', 'gQ8wkMeo93', 'JWE/GCM/256', '3');
