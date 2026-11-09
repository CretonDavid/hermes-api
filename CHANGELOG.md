# Journal des versions

Toutes les évolutions notables de l'API d'Hermès sont listées ici. Les numéros de version suivent
[SemVer](https://semver.org/lang/fr/).

## [1.0.0] - 2026-11-09

Première version publiée.

### Comptes utilisateurs et sécurité

- Connexion par un numéro d'accès de 30 chiffres, sans e-mail ni mot de passe. Seuls les 6 premiers
  chiffres et une empreinte Argon2 sont conservés : un numéro perdu ne peut pas être récupéré.
- Jetons d'accès JWT courts et jetons de renouvellement à usage unique, enregistrés en base. La
  réutilisation d'un ancien jeton coupe la session, et la déconnexion ou la désactivation du compte
  invalide tous les jetons.
- Limitation du nombre de tentatives de connexion et d'inscription par adresse IP.
- Désactivation ou suppression définitive du compte par l'utilisateur lui-même.
- Origine des requêtes vérifiée (CORS) et secrets lus dans l'environnement (`JWT_SECRET`,
  `DB_PASSWORD`).

### Comptes bancaires et opérations

- Comptes bancaires par utilisateur, avec types personnalisables, devise, solde initial et clôture.
  Le solde est toujours calculé à partir des opérations.
- Opérations avec recherche, filtres, tri, pagination, totaux par devise et catégorisation groupée.
- Virements entre comptes, y compris entre deux devises, sous la forme de deux opérations liées.
- Catégories à plusieurs niveaux, dépenses et revenus, avec une liste standard modifiable.
- Pointage et rapprochement bancaire.
- Import CSV et OFX avec vérification avant enregistrement et détection des doublons.

### Récurrences et remboursements

- Opérations récurrentes générées automatiquement chaque nuit, virements récurrents compris.
- Remboursement d'une dépense récurrente par un ou plusieurs tiers, chacun avec sa part, son rythme
  et sa période de début. Le trop-perçu est reporté sur les périodes suivantes.

### Devises et statistiques

- Devises courantes fournies par défaut, devises personnalisées et taux de change, récupérés
  automatiquement ou saisis à la main.
- Statistiques : dépenses par catégorie, évolution des revenus et des dépenses, épargne et
  patrimoine, avec conversion dans une devise de référence.

### Exploitation

- Image Docker et déploiement Docker Compose (PostgreSQL, API, front et sauvegarde quotidienne de la
  base), avec une sonde de santé `/actuator/health`.
- Intégration continue GitHub Actions : compilation avec avertissements, tests et couverture JaCoCo.

[1.0.0]: https://github.com/CretonDavid/hermes-api/releases/tag/v1.0.0
