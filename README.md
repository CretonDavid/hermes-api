# Hermès API

API REST de comptabilité personnelle : comptes bancaires, opérations, virements, catégories, opérations récurrentes, import de relevés, rapprochement bancaire et statistiques.

Stack : Java 26, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, PostgreSQL.

## Démarrage

Prérequis : Java 26 et une base PostgreSQL `hermes-db` (identifiants dans `src/main/resources/application.properties`).

```bash
# Clé de signature des JWT : 32 octets minimum, encodés en Base64
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run
```

L'API écoute sur `http://localhost:8080`. Le schéma est créé par Hibernate (`ddl-auto=update`) et les devises courantes sont insérées au démarrage.

Tests (base H2 en mémoire, aucune dépendance externe) :

```bash
./mvnw test
```

## Authentification

Pas d'e-mail ni de mot de passe : `POST /auth/register` génère un **numéro de compte de 30 chiffres**, seul identifiant de l'utilisateur. Seul son hash Argon2 est stocké : un numéro perdu ne peut pas être récupéré.

| Méthode | Route | Rôle |
|---|---|---|
| POST | `/auth/register` | Crée un utilisateur et renvoie son numéro de compte |
| POST | `/auth/login` | `{accountNumber}` → `{access_token, refresh_token}` |
| POST | `/auth/refresh` | `{refresh_token}` → nouvelle paire de tokens |
| POST | `/auth/reactivate` | Réactive un compte désactivé |
| GET / PATCH | `/users/me` | Profil, devise de référence |
| POST | `/users/me/disable` | Désactive le compte (données conservées) |
| DELETE | `/users/me` | `{accountNumber}` : suppression définitive de toutes les données |

Toutes les autres routes exigent l'en-tête `Authorization: Bearer <access_token>` (15 min ; refresh 7 jours). Chaque utilisateur ne voit que ses propres données.

## Ressources

| Route | Description |
|---|---|
| `/account-types` | Types de compte de l'utilisateur ; `savings=true` pour les comptes d'épargne |
| `/accounts` | Comptes. Le solde est **calculé** : solde initial + opérations (`balance` à date, `forecastBalance` avec les opérations futures, `clearedBalance` pointé) |
| `/accounts/{id}/reconciliation-status`, `/accounts/{id}/reconciliations` | Rapprochement bancaire : valide les opérations pointées si le solde correspond au relevé, puis les verrouille. Le dernier rapprochement peut être annulé |
| `/currencies` | Devises par défaut (partagées) + devises personnelles |
| `/exchange-rates` | Taux de change saisis ; `POST /exchange-rates/sync[?from=]` importe les taux BCE (via frankfurter.dev) |
| `/categories` | Catégories hiérarchiques, créées par défaut à l'inscription. `DELETE ?replacementId=` déplace les opérations |
| `/transactions` | Opérations, recherche paginée et filtrée (comptes, période, catégorie et sous-catégories, type, statut, texte, montant). `PATCH /{id}/status` pour pointer |
| `/transactions/payees` | Auto-complétion des tiers avec la dernière catégorie utilisée |
| `/transfers` | Virements entre comptes : deux opérations liées (gère les devises différentes) |
| `/recurring-transactions` | Opérations et virements récurrents, générés à échéance (au démarrage, chaque nuit et à la connexion). `/upcoming?days=` liste les prochaines échéances |
| `/imports/csv/inspect`, `/imports/csv/preview`, `/imports/ofx/preview`, `/imports/confirm` | Import de relevés en deux temps : aperçu (doublons détectés, catégories suggérées) puis confirmation |
| `/stats/summary`, `/stats/categories`, `/stats/monthly`, `/stats/category-trend`, `/stats/payees`, `/stats/balance-history`, `/stats/savings` | Statistiques sur une période, converties dans la devise de référence |

### Règles des statistiques

- Les virements internes sont exclus des revenus et des dépenses.
- Un crédit dans une catégorie de dépense (un remboursement) vient en déduction des dépenses.
- Une opération non catégorisée compte en dépense si c'est un débit, en revenu sinon.
- L'épargne correspond aux virements nets d'un compte courant vers un compte d'épargne.
- La conversion utilise le dernier taux connu à la date de l'opération ; les devises sans taux sont signalées dans `missingRates`.

## Erreurs

Format homogène, messages en français :

```json
{ "timestamp": "...", "status": 400, "code": "VALIDATION_ERROR", "message": "...", "fieldErrors": { "amount": "..." } }
```

Codes HTTP : 400 (validation, règle métier), 401 (non authentifié), 403 (compte désactivé), 404, 409 (doublon).
