# secure-orders : authentification et autorisation interservices

[Projet Ingénierie des Systèmes Sécurisés.pdf](https://github.com/user-attachments/files/35186204/Projet.Ingenierie.des.Systemes.Securises.pdf)

Projet ENSIAS 3A GL, Ingénierie des Systèmes Sécurisés (A.U. 2026/2027).
Application de gestion de commandes à 3 microservices Spring Boot (Java 17, Maven), sécurisée avec Keycloak (OAuth 2.0 / OIDC, JWT).

## Architecture

    client --> order-service (8081) --> inventory-service (8082)
                                    \-> payment-service   (8083)
    Keycloak (8080, docker-compose) : serveur d'identité (realm secure-orders)

| Service | Port | Endpoints |
|---|---|---|
| order-service | 8081 | `POST /orders`, `GET /orders`, `GET /orders/{id}` |
| inventory-service | 8082 | `GET /inventory`, `GET /inventory/{id}`, `POST /inventory/reserve` |
| payment-service | 8083 | `POST /payments` |

Produits de test : `P-100` (Laptop, 5000), `P-200` (Souris, 150), `P-300` (Clavier, 400).

## État d'avancement

| Élément | Statut |
|---|---|
| Keycloak : realm, rôles, utilisateurs, clients | Fait |
| Validation des JWT dans les 3 services | Fait |
| Client credentials : `order-service` appelle les autres avec son propre token | Fait |
| Autorisation RBAC / ABAC | Fait |
| Tests de sécurité (401, 403, token expiré) | Fait |
| Bonus : mTLS ou pipeline | Si le temps le permet |

Tous les endpoints exigent un token valide : un appel sans token renvoie **401**.

## Prérequis

- JDK 17, IntelliJ IDEA
- Docker Desktop (démarré)

## Lancer

1. Démarrer Keycloak :

        docker compose up -d      # console : http://localhost:8080 (admin / admin, dev uniquement)

2. Configurer Keycloak (voir la section ci-dessous), à faire une fois par machine.
3. Définir la variable d'environnement du secret de `order-service`. Le secret se trouve dans Keycloak : Clients > `order-service` > Credentials. **Ne jamais le commiter.**
   - IntelliJ : Run > Edit Configurations > Environment variables : `ORDER_CLIENT_SECRET=...`
   - PowerShell : `$env:ORDER_CLIENT_SECRET="..."`
4. Ouvrir le dossier dans IntelliJ (File > Open > `pom.xml` > Open as Project), puis lancer les 3 classes `*Application` : `InventoryServiceApplication`, `PaymentServiceApplication`, puis `OrderServiceApplication`.

Ou en ligne de commande, dans 3 terminaux (la variable `ORDER_CLIENT_SECRET` doit être définie dans le dernier) :

    mvn -pl inventory-service spring-boot:run
    mvn -pl payment-service spring-boot:run
    mvn -pl order-service spring-boot:run

## Keycloak

La configuration n'est pas dans le dépôt : elle vit dans le conteneur Keycloak. Chaque machine doit la recréer.

1. Créer le realm `secure-orders`.
2. Realm roles : `USER`, `ADMIN`, `SVC_ORDER`.
3. Utilisateurs (mot de passe non temporaire, email, prénom et nom renseignés) :
   - `alice` : rôle `USER`
   - `bob` : rôles `USER` et `ADMIN`
4. Client `order-service` : Client authentication ON, Service accounts roles coché, Standard flow décoché. Onglet Service accounts roles : assigner le rôle `SVC_ORDER`.
5. Client `demo-client` (tokens de test uniquement) : Client authentication OFF, Direct access grants coché.

Détails et explications : [docs/authentification-identites.md](docs/authentification-identites.md).

## Tester

Les commandes suivantes sont pour PowerShell (`curl` y est un alias, donc on utilise `Invoke-RestMethod`).

Obtenir un token pour alice (comptes de test, réservés au développement) :

    $r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/realms/secure-orders/protocol/openid-connect/token -Body @{grant_type="password"; client_id="demo-client"; username="alice"; password="<mot de passe d'alice>"}

Commande valide : 201 + `status : CONFIRMED`, total 300.

    Invoke-RestMethod -Method Post -Uri http://localhost:8081/orders -Headers @{Authorization="Bearer $($r.access_token)"} -ContentType "application/json" -Body '{"productId":"P-200","quantity":2}'

Sans token : 401.

    try { Invoke-RestMethod -Method Post -Uri http://localhost:8081/orders -ContentType "application/json" -Body '{"productId":"P-200","quantity":2}' } catch { $_.Exception.Response.StatusCode.value__ }

Autres corps de requête à essayer avec le même appel (avec token) :

| Corps | Résultat attendu |
|---|---|
| `{"productId":"P-100","quantity":999}` | 409, stock insuffisant |
| `{"productId":"X","quantity":1}` | 404, produit inconnu |
| `{"productId":"P-100","quantity":10}` | `status : PAYMENT_FAILED` (montant > 100000) |

## Documentation

- [Authentification et gestion des identités](docs/authentification-identites.md) : mécanismes utilisés (OAuth 2.0 / OIDC, JWT), flux d'une commande, limites.

## Répartition (binôme)

- **Personne A (Oumaima)** : Keycloak (realm, clients, rôles, utilisateurs), validation JWT, client credentials, puis mTLS / pipeline.
- **Personne B (Aya)** : règles RBAC/ABAC dans les services, tests de sécurité (401, 403, token expiré), scénarios de démo.
- **Commun** : rapport, tableau comparatif, présentation.
- Git : une branche par personne (`feat/keycloak-jwt`, `feat/rbac-tests`), merge quotidien dans `main`.
