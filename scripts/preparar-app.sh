#!/usr/bin/env bash
# Deja la aplicacion bajo prueba lista para los tests: usuario administrador,
# token de API y datos conocidos.
#
# Es idempotente a proposito: se puede correr las veces que haga falta sin
# romper nada. Un script de preparacion que solo funciona la primera vez obliga
# a bajar y volver a levantar todo cada vez que algo sale mal.
#
#   ./scripts/preparar-app.sh
set -euo pipefail

USUARIO="${APP_USUARIO:-tester}"
PASSWORD="${APP_PASSWORD:-Framework2026!}"
EMAIL="${APP_EMAIL:-tester@framework.test}"
URL="${APP_URL:-http://localhost:3000}"
TOKEN_ARCHIVO="src/test/resources/config/.app-token"

echo "==> Esperando a que la aplicacion responda"
for _ in $(seq 1 60); do
    if curl -sf "$URL/api/healthz" >/dev/null 2>&1; then break; fi
    sleep 2
done
curl -sf "$URL/api/healthz" >/dev/null || { echo "La aplicacion no respondio"; exit 1; }

echo "==> Creando el usuario administrador (si no existe)"
# El comando falla si el usuario ya esta, y eso no es un error para este script.
docker exec --user git app-gitea gitea admin user create \
    --username "$USUARIO" --password "$PASSWORD" --email "$EMAIL" \
    --admin --must-change-password=false 2>/dev/null \
    && echo "    usuario creado" \
    || echo "    el usuario ya existia"

echo "==> Generando token de API"
# Se borra el anterior antes de crear uno nuevo: Gitea no permite dos tokens con
# el mismo nombre, y el token solo se muestra en el momento de crearlo.
curl -s -X DELETE -u "$USUARIO:$PASSWORD" \
    "$URL/api/v1/users/$USUARIO/tokens/framework" >/dev/null 2>&1 || true

TOKEN=$(curl -s -X POST -u "$USUARIO:$PASSWORD" \
    -H "Content-Type: application/json" \
    -d '{"name":"framework","scopes":["write:user","write:repository","write:issue"]}' \
    "$URL/api/v1/users/$USUARIO/tokens" | grep -o '"sha1":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "No se pudo generar el token de API"
    exit 1
fi

mkdir -p "$(dirname "$TOKEN_ARCHIVO")"
printf 'app.token=%s\n' "$TOKEN" > "$TOKEN_ARCHIVO"
echo "    token guardado en $TOKEN_ARCHIVO"

echo "==> Creando el repositorio de pruebas (si no existe)"
curl -s -X POST -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"framework-demo","description":"Repositorio de pruebas del framework","auto_init":true}' \
    "$URL/api/v1/user/repos" >/dev/null 2>&1 || true

echo ""
echo "Listo. La aplicacion esta en $URL"
echo "  usuario:  $USUARIO"
echo "  password: $PASSWORD"
echo "  base:     jdbc:postgresql://localhost:5433/gitea"
