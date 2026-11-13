# Validación de la versión 3.0.0

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

## Pendiente de ejecutar en el equipo local

El entorno donde se preparó esta versión no tenía acceso a Maven Central, así que **no se ejecutó `mvnw clean test` ni la aplicación completa**. Antes de presentar:

1. `./mvnw.cmd clean test` → deben pasar `ProcesosTest` (recorridos de los tres procesos, DMN, temporizadores, mensajes, error SIN_STOCK, abonos y comprobantes repetidos) y `PortalTest` (logo, inicio por mensaje desde Thymeleaf, CSRF, validación de campos).
2. `./mvnw.cmd spring-boot:run` + `node worker-js/worker.cjs` y recorrer la demostración del README.

Si alguna prueba falla, el mensaje de Maven indica la tarea o variable involucrada.
