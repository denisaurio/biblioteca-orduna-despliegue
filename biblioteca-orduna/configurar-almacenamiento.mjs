// Configura el almacenamiento de archivos (Cloudflare R2) de la biblioteca.
//
// Que hace (con las mismas credenciales que usa la aplicacion):
//   1. Lee el .env (credenciales y nombres de los buckets).
//   2. Mira si existen los dos buckets: el publico de portadas y el privado de PDF.
//   3. Si falta el bucket PRIVADO de los PDF, lo crea (con --crear).
//   4. Prueba que el token puede escribir (sube un archivo de 0 bytes y lo borra).
//   5. Si la URL publica de las portadas esta puesta, prueba que la imagen se
//      descarga desde esa URL.
//   6. Dice que falta y donde se arregla.
//
// Como se usa:
//   node configurar-almacenamiento.mjs          (solo mira y prueba)
//   node configurar-almacenamiento.mjs --crear  (ademas crea el bucket que falte)
//
// Las credenciales nunca se muestran por pantalla y solo se usan contra
// Cloudflare R2, que es su unico destino.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createHmac, createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RUTA_ENV = join(AQUI, '.env');
const CREAR = process.argv.includes('--crear');

/** Donde se mira el panel de R2: lo que falte se activa ahi. */
const PANEL = 'https://dash.cloudflare.com/?to=/:account/r2';

// ---------------------------------------------------------------- utilidades

function leerEnv(ruta) {
  if (!existsSync(ruta)) {
    console.error('No existe el archivo ' + ruta);
    console.error('Copia la plantilla: cp .env.example .env y completa las credenciales.');
    process.exit(1);
  }
  const valores = {};
  for (const linea of readFileSync(ruta, 'utf8').split(/\r?\n/)) {
    const t = linea.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i < 1) continue;
    valores[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return valores;
}

/** Escribe (o reemplaza) una variable del .env sin tocar el resto del archivo. */
function escribirEnv(ruta, clave, valor) {
  let texto = readFileSync(ruta, 'utf8');
  const re = new RegExp('^' + clave + '=.*$', 'm');
  texto = re.test(texto)
    ? texto.replace(re, clave + '=' + valor)
    : texto.replace(/\s*$/, '\n' + clave + '=' + valor + '\n');
  writeFileSync(ruta, texto);
}

// ------------------------------------------------------- firma AWS (SigV4)
// R2 habla el protocolo S3, asi que las llamadas se firman como en AWS:
// region "auto" y la ruta con el nombre del bucket delante (path style).

export function firmar({ metodo, ruta, consulta, cuerpo, host, region, servicio, claveAcceso, claveSecreta, fecha, extra }) {
  const marcaTiempo = fecha.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const dia = marcaTiempo.slice(0, 8);
  const hashCuerpo = createHash('sha256').update(cuerpo || '').digest('hex');

  const encabezados = {
    ...(extra || {}),
    host,
    'x-amz-content-sha256': hashCuerpo,
    'x-amz-date': marcaTiempo,
  };
  const nombres = Object.keys(encabezados).sort();
  const canonicos = nombres.map((n) => n + ':' + encabezados[n] + '\n').join('');
  const firmados = nombres.join(';');

  const peticion = [metodo, ruta, consulta, canonicos, firmados, hashCuerpo].join('\n');
  const ambito = dia + '/' + region + '/' + servicio + '/aws4_request';
  const aFirmar = ['AWS4-HMAC-SHA256', marcaTiempo, ambito, createHash('sha256').update(peticion).digest('hex')].join('\n');

  const kFecha = createHmac('sha256', 'AWS4' + claveSecreta).update(dia).digest();
  const kRegion = createHmac('sha256', kFecha).update(region).digest();
  const kServicio = createHmac('sha256', kRegion).update(servicio).digest();
  const kFirma = createHmac('sha256', kServicio).update('aws4_request').digest();
  const firma = createHmac('sha256', kFirma).update(aFirmar, 'utf8').digest('hex');

  return {
    ...encabezados,
    authorization:
      'AWS4-HMAC-SHA256 Credential=' + claveAcceso + '/' + ambito +
      ', SignedHeaders=' + firmados + ', Signature=' + firma,
  };
}

/** Una llamada al S3 de R2 con la firma puesta. */
async function s3(env, metodo, ruta, consulta = '', cuerpo = '') {
  const host = env.R2_ACCOUNT_ID + '.r2.cloudflarestorage.com';
  const encabezados = firmar({
    metodo,
    ruta,
    consulta,
    cuerpo,
    host,
    region: 'auto',
    servicio: 's3',
    claveAcceso: env.R2_ACCESS_KEY_ID,
    claveSecreta: env.R2_SECRET_ACCESS_KEY,
    fecha: new Date(),
  });
  const url = 'https://' + host + ruta + (consulta ? '?' + consulta : '');
  const respuesta = await fetch(url, {
    method: metodo,
    headers: { ...encabezados, 'content-type': 'application/octet-stream' },
    body: metodo === 'GET' || metodo === 'HEAD' ? undefined : cuerpo,
  });
  const texto = await respuesta.text();
  return { codigo: respuesta.status, cuerpo: texto };
}

/** El codigo de error de S3 va en el XML: <Code>NoSuchBucket</Code>. */
function codigoError(xml) {
  const m = /<Code>([^<]+)<\/Code>/.exec(xml || '');
  return m ? m[1] : '';
}

// -------------------------------------------------------------------- main

async function main() {
  const env = leerEnv(RUTA_ENV);

  for (const clave of ['R2_ACCOUNT_ID', 'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY']) {
    if (!env[clave]) {
      console.error('Falta ' + clave + ' en el .env.');
      console.error('Se saca de Cloudflare > R2 > Manage R2 Tokens (Overview da el Account ID).');
      process.exit(1);
    }
  }

  const bucketPortadas = env.R2_BUCKET_PORTADAS || 'biblioteca';
  const bucketPdfs = env.R2_BUCKET_PDFS || 'biblioteca-pdfs';

  console.log('\n== Almacenamiento de archivos (Cloudflare R2) ==\n');
  console.log('Bucket de portadas (publico) : ' + bucketPortadas);
  console.log('Bucket de PDFs (privado)    : ' + bucketPdfs);
  console.log('');

  // ---- 1. Que buckets existen
  const existe = async (nombre) => {
    const r = await s3(env, 'HEAD', '/' + nombre);
    return { ok: r.codigo === 200, codigo: r.codigo };
  };

  const hayPortadas = await existe(bucketPortadas);
  if (hayPortadas.ok) {
    console.log('[OK] El bucket de portadas "' + bucketPortadas + '" existe.');
  } else {
    console.log('[ ] El bucket "' + bucketPortadas + '" NO existe (codigo ' + hayPortadas.codigo + ').');
    if (CREAR) {
      console.log('    Creando...');
      const r = await s3(env, 'PUT', '/' + bucketPortadas);
      console.log(r.codigo === 200 || r.codigo === 204
        ? '    Creado.'
        : '    No se pudo crear (' + r.codigo + ' ' + codigoError(r.cuerpo) + ').');
    } else {
      console.log('    Crealo en el panel: R2 > Create bucket > "' + bucketPortadas + '"');
    }
  }

  // ---- 2. El bucket privado de los PDF (este es el que faltaba)
  const hayPdfs = await existe(bucketPdfs);
  if (hayPdfs.ok) {
    console.log('[OK] El bucket privado de PDFs "' + bucketPdfs + '" existe.');
  } else {
    console.log('\n[ ] El bucket PRIVADO de PDFs "' + bucketPdfs + '" NO existe (codigo ' + hayPdfs.codigo + ').');
    console.log('    Sin el no se pueden subir los PDF de los libros virtuales ni los');
    console.log('    comprobantes de Yape.');
    if (CREAR) {
      console.log('    Creando "' + bucketPdfs + '" (privado, sin acceso publico)...');
      const r = await s3(env, 'PUT', '/' + bucketPdfs);
      if (r.codigo === 200 || r.codigo === 204) {
        console.log('    Creado.');
      } else if (codigoError(r.cuerpo) === 'BucketAlreadyExists') {
        console.log('    Ya existia (lo ha creado el panel).');
      } else {
        console.log('    No se pudo crear (' + r.codigo + ' ' + codigoError(r.cuerpo) + ').');
        console.log('    Crealo a mano en el panel: R2 > Create bucket > "' + bucketPdfs + '"');
      }
    } else {
      console.log('    Crealo en el panel: R2 > Create bucket > "' + bucketPdfs + '"');
      console.log('    (sin acceso publico: los PDF se sirven con URL firmada)');
      console.log('    O vuelve a ejecutar esto con --crear.');
    }
  }

  // ---- 3. Prueba de escritura (el token puede subir archivos?)
  console.log('\n-- Prueba de escritura --');
  const clavePrueba = '_pruebas/configuracion.txt';
  const subida = await s3(env, 'PUT', '/' + bucketPortadas + '/' + clavePrueba, '', 'prueba de escritura');
  if (subida.codigo === 200 || subida.codigo === 204) {
    console.log('[OK] El token puede escribir en "' + bucketPortadas + '".');
    await s3(env, 'DELETE', '/' + bucketPortadas + '/' + clavePrueba);
  } else {
    console.log('[X] No se pudo escribir (' + subida.codigo + ' ' + codigoError(subida.cuerpo) + ').');
    console.log('    El token de R2 necesita permiso de escritura (Object Read & Write).');
  }

  // ---- 4. La URL publica de las portadas
  console.log('');
  const base = (env.R2_URL_PUBLICO_PORTADAS || '').trim().replace(/\/+$/, '');
  if (!base) {
    console.log('[ ] Falta R2_URL_PUBLICO_PORTADAS en el .env.');
    console.log('    Sin ella la portada se guarda pero el navegador no la muestra.');
    console.log('    Como se saca: R2 > bucket "' + bucketPortadas + '" > Settings >');
    console.log('    Public Development URL > Enable. Copia la URL (https://pub-....r2.dev)');
    console.log('    en el .env como R2_URL_PUBLICO_PORTADAS=...');
  } else {
    console.log('[OK] URL publica de las portadas: ' + base);
    // Se sube una imagen de 1x1 en PNG y se descarga por la URL publica.
    const png = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
      'base64');
    const claveImg = '_pruebas/configuracion.png';
    const subidaImg = await s3(env, 'PUT', '/' + bucketPortadas + '/' + claveImg, '', png);
    if (subidaImg.codigo === 200 || subidaImg.codigo === 204) {
      const descarga = await fetch(base + '/' + claveImg);
      if (descarga.ok) {
        console.log('[OK] La imagen se descarga por la URL publica.');
      } else {
        console.log('[X] La URL publica no responde (' + descarga.status + ').');
        console.log('    Revisa que la Public Development URL del bucket este activada.');
      }
      await s3(env, 'DELETE', '/' + bucketPortadas + '/' + claveImg);
    }
  }

  // ---- 5. Resumen
  console.log('\n-- Que falta --');
  const falta = [];
  if (!hayPortadas.ok) falta.push('el bucket "' + bucketPortadas + '"');
  if (!hayPdfs.ok) falta.push('el bucket privado "' + bucketPdfs + '"');
  if (!base) falta.push('la URL publica de las portadas (R2_URL_PUBLICO_PORTADAS)');
  if (falta.length === 0) {
    console.log('    Nada: los dos buckets estan y las portadas tienen URL publica.');
  } else {
    console.log('    Falta: ' + falta.join(', ') + '.');
    console.log('    Se resuelve en el panel de Cloudflare: ' + PANEL);
  }

  console.log('\nDespues, reinicia el backend para que cargue el .env:');
  console.log('  Ctrl+C y "mvn spring-boot:run"');
  console.log('Y en Configuracion > Almacenamiento sale el estado y el boton de prueba.\n');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error('\nERROR: ' + e.message + '\n');
    process.exit(1);
  });
}
