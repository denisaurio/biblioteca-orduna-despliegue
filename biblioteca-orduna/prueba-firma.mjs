// Comprobacion de la firma SigV4 contra el vector de ejemplo de AWS
// ("GET Object" de la documentacion Signature Version 4). Se borra despues.
import { firmar } from './configurar-almacenamiento.mjs';

const esperado = 'f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41';

const r = firmar({
  metodo: 'GET',
  ruta: '/test.txt',
  consulta: 'lifecycle=',
  cuerpo: '',
  host: 'examplebucket.s3.amazonaws.com',
  region: 'us-east-1',
  servicio: 's3',
  claveAcceso: 'AKIAIOSFODNN7EXAMPLE',
  claveSecreta: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
  fecha: new Date('2013-05-24T00:00:00Z'),
  extra: { range: 'bytes=0-9' },
});

const firma = /Signature=([0-9a-f]+)/.exec(r.authorization)[1];
console.log('calculada:', firma);
console.log('esperada :', esperado);
console.log(firma === esperado ? 'OK - coincide con el vector de AWS' : 'FALLO - no coincide');
process.exit(firma === esperado ? 0 : 1);
