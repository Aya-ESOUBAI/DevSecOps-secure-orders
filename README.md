# secure-orders : authentification et autorisation interservices

Projet ENSIAS 3A GL, Ingenierie des Systemes Securises (A.U. 2026/2027).
Application de gestion de commandes a 3 microservices Spring Boot (Java 17, Maven).

## Architecture

    client --> order-service (8081) --> inventory-service (8082)
                                    \-> payment-service   (8083)
    Keycloak (8080, docker-compose) : serveur d'identite, utilise a partir de l'etape securite

| Service | Port | Endpoints |
|---|---|---|
| order-service | 8081 | `POST /orders`, `GET /orders`, `GET /orders/{id}` |
| inventory-service | 8082 | `GET /inventory`, `GET /inventory/{id}`, `POST /inventory/reserve` |
| payment-service | 8083 | `POST /payments` |

Produits de test : `P-100` (Laptop, 5000), `P-200` (Souris, 150), `P-300` (Clavier, 400).
Etat actuel : **aucune securite**. C'est le point de depart.

## Lancer

Ouvrir le dossier dans IntelliJ (File > Open > pom.xml > Open as Project), puis lancer les 3 classes `*Application`.
Ou en ligne de commande, dans 3 terminaux :

    mvn -pl inventory-service spring-boot:run
    mvn -pl payment-service spring-boot:run
    mvn -pl order-service spring-boot:run

## Tester

    # commande valide : 201 + status CONFIRMED
    curl -i -X POST localhost:8081/orders -H "Content-Type: application/json" \
         -d '{"productId":"P-200","quantity":2}'

    # stock insuffisant : 409
    curl -i -X POST localhost:8081/orders -H "Content-Type: application/json" \
         -d '{"productId":"P-100","quantity":999}'

    # produit inconnu : 404
    curl -i -X POST localhost:8081/orders -H "Content-Type: application/json" \
         -d '{"productId":"X","quantity":1}'

    # paiement refuse (montant > 100000) : status PAYMENT_FAILED
    curl -i -X POST localhost:8081/orders -H "Content-Type: application/json" \
         -d '{"productId":"P-100","quantity":10}'

## Keycloak

    docker compose up -d      # console : http://localhost:8080 (admin / admin)

## Repartition (binome)

- **Personne A** : Keycloak (realm, clients, roles, utilisateurs), validation JWT, client credentials, puis mTLS / pipeline.
- **Personne B** : regles RBAC/ABAC dans les services, tests de securite (401, 403, token expire), scenarios de demo.
- **Commun** : rapport, tableau comparatif, presentation.
- Git : une branche par personne (`feat/keycloak-jwt`, `feat/rbac-tests`), merge quotidien dans `main`.
