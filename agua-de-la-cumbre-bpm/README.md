# Agua de la Cumbre — Gestión de procesos de negocio con Camunda 7

**Nombre del proyecto:** Agua de la Cumbre BPM: distribución, equipos de campo y cartera.

Modelado y automatización, en Camunda Platform 7, de los tres procesos priorizados en el documento *Identificación de procesos* de la empresa Agua de la Cumbre (Villavicencio, Meta). Proyecto académico de Ingeniería de Sistemas, Universidad de los Llanos.

## Integrantes y proceso a cargo

| Estudiante (nombre completo) | Proceso que asumió | Nombre de la carpeta en el repositorio |
|---|---|---|
| Santiago Cardona Tovar | PR-CO-03 Distribuir y Comercializar el Producto Terminado | `PR-CO-03_Distribuir_y_Comercializar` |
| Brayan Fernández | PR-SO-04 Asignar y Recuperar Equipos de Campo | `PR-SO-04_Asignar_y_Recuperar_Equipos` |
| Felipe Carrasquilla | PR-SO-05 Facturar y Recaudar las Ventas (Cartera) | `PR-SO-05_Facturar_y_Recaudar` |

## Estructura del repositorio

```
PR-CO-03_Distribuir_y_Comercializar/   bpmn/  forms/  dmn/
PR-SO-04_Asignar_y_Recuperar_Equipos/  bpmn/  forms/  dmn/
PR-SO-05_Facturar_y_Recaudar/          bpmn/  forms/  dmn/
worker-js/            External Tasks en JavaScript, desplegador y panel de eventos externos
docs/diagramas/       Imagen de cada BPMN
aplicacion-embebida/  (Opcional) los mismos procesos en Spring Boot + portal Thymeleaf
```

## Archivos que se deben desplegar en el motor

Todos los archivos de las tres carpetas `PR-*`. Las referencias usan *binding* `latest`, así que el orden de despliegue no importa y cada archivo puede desplegarse por separado.

### PR-CO-03 Distribuir y Comercializar el Producto Terminado — `PR-CO-03_Distribuir_y_Comercializar/`

| Tipo | Archivo | Id en el motor |
|---|---|---|
| Proceso principal | `bpmn/distribuir_comercializar.bpmn` | `distribuir_comercializar` |
| Subproceso (Call Activity) | `bpmn/intercambio_envases.bpmn` | `intercambio_envases` |
| DMN | `dmn/validar_despacho.dmn` | `validar_despacho` |
| Forms | `forms/dist_solicitud.form` (inicio), `dist_pedido.form`, `dist_preparar.form`, `dist_comprobante.form`, `dist_reprogramar.form`, `seguimiento.form`, `env_recepcion.form`, `env_registro.form` | mismo nombre del archivo |

### PR-SO-04 Asignar y Recuperar Equipos de Campo — `PR-SO-04_Asignar_y_Recuperar_Equipos/`

| Tipo | Archivo | Id en el motor |
|---|---|---|
| Proceso principal | `bpmn/equipos_campo.bpmn` | `equipos_campo` |
| Subproceso (Call Activity) | `bpmn/mantenimiento_equipo.bpmn` | `mantenimiento_equipo` |
| DMN | `dmn/disposicion_equipo.dmn` | `disposicion_equipo` |
| Forms | `forms/eq_solicitud.form` (inicio), `eq_acta_entrega.form`, `eq_acta_devolucion.form`, `eq_diagnostico.form`, `eq_informe_mantenimiento.form`, `seguimiento.form` | mismo nombre del archivo |

### PR-SO-05 Facturar y Recaudar las Ventas (Cartera) — `PR-SO-05_Facturar_y_Recaudar/`

| Tipo | Archivo | Id en el motor |
|---|---|---|
| Proceso principal | `bpmn/facturar_recaudar.bpmn` | `facturar_recaudar` |
| Subprocesos (Call Activity) | `bpmn/conciliar_cartera.bpmn`, `bpmn/revisar_soporte.bpmn` | `conciliar_cartera`, `revisar_soporte` |
| DMN | `dmn/accion_cobro.dmn`, `dmn/estado_cartera.dmn` | `accion_cobro`, `estado_cartera` |
| Forms | `forms/fin_venta_manual.form` (inicio), `revision_soporte.form`, `fin_pago.form`, `fin_reporte.form`, `fin_cobro.form`, `seguimiento.form` | mismo nombre del archivo |

**Total:** 7 BPMN, 4 DMN y 18 formularios (`seguimiento.form` se repite en las tres carpetas para que cada una sea autosuficiente).

## Nombre visible en el motor para darle *Start*

En **Tasklist → Start process** aparecen solo estos tres (los subprocesos están marcados como no iniciables):

| Proceso | Nombre visible en Tasklist | Clave |
|---|---|---|
| PR-CO-03 | **PR-CO-03 Distribuir y Comercializar el Producto Terminado** | `distribuir_comercializar` |
| PR-SO-04 | **PR-SO-04 Asignar y Recuperar Equipos de Campo** | `equipos_campo` |
| PR-SO-05 | **PR-SO-05 Facturar y Recaudar las Ventas (Cartera)** | `facturar_recaudar` |

PR-SO-05 también se inicia solo cuando PR-CO-03 termina (mensaje `VentaEntregada`).

## Datos por defecto

Todos los formularios traen sus campos ya diligenciados, así que basta con pulsar **Start** o **Complete**:

| Formulario | Valores por defecto |
|---|---|
| Inicio PR-CO-03 | pedido `PED-001`, cliente `CLI-001` |
| Registrar el pedido | vendedor `VEN-001`, zona `Centro - Villavicencio`, canal `DIRECTO`, lote `LOTE-DEMO`, cantidad `2`, precio `10000` |
| Inicio PR-SO-04 | equipo `EQ-001`, responsable `VEN-001` |
| Actas de equipo | `ACTA-ENT-001`, `ACTA-DEV-001`, equipo apto ✓ |
| Inicio PR-SO-05 | venta `VTA-001`, cliente `CLI-001`, canal `DIRECTO`, valor `20000` |
| Registrar el pago | pago `20000`, comprobante `REC-001` (salda la cuenta) |
| Demás tareas | observaciones y referencias de ejemplo |

Datos simulados del worker:

- **Lotes:** `LOTE-DEMO` (liberado y vigente), `LOTE-PENDIENTE` (sin liberar) y `LOTE-VENCIDO`.
- **Equipos:** `EQ-001`, `EQ-002` y `EQ-003`.
- **Plazos de los temporizadores:** 1 minuto para la espera del lote, 2 minutos para la asignación y 1 minuto para el pago. Para la operación real se proponen P3D, P30D y P30D.

## Cómo ejecutarlo

Requisitos: **Camunda Platform 7 Run 7.24** (o cualquier motor Camunda 7.21+ con REST en `http://localhost:8080/engine-rest`) y **Node.js 18+**.

1. Iniciar Camunda Run (`start.bat` o `start.sh`). Tasklist: http://localhost:8080/camunda/app/tasklist/ (usuario `demo` / `demo`).
2. Desplegar los procesos de **una** de estas dos formas:
   - **Script:** `cd worker-js` y `node desplegar.cjs`. Despliega las tres carpetas.
   - **Camunda Modeler:** abrir cada archivo de `bpmn/`, `dmn/` y `forms/` y pulsar *Deploy* (Camunda 7, `http://localhost:8080/engine-rest`).
3. Iniciar el worker JavaScript: `cd worker-js` y `node worker.cjs`. No requiere `npm install`.
4. Abrir el **panel de eventos externos** en http://localhost:3000. Ahí se envían los mensajes que esperan las instancias: *Liberar lote*, *Solicitar devolución* y *Registrar pago*. También muestra lotes, equipos, cuentas y movimientos.
5. En Tasklist: **Start process** → elegir uno de los tres nombres → **Start** → completar las tareas (filtro *All tasks*; *Claim* y luego *Complete*).

Si el motor exige autenticación: `CAMUNDA_USER=demo CAMUNDA_PASSWORD=demo node worker.cjs` (lo mismo para `desplegar.cjs`). Otro motor: `CAMUNDA_URL=http://host:puerto/engine-rest`.

### Recorrido sugerido (todo con los valores por defecto)

1. **PR-CO-03:** Start → *Registrar el pedido* → *Organizar el pedido* → *Entregar el producto*. El worker registra la salida y arranca PR-SO-05.
2. **PR-SO-05** (iniciado por el paso anterior): *Revisar soporte documental*. Luego, en el panel http://localhost:3000, pulsar *Registrar pago del cliente* → *Registrar el pago* → *Reportar el estado de cartera*. La cuenta queda cerrada.
3. **PR-SO-04:** Start → *Asignar el equipo*. En el panel, pulsar *Solicitar devolución del equipo* (o esperar 2 minutos a que venza la asignación) → *Verificar el estado y firmar el acta de devolución*. El equipo queda disponible. Si se desmarca "apto", pasa por el subproceso de mantenimiento.

### Variantes para mostrar otros caminos

| Cambio en el formulario | Camino que se recorre |
|---|---|
| Lote `LOTE-PENDIENTE` | DMN = ESPERAR, *Informar la demora* y gateway basado en eventos. En el panel, *Liberar lote*, o esperar 1 minuto para el temporizador y *Reprogramar o cancelar* |
| Lote `LOTE-VENCIDO` | DMN = BLOQUEAR; el lote se remite a PR-CO-04 |
| Canal `DISTRIBUIDOR` | Call Activity *Intercambiar envases* |
| Pago menor al valor | DMN `estado_cartera` = PENDIENTE; espera otro abono |
| Pago mayor al saldo, o comprobante repetido | REVISAR → *Revisar diferencias y corregir el registro* |
| No registrar el pago | Al minuto vence el plazo: DMN `accion_cobro` → *Gestionar el cobro* |
| Equipo apto sin marcar | DMN = MANTENIMIENTO → subproceso de mantenimiento |

## Elementos de la rúbrica por proceso

| Elemento | PR-CO-03 | PR-SO-04 | PR-SO-05 |
|---|---|---|---|
| Camunda Forms | 6 formularios + 2 del subproceso | 4 + 2 del subproceso | 5 + 1 del subproceso |
| DMN | `validar_despacho` | `disposicion_equipo` | `accion_cobro`, `estado_cartera` |
| AND / XOR | AND alistar ‖ organizar; XOR despacho, canal, reintento | AND registrar ‖ entregar; XOR disponibilidad, mantenimiento | AND cuenta por cobrar ‖ factura; XOR estado de cartera |
| Call Activity | `intercambio_envases` | `mantenimiento_equipo` | `conciliar_cartera`, `revisar_soporte` |
| Evento de mensaje | Captura `LoteLiberado`; envío `VentaEntregada` | Captura `DevolucionEquipo` | Inicio `VentaEntregada`; envío `FacturaEnviada`; captura `PagoRecibido` |
| Event-based gateway + temporizador | Lote liberado vs. espera máxima | Devolución vs. vencimiento | Pago vs. plazo de pago |
| External Task (JavaScript) | `consultar-inventario`, `bloquear-lote`, `registrar-salida-producto`, `trasladar-venta` | `consultar-inventario-equipos`, `constancia-acta-equipo`, `actualizar-inventario-equipos` | `registrar-venta`, `generar-factura`, `registrar-cuenta-por-cobrar`, `enviar-factura`, `validar-pago`, `aplicar-abono` |
| Adicional | Error de borde `SIN_STOCK` | — | Proceso compartido `revisar_soporte` |

## Aplicación embebida (opcional)

`aplicacion-embebida/` contiene los mismos tres procesos en un motor Camunda embebido en Spring Boot 3.5 con portal Thymeleaf, base H2 y logo de Agua de la Cumbre. Allí las tareas que tocan la base de datos son JavaDelegate. Ver `aplicacion-embebida/README.md`. Usar **o** la aplicación embebida **o** Camunda Run con las carpetas `PR-*`, no ambas en el mismo motor, porque comparten las claves de proceso.

## Supuestos

Propuesta TO-BE académica. Inventarios, cartera, liberación de lotes (PR-CO-02) y pagos son simulados por el worker y el panel. Los plazos y el umbral de la DMN `accion_cobro` (1.000.000 COP) son propuestas por validar con la empresa.
