# Déploiement d'Hermès avec Docker

Quatre conteneurs : `db` (PostgreSQL 18), `api` (Spring Boot), `app` (front Angular servi par nginx,
qui relaie aussi `/api` vers l'API) et `backup` (sauvegarde quotidienne de la base).
Seul le front est exposé, sur le port `HERMES_PORT` (8090 par défaut) : la base et l'API restent
internes au réseau Docker.

## Première installation

Les deux dépôts doivent être côte à côte :

```bash
mkdir -p ~/hermes && cd ~/hermes
git clone <url hermes-api> hermes-api
git clone <url hermes-app> hermes-app
cd hermes-api/deploy
cp .env.example .env
nano .env                      # DB_PASSWORD et JWT_SECRET (openssl rand -base64 48)
docker compose up -d --build
```

Hermès est alors accessible sur `http://<ip du serveur>:8090`. La base est créée vide au premier
démarrage : il suffit de créer un compte sur l'écran d'inscription.

Si le pare-feu `ufw` est actif : `sudo ufw allow 8090/tcp` (à limiter au réseau local ou au VPN).

## Mise à jour

```bash
cd ~/hermes
git -C hermes-api pull && git -C hermes-app pull
cd hermes-api/deploy
docker compose up -d --build
docker image prune -f           # supprime les anciennes images
```

Les données sont conservées dans le volume `hermes_db-data`.

L'API dispose d'une sonde de santé (`/actuator/health`) : le front ne démarre qu'une fois l'API
prête, et `docker compose ps` affiche `healthy` quand tout va bien.

## Suivi

```bash
docker compose ps
docker compose logs -f api      # ou app, db, backup
```

## Sauvegardes

Une sauvegarde est faite chaque jour à `BACKUP_TIME` dans `deploy/backups/` (`BACKUP_DIR`) et
conservée `BACKUP_KEEP_DAYS` jours. Il vaut mieux copier régulièrement ce dossier sur une autre
machine.

Sauvegarde immédiate :

```bash
docker compose exec backup sh /backup.sh
```

Restauration (remplace tout le contenu de la base) :

```bash
docker compose stop api
docker compose exec backup pg_restore --clean --if-exists --no-owner -d hermes /backups/hermes-AAAA-MM-JJ_HHMM.dump
docker compose start api
```

## Derrière le nginx du serveur (plus tard, pour le VPN ou un nom de domaine)

Un `location` qui relaie vers `http://127.0.0.1:8090` avec `proxy_set_header Host $http_host;`.
On peut alors limiter le port aux connexions locales avec `HERMES_PORT=127.0.0.1:8090`.

L'API considère une requête comme venant d'Hermès quand l'en-tête `Origin` du navigateur correspond
à l'en-tête `Host` reçu : sinon elle la rejette en 403 (CORS). En HTTPS, il faudra donc ajuster cette
vérification (en-têtes X-Forwarded-Proto / X-Forwarded-Port, ou APPLICATION_CORS_ALLOWEDORIGINS).
