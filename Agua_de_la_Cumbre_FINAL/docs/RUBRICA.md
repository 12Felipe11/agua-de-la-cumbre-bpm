# Correspondencia con la rúbrica

Los siete elementos están en el proceso principal de cada uno de los tres procesos.

| Criterio (peso) | PR-CO-03 `distribuir_comercializar` | PR-SO-04 `equipos_campo` | PR-SO-05 `facturar_recaudar` |
|---|---|---|---|
| Camunda Forms (10%) | `dist_pedido`, `dist_preparar`, `dist_comprobante`, `dist_reprogramar`, `seguimiento` | `eq_acta_entrega`, `eq_acta_devolucion`, `seguimiento` | `fin_pago`, `fin_reporte`, `fin_cobro`, `seguimiento` |
| DMN (10%) | `validar_despacho` (liberado + vigente + existencias) | `disposicion_equipo` | `accion_cobro` (+ `estado_cartera` en `conciliar_cartera`) |
| AND / XOR (10%) | AND alistar ‖ organizar; XOR ¿se puede despachar?, ¿canal?, ¿reintentar? | AND registrar ‖ entregar; XOR ¿disponible?, ¿mantenimiento? | AND cuenta por cobrar ‖ enviar factura; XOR ¿estado de la cartera? |
| Call Activity (20%) | `intercambio_envases` | `mantenimiento_equipo` | `conciliar_cartera`, `revisar_soporte` |
| Evento de mensaje (10%) | Inicio `PedidoRecibido`; captura `LoteLiberado`; envío `VentaEntregada` | Inicio `SolicitudEquipo`; captura `DevolucionEquipo` | Inicio `VentaEntregada`; envío `FacturaEnviada`; captura `PagoRecibido` |
| Event-based gateway + temporizador (20%) | `LoteLiberado` vs `${plazoEsperaLote}` | `DevolucionEquipo` vs `${plazoAsignacion}` | `PagoRecibido` vs `${plazoPago}` |
| External Task JavaScript (20%) | `trasladar-venta` (evento de envío) | `constancia-acta-equipo` | `enviar-factura` (evento de envío) |
| Adicional | Error de borde `SIN_STOCK` | — | Proceso compartido `revisar_soporte` |

## Decisiones

- `validar_despacho`: DESPACHAR solo con lote liberado (PR-AC-PT-004), vigente (fecha de vencimiento) y existencias suficientes; ESPERAR si falta liberación o stock; BLOQUEAR si el lote está vencido.
- `disposicion_equipo`: equipo apto → REINTEGRAR; no apto → MANTENIMIENTO.
- `estado_cartera`: pago no válido (valor o comprobante) → REVISAR; saldo > 0 → PENDIENTE; saldo ≤ 0 → CERRADA.
- `accion_cobro`: canal DIRECTO ≤ 1.000.000 → RECORDATORIO; > 1.000.000 → LLAMADA_DE_COBRO; DISTRIBUIDOR → ACUERDO_DE_PAGO (umbral propuesto).

## Thymeleaf y Camunda Forms

La interfaz principal es Thymeleaf. Las plantillas leen los campos de los mismos recursos `.form`, y el formulario de cada tarea se toma del `camunda:formRef` del modelo desplegado. Los `.form` también se pueden abrir en Tasklist, lo que evidencia literalmente el criterio Camunda Forms. Los formularios `dist_solicitud`, `eq_solicitud` y `fin_venta_manual` solo los usa el portal para armar el mensaje de inicio (un evento de inicio de mensaje no tiene formulario en Tasklist).

## Arquitectura

Spring Boot aloja Camunda, Thymeleaf, los controladores, los JavaDelegate y H2. Las tareas que modifican el negocio son JavaDelegate y participan en la transacción del motor. El trabajador Node.js es un proceso separado: obtiene tareas por REST autenticado, completa las tres External Tasks y, en `trasladar-venta`, correlaciona el mensaje que inicia PR-SO-05 solo si esa venta aún no tiene instancia.
