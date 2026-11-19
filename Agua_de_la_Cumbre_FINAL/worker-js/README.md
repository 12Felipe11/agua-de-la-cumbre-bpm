# Trabajador de tareas externas

Este componente Node.js atiende `trasladar-venta`, `constancia-acta-equipo` y `enviar-factura` de Camunda 7. Usa únicamente bibliotecas de Node 18+ y la API REST del motor embebido.

Desde `aplicacion-embebida`, ejecutar `node worker-js/worker.cjs` en una terminal distinta de Spring Boot. Variables opcionales: `CAMUNDA_URL` (por defecto `http://127.0.0.1:8080/engine-rest`), `CAMUNDA_USER` (por defecto `admin`) y `CAMUNDA_ADMIN_PASSWORD` (por defecto `admin`). La cuenta debe tener permiso de administrador REST. Para probar las transformaciones sin motor: `node --test worker-js/worker.test.cjs`.

El traslado de venta conserva `solicitantePortal` para que el cliente vea la facturación que nace de su pedido. La factura y la constancia son documentos simulados representados por contenido JSON y huella SHA-256; no son una factura DIAN ni una firma digital.
