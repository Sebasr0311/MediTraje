# DATABASE.md — MediTriaje 2.0

> La guía técnica completa de base de datos Oracle ATP, modelo relacional y migraciones se encuentra centralizada en:  
> 🔗 **[`docs/database/DATABASE.md`](docs/database/DATABASE.md)**

Para consultar los lineamientos específicos:
- [Aprovisionamiento y Wallet de Oracle ATP](docs/database/DATABASE.md#1-descarga-y-ubicación-del-wallet)
- [Usuarios y Segregación de Privilegios (OWNER vs. APP)](docs/database/DATABASE.md#2-aprovisionamiento-de-usuarios-en-oracle-atp-adr-012)
- [Formato de URL JDBC y Variables de Entorno](docs/database/DATABASE.md#3-formato-de-la-url-jdbc-y-variables-de-entorno)
- [Pool HikariCP y Time Zone `America/Bogota`](docs/database/DATABASE.md#4-configuración-del-pool-hikaricp-y-sesión-adr-005--adr-012)
- [Gestión de Migraciones con Flyway](docs/database/DATABASE.md#8-gestión-de-migraciones-con-flyway-m13--adr-004)
- [Inventario Consolidado de Migraciones V001 a V009](docs/database/DATABASE.md#9-inventario-consolidado-de-migraciones-aplicadas-v001-a-v009)
- [Triggers PL/SQL de Inmutabilidad Clínica](docs/database/DATABASE.md#10-triggers-plsql-de-inmutabilidad-clínica)
- [Matriz de Permisos de Runtime (MEDITRIAJE_APP)](docs/database/DATABASE.md#11-segregación-de-privilegios-de-meditriaje_app-adr-012)

Otros recursos de base de datos disponibles:
- [`docs/database/MODELO_RELACIONAL.md`](docs/database/MODELO_RELACIONAL.md) — Definición DDL y normalización a 3FN.
- [`docs/database/MER.md`](docs/database/MER.md) — Diagrama Entidad-Relación en Mermaid.
- [`database/seeds/README.md`](database/seeds/README.md) — Catálogo de semillas y datos ficticios de desarrollo.
