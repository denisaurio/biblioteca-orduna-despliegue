# Sistema de Biblioteca Orduña

Sistema de gestión de biblioteca con **backend Spring Boot + JWT** y **frontend React + Vite**.
Implementa los roles **LECTOR / BIBLIOTECARIO / GERENTE**, las reglas de negocio, las 14 pantallas
del prototipo de Figma (*Seguimiento 2*) y las tablas del modelo (`id_usuario`, `contrasena_hash`,
`libros_autores`, `libros_generos`, `bitacora_turnos`, `configuracion_multas`, …).

## Estructura

```
biblioteca-orduna-completo/
├── biblioteca-orduna/
│   ├── pom.xml, src/      ← BACKEND (Spring Boot, puerto 8080)
│   └── frontend/          ← FRONTEND (React + Vite, puerto 5173)
├── package-lock.json
└── README.md
```

## Requisitos

- Java 17+
- Maven 3.9+
- MySQL 8+
- Node.js 18+

## Puesta en marcha

### 1. Base de datos (una sola vez)

```sql
CREATE DATABASE biblioteca_orduna;
```

> Si ya tenías una versión anterior de la base, bórrala y créala de nuevo
> (`DROP DATABASE biblioteca_orduna; CREATE DATABASE biblioteca_orduna;`).

Usuario/clave en `biblioteca-orduna/src/main/resources/application.properties`.

### 2. Backend (terminal 1)

```bash
cd biblioteca-orduna
mvn spring-boot:run
```

Hibernate crea las tablas y `data.sql` carga usuarios, configuración y géneros.
Prueba: http://localhost:8080/ debe responder `"estado": "funcionando"`.

### 3. Frontend (terminal 2)

```bash
cd biblioteca-orduna/frontend
cp .env.example .env      # ajusta VITE_API_URL si es necesario
npm install
npm run dev
```

Abre http://localhost:5173

## Usuarios de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| GERENTE | admin@biblioteca.pe | admin123 |
| BIBLIOTECARIO | biblio@biblioteca.pe | biblio123 |
| LECTOR | lector@biblioteca.pe | lector123 |

## Horario de caja

El gerente configura en **Configuración de caja** un horario por día (encargado, hora de inicio y
hora de fin; p. ej. Lunes–Viernes 08:00–20:00 y Sábado 08:00–15:00). El bibliotecario ve en la
pantalla Caja a qué hora se le cierra la caja, y el backend la cierra sola al pasarse esa hora.

## Documentación

- Backend (endpoints, reglas de negocio, errores): [`biblioteca-orduna/LEEME.md`](biblioteca-orduna/LEEME.md)
- Cómo ejecutar: [`biblioteca-orduna/COMO-EJECUTAR.md`](biblioteca-orduna/COMO-EJECUTAR.md)
- Frontend (pantallas y endpoints): [`biblioteca-orduna/frontend/README.md`](biblioteca-orduna/frontend/README.md)

## Tecnologías

- **Backend:** Spring Boot 3.3, Spring Security, JWT (jjwt), Spring Data JPA, MySQL, BCrypt.
- **Frontend:** React 18, Vite, React Router, Axios.
