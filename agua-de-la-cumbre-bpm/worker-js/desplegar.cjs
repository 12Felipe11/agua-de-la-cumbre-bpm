// Despliega cada carpeta de proceso (BPMN + forms + DMN) en el motor Camunda 7.
//   node desplegar.cjs            -> despliega las tres carpetas
const fs = require('node:fs'), path = require('node:path');
const { http, base } = require('./camunda.cjs');
const raiz = path.join(__dirname, '..');
const carpetas = fs.readdirSync(raiz).filter(d => /^PR-/.test(d)).sort();
(async () => {
  for (const carpeta of carpetas) {
    const form = new FormData();
    form.append('deployment-name', carpeta);
    form.append('deploy-changed-only', 'true');
    form.append('deployment-source', 'agua-de-la-cumbre-bpm');
    let n = 0;
    for (const sub of ['bpmn', 'dmn', 'forms']) {
      const dir = path.join(raiz, carpeta, sub);
      for (const f of fs.readdirSync(dir)) {
        form.append(f, new Blob([fs.readFileSync(path.join(dir, f))]), f); n++;
      }
    }
    const r = await http('POST', '/deployment/create', undefined, form);
    console.log(`${carpeta}: ${n} archivos enviados, ${Object.keys(r.deployedProcessDefinitions || {}).length} procesos y ${Object.keys(r.deployedDecisionDefinitions || {}).length} decisiones nuevas`);
  }
  console.log('Listo. Motor:', base);
})().catch(e => { console.error('No se pudo desplegar:', e.message); process.exit(1); });
