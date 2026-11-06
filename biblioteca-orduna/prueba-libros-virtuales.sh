#!/usr/bin/env bash
# Prueba de punta a punta del flujo de libros virtuales (reglas 13, 14, 27, 28, 29 y 30
# de AGENTS.md).
#
# Recorre el camino completo con la API real:
#   1. El backend responde.
#   2. Se loguea como lector y como bibliotecario (los tokens se quedan en variables:
#      nunca se imprimen).
#   3. Se abre la CAJA (sin caja abierta no se puede cobrar nada).
#   4. El bibliotecario crea un libro VIRTUAL con su PDF (multipart en /api/libros/completo).
#   5. El lector lo pide pagando en la tienda: recibe numero de cola y codigo de 24 horas.
#   6. El personal busca la orden con ese codigo y la cobra.
#   7. El bibliotecario crea un SEGUNDO libro virtual: el lector no puede tener dos
#      pedidos del mismo libro ("ya tienes acceso"), asi que el pago con Yape se
#      prueba con otro libro.
#   8. El lector lo pide con YAPE, sube la captura del pago y el personal la aprueba:
#      el pedido pasa a PAGADO.
#   9. Se comprueban el aviso "Felicidades" y la lista del lector.
#  10. El lector abre el PDF con la URL firmada de 15 minutos.
#  11. Se comprueban los movimientos de la caja y se cierra el cajon: el arqueo
#      tiene que avisar del sobrante que se declara a proposito.
#  12. Se vence el alquiler en la base de datos y se comprueba que el libro sale "en plomo".
#
# Requisitos:
#   - El backend arrancado (mvn spring-boot:run) con el .env de R2 completo.
#   - El bucket privado de PDFs (R2_BUCKET_PDFS) tiene que existir (ahi van los PDF
#     y las capturas de los comprobantes).
#   - Node (el frontend lo necesita igual). No hace falta jq.
#
# Como se usa (Git Bash o Linux):
#   cd biblioteca-orduna
#   ./prueba-libros-virtuales.sh
#
# variables de entorno opcionales:
#   BASE=http://localhost:8080   direccion del backend
#   DB_PASSWORD=...              clave de MySQL (para el paso de vencer el alquiler)

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
API="$BASE/api"
DB_NAME="${DB_NAME:-biblioteca_orduna}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-denis123}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32mOK\033[0m %s\n' "$1"; }
info() { printf '     %s\n' "$1"; }
err()  { printf '  \033[31mFALLO\033[0m %s\n' "$1"; exit 1; }

# Saca un campo del JSON que llega por la entrada estandar.
campo() {
  node -e '
    let s = "";
    process.stdin.on("data", (d) => (s += d));
    process.stdin.on("end", () => {
      const o = JSON.parse(s);
      const v = o[process.argv[1]];
      console.log(v === undefined || v === null ? "" : v);
    });
  ' "$1"
}

# Inicia sesion y devuelve el token (se queda en la variable, no se imprime).
login() {
  local email="$1" clave="$2" cuerpo
  cuerpo="$(curl -sS --max-time 20 -X POST "$API/auth/login" \
      -H 'Content-Type: application/json' \
      -d "{\"email\":\"$email\",\"password\":\"$clave\"}")"
  if ! printf '%s' "$cuerpo" | grep -q '"token"'; then
    err "No se pudo iniciar sesion como $email. Respuesta del servidor: $cuerpo"
  fi
  printf '%s' "$cuerpo" | campo token
}

# ISBN de 13 digitos, distinto en cada llamada (la base lo tiene unico).
isbn_nuevo() {
  printf '978%010d' $(( (RANDOM * 1000 + RANDOM) % 10000000000 ))
}

# ---------------------------------------------------------------- 1. backend
say "1. El backend responde"
if ! curl -sS --max-time 10 "$BASE/" >/dev/null 2>&1; then
  err "No hay backend en $BASE. Arrancalo con: mvn spring-boot:run"
fi
ok "backend encendido en $BASE"

# ---------------------------------------------------------------- 2. sesiones
say "2. Sesiones (lector y bibliotecario)"
TOKEN_LECTOR="$(login lector@biblioteca.pe lector123)"
TOKEN_BIBLIO="$(login biblio@biblioteca.pe biblio123)"
ok "sesiones iniciadas"

# ---------------------------------------------------------------- 3. caja
say "3. Se abre la caja (sin caja abierta no se puede cobrar)"
CAJA_ACTUAL="$(curl -sS --max-time 20 "$API/cajas/actual" -H "Authorization: Bearer $TOKEN_BIBLIO")"
ID_CAJA="$(printf '%s' "$CAJA_ACTUAL" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => {
    const o = JSON.parse(s);
    console.log(o.caja_abierta ? o.caja_abierta.id : "");
  });
')"
if [ -n "$ID_CAJA" ]; then
  ok "ya habia una caja abierta (id $ID_CAJA): se usa esa"
else
  ABIERTA="$(curl -sS --max-time 20 -X POST "$API/cajas" \
      -H "Authorization: Bearer $TOKEN_BIBLIO" -H 'Content-Type: application/json' \
      -d '{"monto_inicial":0.00,"observaciones":"Prueba automatica"}')"
  ID_CAJA="$(printf '%s' "$ABIERTA" | campo id 2>/dev/null || true)"
  [ -n "$ID_CAJA" ] || err "No se pudo abrir la caja. Respuesta: $ABIERTA"
  ok "caja abierta (id $ID_CAJA) con monto inicial S/ 0.00"
fi

# ---------------------------------------------------------------- 4. libro
say "4. El bibliotecario crea un libro virtual con su PDF"

ID_AUTOR="$(curl -sS --max-time 20 "$API/autores" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const l = JSON.parse(s); console.log(l.length ? l[0].id : ""); });
')"
[ -n "$ID_AUTOR" ] || err "No hay autores en la base de datos (falta data.sql)"

ID_GENERO="$(curl -sS --max-time 20 "$API/generos" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const l = JSON.parse(s); console.log(l.length ? l[0].id : ""); });
')"
[ -n "$ID_GENERO" ] || err "No hay generos en la base de datos (falta data.sql)"

# PDF minimo y valido (lo construye node para que el xref cuadre).
node -e '
  const fs = require("fs");
  const texto = "BT /F1 16 Tf 20 100 Td (Libro virtual de prueba) Tj ET";
  const objetos = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
    "<< /Length " + texto.length + " >>\nstream\n" + texto + "\nendstream",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
  ];
  let pdf = "%PDF-1.4\n";
  const offsets = [];
  objetos.forEach((o, i) => {
    offsets.push(pdf.length);
    pdf += (i + 1) + " 0 obj\n" + o + "\nendobj\n";
  });
  const xref = pdf.length;
  pdf += "xref\n0 " + (objetos.length + 1) + "\n0000000000 65535 f \n";
  offsets.forEach((o) => { pdf += String(o).padStart(10, "0") + " 00000 n \n"; });
  pdf += "trailer\n<< /Size " + (objetos.length + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n";
  fs.writeFileSync(process.argv[1], pdf, "latin1");
' "$TMP/libro.pdf"

# Crea un libro virtual con su PDF y deja su id en LIBRO_CREADO.
crear_libro_virtual() {
  local titulo="$1" isbn
  isbn="$(isbn_nuevo)"
  cat > "$TMP/libro.json" <<JSON
{
  "titulo": "$titulo",
  "isbn": "$isbn",
  "editorial": "Biblioteca Orduña",
  "anio_publicacion": 2026,
  "num_paginas": 100,
  "ids_autores": [$ID_AUTOR],
  "ids_generos": [$ID_GENERO],
  "tipo_libro": "VIRTUAL",
  "precio_alquiler_diario": 2.50,
  "precio_compra": 35.00
}
JSON
  local respuesta
  respuesta="$(curl -sS --max-time 60 -X POST "$API/libros/completo" \
      -H "Authorization: Bearer $TOKEN_BIBLIO" \
      -F "libro=@$TMP/libro.json;type=application/json" \
      -F "pdf=@$TMP/libro.pdf;type=application/pdf")"
  LIBRO_CREADO="$(printf '%s' "$respuesta" | campo id 2>/dev/null || true)"
  [ -n "$LIBRO_CREADO" ] || err "No se pudo crear el libro '$titulo'. Respuesta: $respuesta"
  ok "libro virtual creado (id $LIBRO_CREADO, isbn $isbn): $titulo"
}

crear_libro_virtual "Libro virtual de prueba (efectivo)"
ID_LIBRO="$LIBRO_CREADO"

# ---------------------------------------------------------------- 5. pedido
say "5. El lector lo pide pagando en la tienda (efectivo)"
PEDIDO="$(curl -sS --max-time 30 -X POST "$API/accesos-virtuales" \
    -H "Authorization: Bearer $TOKEN_LECTOR" -H 'Content-Type: application/json' \
    -d "{\"id_libro\":$ID_LIBRO,\"tipo\":\"ALQUILER\",\"dias_alquiler\":7,\"metodo_pago\":\"EFECTIVO\"}")"
ID_PEDIDO="$(printf '%s' "$PEDIDO" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const o = JSON.parse(s); console.log(o.acceso ? o.acceso.id : ""); });
')"
CODIGO="$(printf '%s' "$PEDIDO" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const o = JSON.parse(s); console.log(o.acceso ? (o.acceso.codigo_retiro || "") : ""); });
')"
COLA="$(printf '%s' "$PEDIDO" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const o = JSON.parse(s); console.log(o.acceso ? (o.acceso.numero_cola || "") : ""); });
')"
[ -n "$ID_PEDIDO" ] && [ -n "$CODIGO" ] || err "No se pudo crear el pedido. Respuesta: $PEDIDO"
ok "pedido creado (id $ID_PEDIDO)"
ok "numero de cola: $COLA | codigo de 24 horas: $CODIGO"

# ---------------------------------------------------------------- 6. mostrador
say "6. El personal busca la orden con el codigo del cliente"
BUSCADA="$(curl -sS --max-time 20 "$API/accesos-virtuales/codigo/$CODIGO" \
    -H "Authorization: Bearer $TOKEN_BIBLIO")"
ESTADO="$(printf '%s' "$BUSCADA" | campo estado)"
[ "$ESTADO" = "PENDIENTE_PAGO" ] || err "La orden deberia estar PENDIENTE_PAGO y esta en '$ESTADO'. Respuesta: $BUSCADA"
ok "el codigo encontro la orden (PENDIENTE_PAGO)"

say "7. El personal cobra la orden"
COBRADA="$(curl -sS --max-time 30 -X POST "$API/accesos-virtuales/$ID_PEDIDO/cobrar" \
    -H "Authorization: Bearer $TOKEN_BIBLIO")"
ESTADO="$(printf '%s' "$COBRADA" | campo estado)"
[ "$ESTADO" = "PAGADO" ] || err "La orden deberia quedar PAGADA y quedo en '$ESTADO'. Respuesta: $COBRADA"
DIAS="$(printf '%s' "$COBRADA" | campo dias_restantes)"
ok "orden cobrada: PAGADO, $DIAS dias restantes"

# ---------------------------------------------------------------- 8. yape
say "8. El lector pide con Yape, sube la captura y el personal la aprueba"

# Segundo libro: el lector ya tiene acceso al primero ("ya tienes acceso").
crear_libro_virtual "Libro virtual de prueba (yape)"
ID_LIBRO_YAPE="$LIBRO_CREADO"

# El QR de Yape es publico (lo carga el <img> del navegador sin token).
CODIGO_QR="$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' "$API/pagos/yape/qr")"
[ "$CODIGO_QR" = "200" ] || err "El QR de Yape respondio $CODIGO_QR (se espera 200)"
ok "el QR de Yape se sirve como imagen publica (HTTP $CODIGO_QR)"

# Captura minima (PNG de 1x1) que simula la foto del pago en la app de Yape.
node -e '
  const fs = require("fs");
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "base64");
  fs.writeFileSync(process.argv[1], png);
' "$TMP/comprobante.png"

PEDIDO_YAPE="$(curl -sS --max-time 30 -X POST "$API/accesos-virtuales" \
    -H "Authorization: Bearer $TOKEN_LECTOR" -H 'Content-Type: application/json' \
    -d "{\"id_libro\":$ID_LIBRO_YAPE,\"tipo\":\"COMPRA\",\"metodo_pago\":\"YAPE\"}")"
ID_PEDIDO_YAPE="$(printf '%s' "$PEDIDO_YAPE" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const o = JSON.parse(s); console.log(o.acceso ? o.acceso.id : ""); });
')"
[ -n "$ID_PEDIDO_YAPE" ] || err "No se pudo crear el pedido con Yape. Respuesta: $PEDIDO_YAPE"
ok "pedido con Yape creado (id $ID_PEDIDO_YAPE, compra de S/ 35.00)"

COMPROBANTE="$(curl -sS --max-time 60 -X POST "$API/accesos-virtuales/$ID_PEDIDO_YAPE/comprobante" \
    -H "Authorization: Bearer $TOKEN_LECTOR" \
    -F "comprobante=@$TMP/comprobante.png;type=image/png" \
    -F "referencia_yape=987654321")"
ID_COMPROBANTE="$(printf '%s' "$COMPROBANTE" | campo id 2>/dev/null || true)"
[ -n "$ID_COMPROBANTE" ] || err "No se pudo subir el comprobante. Respuesta: $COMPROBANTE"
ok "comprobante subido por el lector (id $ID_COMPROBANTE)"

# El pedido NO cambia de estado al subir el comprobante: sigue pendiente.
ESTADO_YAPE="$(printf '%s' "$PEDIDO_YAPE" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => { const o = JSON.parse(s); console.log(o.acceso ? o.acceso.estado : ""); });
')"
[ "$ESTADO_YAPE" = "PENDIENTE_PAGO" ] || err "El pedido deberia seguir PENDIENTE_PAGO y esta en '$ESTADO_YAPE'"
ok "el pedido sigue PENDIENTE_PAGO (el comprobante no cambia el estado)"

PENDIENTES="$(curl -sS --max-time 20 "$API/comprobantes/pendientes" -H "Authorization: Bearer $TOKEN_BIBLIO")"
printf '%s' "$PENDIENTES" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => {
    const l = JSON.parse(s);
    const e = l.find((x) => String(x.id) === process.argv[1]);
    process.exit(e ? 0 : 1);
  });
' "$ID_COMPROBANTE" || err "El comprobante no sale en los pendientes del personal. Respuesta: $PENDIENTES"
ok "el personal ve el comprobante en la lista de pendientes"

IMAGEN="$(curl -sS --max-time 30 "$API/comprobantes/$ID_COMPROBANTE/imagen" \
    -H "Authorization: Bearer $TOKEN_BIBLIO" | campo url)"
[ -n "$IMAGEN" ] || err "No se devolvio la URL firmada de la captura del comprobante"
ok "la captura se ve con una URL firmada"

APROBADO="$(curl -sS --max-time 30 -X POST "$API/comprobantes/$ID_COMPROBANTE/aprobar" \
    -H "Authorization: Bearer $TOKEN_BIBLIO")"
ESTADO_COMP="$(printf '%s' "$APROBADO" | campo estado)"
[ "$ESTADO_COMP" = "APROBADO" ] || err "El comprobante deberia quedar APROBADO y quedo en '$ESTADO_COMP'. Respuesta: $APROBADO"
ok "comprobante aprobado por el personal"

ESTADO_FINAL="$(curl -sS --max-time 20 "$API/accesos-virtuales/mis-libros" -H "Authorization: Bearer $TOKEN_LECTOR" \
    | node -e '
      let s = "";
      process.stdin.on("data", (d) => (s += d));
      process.stdin.on("end", () => {
        const o = JSON.parse(s).find((x) => String(x.id) === process.argv[1]);
        console.log(o ? o.estado : "no-encontrado");
      });
    ' "$ID_PEDIDO_YAPE")"
[ "$ESTADO_FINAL" = "PAGADO" ] || err "El pedido con Yape deberia quedar PAGADO y quedo en '$ESTADO_FINAL'"
ok "el pedido con Yape quedo PAGADO (el lector ya puede abrir el PDF)"

# ---------------------------------------------------------------- 9. lector
say "9. Lo que ve el lector"
MIS="$(curl -sS --max-time 20 "$API/accesos-virtuales/mis-libros" -H "Authorization: Bearer $TOKEN_LECTOR")"
printf '%s' "$MIS" | grep -q "$ID_PEDIDO" || err "El libro no aparece en la lista del lector. Respuesta: $MIS"
ok "el libro aparece en 'Mis libros virtuales'"

AVISO="$(curl -sS --max-time 20 "$API/notificaciones" -H "Authorization: Bearer $TOKEN_LECTOR")"
if printf '%s' "$AVISO" | grep -qi 'Felicidades'; then
  ok "llego el aviso de felicitaciones"
else
  err "No llego el aviso de 'Felicidades'. Respuesta: $AVISO"
fi

say "10. El lector abre el PDF (URL firmada de 15 minutos)"
URL_PDF="$(curl -sS --max-time 30 "$API/accesos-virtuales/$ID_PEDIDO/pdf" \
    -H "Authorization: Bearer $TOKEN_LECTOR" | campo url)"
[ -n "$URL_PDF" ] || err "No se devolvio la URL firmada del PDF"
CODIGO_HTTP="$(curl -sS --max-time 30 -o /dev/null -w '%{http_code}' "$URL_PDF")"
[ "$CODIGO_HTTP" = "200" ] || err "La URL firmada del PDF respondio $CODIGO_HTTP (se espera 200)"
ok "el PDF se descarga desde R2 (HTTP $CODIGO_HTTP)"

# ---------------------------------------------------------------- 11. caja
say "11. La caja: los movimientos del dinero y el arqueo"
DETALLE="$(curl -sS --max-time 20 "$API/cajas/$ID_CAJA" -H "Authorization: Bearer $TOKEN_BIBLIO")"

# Los dos cobros de esta prueba tienen que estar en los movimientos, con su monto
# y su metodo de pago (el del mostrador en efectivo, el de Yape con Yape).
printf '%s' "$DETALLE" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => {
    const o = JSON.parse(s);
    const movs = o.movimientos || [];
    const a = movs.find((m) => String(m.id_acceso_virtual) === process.argv[1]);
    const b = movs.find((m) => String(m.id_acceso_virtual) === process.argv[2]);
    const falla = [];
    if (!a) falla.push("no hay movimiento del pedido en efectivo");
    else {
      if (Number(a.monto) !== 17.50) falla.push("el cobro en efectivo no es S/ 17.50 (es " + a.monto + ")");
      if (a.metodo_pago !== "EFECTIVO") falla.push("el cobro en efectivo no dice EFECTIVO (dice " + a.metodo_pago + ")");
    }
    if (!b) falla.push("no hay movimiento del pedido con Yape");
    else {
      if (Number(b.monto) !== 35.00) falla.push("el cobro con Yape no es S/ 35.00 (es " + b.monto + ")");
      if (b.metodo_pago !== "YAPE") falla.push("el cobro con Yape no dice YAPE (dice " + b.metodo_pago + ")");
    }
    if (falla.length) {
      console.error(falla.join("; "));
      process.exit(1);
    }
  });
' "$ID_PEDIDO" "$ID_PEDIDO_YAPE" || err "Los movimientos de la caja no cuadran con los cobros de la prueba"
ok "los dos cobros estan en la caja con su monto y su metodo de pago"

# El arqueo: se cuenta el efectivo que hay en el cajon y se compara con lo esperado.
ESPERADO="$(printf '%s' "$DETALLE" | node -e '
  let s = "";
  process.stdin.on("data", (d) => (s += d));
  process.stdin.on("end", () => {
    const o = JSON.parse(s);
    console.log(Number(o.caja.esperado_efectivo).toFixed(2));
  });
')"
ok "en el cajon deberia haber S/ $ESPERADO de efectivo (monto inicial + lo cobrado en efectivo)"

# Se declara UN SOLO MAS a proposito: el sistema tiene que avisar del sobrante.
DECLARADO="$(node -e 'console.log((Number(process.argv[1]) + 1).toFixed(2))' "$ESPERADO")"
CERRADA="$(curl -sS --max-time 30 -X POST "$API/cajas/$ID_CAJA/cerrar" \
    -H "Authorization: Bearer $TOKEN_BIBLIO" -H 'Content-Type: application/json' \
    -d "{\"monto_final_declarado\":$DECLARADO,\"observaciones\":\"Cierre de la prueba automatica\"}")"
ESTADO_CAJA="$(printf '%s' "$CERRADA" | campo estado)"
[ "$ESTADO_CAJA" = "CERRADA" ] || err "La caja deberia quedar CERRADA y quedo en '$ESTADO_CAJA'. Respuesta: $CERRADA"
DIFERENCIA="$(printf '%s' "$CERRADA" | campo diferencia)"
[ "$DIFERENCIA" = "1" ] || [ "$DIFERENCIA" = "1.00" ] \
  || err "El arqueo deberia avisar de un sobrante de S/ 1.00 y dice '$DIFERENCIA'. Respuesta: $CERRADA"
ok "caja cerrada y el arqueo avisó del sobrante (S/ $DIFERENCIA: se contaron S/ $DECLARADO y se esperaban S/ $ESPERADO)"

# ---------------------------------------------------------------- 12. en plomo
say "12. Al vencer el alquiler el libro sale 'en plomo'"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
if [ -x "$MYSQL" ]; then
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL" -u "$DB_USER" "$DB_NAME" \
    -e "UPDATE accesos_virtuales SET fecha_fin = DATE_SUB(CURDATE(), INTERVAL 1 DAY) WHERE id = $ID_PEDIDO;" \
    >/dev/null 2>&1 || err "No se pudo vencer el alquiler en la base de datos"
  VENCIDO="$(curl -sS --max-time 20 "$API/accesos-virtuales/mis-libros" -H "Authorization: Bearer $TOKEN_LECTOR" \
    | node -e '
      let s = "";
      process.stdin.on("data", (d) => (s += d));
      process.stdin.on("end", () => {
        const o = JSON.parse(s).find((x) => String(x.id) === process.argv[1]);
        console.log(o && o.vencido ? "si" : "no");
      });
    ' "$ID_PEDIDO")"
  [ "$VENCIDO" = "si" ] || err "El alquiler vencido no salio marcado como vencido"
  ok "el alquiler vencido aparece como vencido (sale en plomo en el perfil)"

  if curl -sS --max-time 20 "$API/accesos-virtuales/$ID_PEDIDO/pdf" -H "Authorization: Bearer $TOKEN_LECTOR" \
      -o /dev/null -w '%{http_code}' | grep -q '400'; then
    ok "un libro vencido ya no se puede abrir"
  else
    err "un libro vencido todavia se puede abrir"
  fi
else
  info "no hay cliente de MySQL: se omite el paso de vencer el alquiler"
fi

printf '\n\033[32mTODO OK\033[0m: el flujo de libros virtuales funciona de punta a punta.\n'
info "los libros de prueba se dejaron creados con id $ID_LIBRO y $ID_LIBRO_YAPE (se pueden borrar desde el panel)"
