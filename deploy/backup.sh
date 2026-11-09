#!/bin/sh
# Sauvegarde de la base Hermès dans /backups (format custom de pg_dump, à restaurer avec pg_restore).
#   sh /backup.sh           une sauvegarde immédiate
#   sh /backup.sh --daemon  une sauvegarde par jour à BACKUP_TIME, en gardant BACKUP_KEEP_DAYS jours
set -eu

backup() {
    file="/backups/hermes-$(date +%Y-%m-%d_%H%M).dump"
    if pg_dump --format=custom --file="$file.tmp"; then
        mv "$file.tmp" "$file"
        echo "$(date '+%F %T') sauvegarde : $file ($(du -h "$file" | cut -f1))"
    else
        rm -f "$file.tmp"
        echo "$(date '+%F %T') ÉCHEC de la sauvegarde" >&2
        return 1
    fi
    find /backups -name 'hermes-*.dump' -mtime "+${BACKUP_KEEP_DAYS:-30}" -print -delete
}

if [ "${1:-}" != "--daemon" ]; then
    backup
    exit
fi

time="${BACKUP_TIME:-03:00}"
echo "Sauvegarde quotidienne à $time, conservation ${BACKUP_KEEP_DAYS:-30} jours"
while true; do
    now=$(date +%s)
    next=$(date -d "$(date +%Y-%m-%d) $time" +%s)
    [ "$next" -le "$now" ] && next=$((next + 86400))
    sleep $((next - now))
    backup || true
done
