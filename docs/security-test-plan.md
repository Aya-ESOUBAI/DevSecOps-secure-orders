
| # | Scénario | Résultat attendu |
|---|----------|------------------|
| 1 | `POST /orders` sans token | 401 |
| 2 | `POST /orders` avec le token d'Alice (USER) | 201 |
| 3 | Token expiré | 401 |
| 4 | Alice consulte la commande de Bob | 403 |
| 5 | Alice passe une commande de plus de 10 000 | 403 |
| 6 | Alice appelle directement `POST /inventory/reserve` | 403 |
| 7 | Alice appelle directement `POST /payments` | 403 |
| 8 | Bob (ADMIN) liste toutes les commandes | 200 |
| 9 | Token du service `order-service` sur `/inventory/reserve` | 200 |
