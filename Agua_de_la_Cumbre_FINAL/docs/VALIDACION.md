# Validación de la aplicación integrada

Camunda 7.24.0, Spring Boot 3.5.5, objetivo Java 17.

## Verificado al preparar esta versión

| Verificación | Resultado |
|---|---|
| XML de los 7 BPMN y 4 DMN bien formado | Correcto |
| Importación de los 7 BPMN en bpmn-js (sin advertencias) | Correcto; diagramas en `docs/diagramas/` |
| Referencias cruzadas: `formRef` → formulario, `decisionRef` → DMN, `calledElement` → proceso, topics BPMN ↔ trabajador | Sin referencias rotas |
| Compuertas exclusivas: todo flujo de salida tiene condición o es el flujo por defecto | Correcto |
| Cobertura de los 7 criterios de la rúbrica en cada proceso principal | Correcto (ver `RUBRICA.md`) |
| Compilación del código Java nuevo contra las firmas de las API usadas (Camunda, Spring, Jackson) | Sin errores |
| `node --test worker-js/worker.test.cjs` | 4 pruebas aprobadas |

## Comprobación de la entrega final

En esta carpeta se ejecutó `mvn package -q` con Java 21 y Maven 3.8.7: **30 pruebas Java aprobadas**, sin fallos ni errores (21 de `ProcesosTest` y 9 de `PortalTest`), y JAR generado. Tras añadir el registro y las restricciones por rol, `PortalTest` comprueba que un cliente inicia su solicitud, no ve casos ajenos, no puede completar tareas internas ni entrar a registros, variables del motor o REST; comprueba que `vendedores` puede atender su tarea, `tesoreria` no, y que bodega puede enviar el evento de liberación que el cliente tiene prohibido. `ProcesosTest` cubre los recorridos de motor ya existentes. Repetir las pruebas antes de la presentación.

También se ejecutó `node --test worker-js/worker.test.cjs` sin fallos. Los mensajes de error `Lote inexistente: NO-EXISTE` durante Maven corresponden al caso negativo esperado por `ProcesosTest`; la salida final y los reportes Surefire determinan el resultado real.

## Ensayo que debe hacerse en el equipo de presentación

1. Desde la carpeta de la aplicación, ejecutar `mvn test` y `node --test worker-js/worker.test.cjs`.
2. Arrancar Spring Boot y el trabajador Node.js en terminales separadas. Verificar `/portal` con `admin` y una cuenta cliente creada en `/registro`.
3. Crear un pedido nuevo como cliente, atender sus tareas como `vendedores`, comprobar la salida en Registros, esperar el traslado automático a facturación, registrar el pago y cerrar la cuenta con `tesoreria` y `administracion`.
4. Mostrar un alternativo (lote pendiente y temporizador, devolución de equipo o abono parcial) y revisar la instancia en Cockpit. El cliente debe ver solo sus casos y no debe poder entrar al REST del motor.

No se afirma aquí que el ensayo interactivo de extremo a extremo en la máquina de presentación ya se haya realizado.
