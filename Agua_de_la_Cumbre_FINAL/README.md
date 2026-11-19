# Agua de la Cumbre — Camunda 7 embebido

Esta es la carpeta final preparada para publicar el código y la documentación del proyecto. El informe de los once criterios está en `Informe_Proyecto_Final_Agua_de_la_Cumbre.tex` y `Informe_Proyecto_Final_Agua_de_la_Cumbre.pdf`. Las fuentes de contexto y la rejilla del curso están en `docs/`; `docs/ARQUITECTURA_Y_ENTREGA.md` indica qué evidencias están disponibles y qué actividades requieren aún demostración o publicación de componentes. No se incluyen la base de datos, los resultados de compilación ni las diapositivas del avance anterior.

Motor Camunda 7.24.0 embebido en Spring Boot 3.5.5 (Java 17/21), base H2 y portal Thymeleaf con el logo de Agua de la Cumbre. Integra los tres procesos priorizados del documento "Identificación - Proyecto", ya corregidos y alineados con la rúbrica:

| Proceso | Clave BPMN | Subprocesos (Call Activity) |
|---|---|---|
| PR-CO-03 Distribuir y comercializar el producto terminado | `distribuir_comercializar` | `intercambio_envases` (canal indirecto) |
| PR-SO-04 Asignar y recuperar equipos de campo | `equipos_campo` | `mantenimiento_equipo` |
| PR-SO-05 Facturar y recaudar las ventas (cartera) | `facturar_recaudar` | `conciliar_cartera`, `revisar_soporte` |

Siete definiciones BPMN, cuatro decisiones DMN, 18 formularios Camunda Forms y un trabajador externo JavaScript.

## Ejecutar en Windows

1. Extraer completamente el ZIP en una carpeta nueva (no copiar la carpeta `data` de versiones anteriores).
2. Tener JDK 17 o 21 y Node.js 18 o superior. Verificar `java -version`, `javac -version`, `node --version`.
3. En la carpeta que contiene `pom.xml`: `./mvnw.cmd spring-boot:run` (o doble clic en `iniciar-servidor.cmd`). Esperar `Started Application`.
4. En otra terminal: `cd worker-js` y `node worker.cjs` (o `iniciar-worker.cmd`). No requiere `npm install`.
5. Abrir http://localhost:8080/portal e ingresar **admin / admin**.

En Linux/macOS: `chmod +x mvnw`, `./mvnw spring-boot:run` y `node worker-js/worker.cjs`.

### Acceso al portal

- **Cliente externo:** en `/registro` crea usuario y contraseña (mínimo 8 caracteres). Luego puede iniciar pedidos de distribución o solicitudes de equipo y seguir solo sus instancias activas o finalizadas. Cuando su caso espera una devolución o un pago, aparece el botón correspondiente.
- **Personal interno:** para la demostración, las cuentas `vendedores`, `bodega`, `administracion`, `tesoreria` y `tecnicos` tienen la contraseña `demo1234` (configurable con `DEMO_USER_PASSWORD` antes de crear la base). Cada una atiende las tareas de su grupo candidato; los eventos se limitan por rol.
- **Administrador:** `admin` / `admin` en una base nueva, o la clave definida por `CAMUNDA_ADMIN_PASSWORD`. Solo esta cuenta accede a Cockpit y REST del motor. El trabajador Node.js usa esa misma contraseña.

Las cuentas de demostración se crean al arrancar la aplicación. Para probar los roles con datos nuevos, use una carpeta sin `data/` o una base nueva; no comparta las claves predeterminadas fuera del equipo local.

### Configuración

`application.yaml` define `127.0.0.1:8080` y H2 en `data/agua-cumbre.mv.db`. Variables de entorno: `CAMUNDA_ADMIN_PASSWORD`, `DEMO_USER_PASSWORD`, `DB_PASSWORD`, `PLAZO_ESPERA_LOTE`, `PLAZO_ASIGNACION`, `PLAZO_PAGO`. El trabajador admite `CAMUNDA_URL` y usa `CAMUNDA_ADMIN_PASSWORD`. Node.js no requiere instalar paquetes.

En Linux, si el wrapper no puede descargar Maven, use `mvn test` o `mvn spring-boot:run` con Maven instalado. En Windows, `mvnw.cmd` descarga Maven la primera vez si hay conexión.

La arquitectura, responsables y correspondencia con la rejilla final están en `docs/ARQUITECTURA_Y_ENTREGA.md`.

## Qué cambió frente a la versión 2.0.0

- Se reemplazaron `liberacion_distribucion`, `entrega_cliente`, `equipos_campo` (PR-CO-05) y `finanzas_costos` (PR-G-02) por los procesos corregidos. La numeración ahora coincide con el documento: PR-CO-03, PR-SO-04 y PR-SO-05.
- **Inicio por evento de mensaje.** Los tres procesos empiezan con un *message start event*. El portal inicia cada proceso enviando el mensaje (`PedidoRecibido`, `SolicitudEquipo`, `VentaEntregada`) con la llave de negocio. PR-SO-05 arranca solo cuando PR-CO-03 termina; también puede iniciarse a mano desde el portal.
- **Eventos externos en vez de autorizaciones.** Las compuertas basadas en eventos esperan hechos del negocio: lote liberado (PR-CO-02), devolución del equipo y pago del cliente. El portal envía el mensaje a la instancia que espera. Cada espera tiene su temporizador.
- **Persistencia en JavaDelegate.** Inventario de lotes, pedidos, equipos, cuentas y pagos se actualizan con JDBC dentro de la transacción del motor (`distribucionDelegate`, `equiposDelegate`, `carteraDelegate`, y `ValidarPagoDelegate` como Java Class).
- **JavaScript donde no se toca la base.** El trabajador Node.js atiende tres External Tasks: `trasladar-venta` (envía `VentaEntregada` e inicia PR-SO-05, de forma idempotente), `constancia-acta-equipo` (huella SHA-256 del acta de entrega) y `enviar-factura` (contenido y huella de la factura).
- **Formularios por modelo.** El portal toma el formulario de cada tarea del `camunda:formRef` del BPMN desplegado; ya no hay un mapa fijo por id de tarea.
- **Registros** muestran lotes liberados/vencidos, canal y comprobante de cada pedido, y los últimos movimientos.

## Demostración

Datos iniciales: lotes `LOTE-DEMO` (liberado, vigente, 100 unidades), `LOTE-PENDIENTE` (sin liberar, 50), `LOTE-VENCIDO` (vencido, 30); equipos `EQ-001`, `EQ-002`, `EQ-003` disponibles.

Plazos de demostración (`application.yaml`, sección `agua.plazos`): espera de lote 1 minuto, asignación de equipo 2 minutos, plazo de pago 1 minuto. Para operación real se proponen P3D, P30D y P30D. También se pueden cambiar con las variables de entorno `PLAZO_ESPERA_LOTE`, `PLAZO_ASIGNACION` y `PLAZO_PAGO`.

### 1. PR-CO-03 → PR-SO-05 (venta directa)

1. Iniciar **Distribuir y comercializar** con `pedidoId=DEMO-300`, `clienteId=CLI-001`.
2. Tarea *Registrar el pedido*: vendedor `VEN-001`, zona `Centro`, canal `DIRECTO`, lote `LOTE-DEMO`, cantidad `2`, precio `10000`. La DMN `validar_despacho` devuelve DESPACHAR.
3. Compuerta paralela: *Alistar* es manual; completar *Organizar el pedido por zona y vendedor*.
4. *Entregar el producto*: comprobante `ENT-300`. Se registra la salida F-AC-PT-002 y el stock baja a 98.
5. El trabajador JS completa `trasladar-venta` y arranca **Facturar y recaudar** con la llave `DEMO-300`.
6. En PR-SO-05: *Revisar soporte documental* (`F-AC-PT-002`, observación). Se registra la venta, se genera la factura y el trabajador completa `enviar-factura`.
7. En **Eventos externos** pulsar *Registrar pago recibido*. Registrar abono `5000` con `REC-300-A`: la DMN `estado_cartera` da PENDIENTE y vuelve a esperar. Repetir con `15000` y `REC-300-B`: queda CERRADA. Completar *Reportar el estado de cartera*.

### 2. Variantes de PR-CO-03

- Canal `DISTRIBUIDOR`: se ejecuta la Call Activity *Intercambiar envases* (contar vacíos y registrar llenos).
- `LOTE-PENDIENTE`: la DMN devuelve ESPERAR; completar *Informar la demora* y luego pulsar *Liberar lote (simula PR-CO-02)*. El proceso vuelve a consultar el lote y continúa. Si no se pulsa en 1 minuto, el temporizador lleva a *Reprogramar o cancelar*.
- `LOTE-VENCIDO`: la DMN devuelve BLOQUEAR; el lote se remite a PR-CO-04 y el pedido termina sin despacho.
- Cantidad mayor a las existencias: ESPERAR. Si otra venta consume el stock justo antes de la salida, el error de borde `SIN_STOCK` devuelve el pedido a *Informar la demora*.

### 3. PR-SO-04

1. Iniciar **Asignar y recuperar equipos** con `equipoId=EQ-001`, `responsableId=VEN-001`.
2. *Asignar el equipo*: acta `ACTA-EQ-1`. El trabajador JS genera la constancia; el equipo queda ASIGNADO (ver Registros).
3. Pulsar *Registrar solicitud de devolución* (o esperar 2 minutos: vence la asignación).
4. *Verificar el estado y firmar el acta de devolución*: acta `DEV-EQ-1`. Marcar "apto" → DISPONIBLE. Sin marcar → subproceso de mantenimiento (diagnóstico e informe técnico) y vuelve a la DMN.

### 4. Evidenciar la tarea externa

Detener solo el trabajador. Al llegar a `trasladar-venta`, `constancia-acta-equipo` o `enviar-factura`, la tarea queda en **Tareas externas**. Encender el trabajador y actualizar.

## Modelos y rúbrica

Ver `docs/RUBRICA.md` y los diagramas en `docs/diagramas/`. Los BPMN están en `src/main/resources/static/bpmn/` (abrir en Camunda Modeler con plataforma Camunda 7). Cockpit: http://localhost:8080/camunda/app/cockpit/default/. Tasklist: http://localhost:8080/camunda/app/tasklist/default/.

No iniciar directamente `intercambio_envases`, `mantenimiento_equipo`, `conciliar_cartera` ni `revisar_soporte`: son subprocesos llamados.

## Configuración, persistencia y seguridad

La aplicación escucha en 127.0.0.1; portal requiere inicio de sesión y REST/Cockpit requieren `admin`. Los formularios usan CSRF y el trabajador usa HTTP Basic. Cambiar la contraseña con `CAMUNDA_ADMIN_PASSWORD` antes de crear la base y usar el mismo valor en la terminal del trabajador. La base H2 queda en `data/agua-cumbre.mv.db`. El esquema agrega las columnas `ac_lote.liberado` y `ac_pedido.canal` (también sobre bases existentes), pero las instancias de los procesos anteriores no migran: usar una carpeta nueva.

## Pruebas y compilación

- `./mvnw.cmd clean test`: pruebas con motor real (`ProcesosTest`, `PortalTest`).
- `node --test worker-js/worker.test.cjs`: pruebas del trabajador.
- `./mvnw.cmd clean package` genera `target/agua-cumbre-camunda-3.0.0.jar`; `java -jar target/agua-cumbre-camunda-3.0.0.jar` lo ejecuta.

Ver `docs/VALIDACION.md`: qué se verificó al preparar esta versión y qué debe ejecutarse localmente.

## Límites del alcance

Propuesta TO-BE académica. No hay DIAN, firma digital, pasarela bancaria, correo real ni producción de lotes: la liberación del lote (PR-CO-02) y el pago del cliente se simulan desde el portal. El bloqueo de un lote vencido solo registra el movimiento hacia PR-CO-04. Un pedido contiene un único lote. Valores en COP enteros. Los umbrales de la DMN `accion_cobro` y los plazos son propuestas por validar con la empresa.
