// Cheap regression gate: Java compiles HTML text blocks without parsing their
// embedded JavaScript. Validate the actual source on each Android Debug build.
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(
  'PipePipeClient/app/src/main/java/org/schabi/newpipe/views/SupjavBrowserHlsDocument.java',
  'utf8'
);
const script = source.match(/<script>\s*(\(function\(\)\{[\s\S]*?\}\)\(\);)\s*<\/script>/);
if (!script) throw new Error('Supjav HLS inline script not found');
new vm.Script(script[1], {filename: 'SupjavBrowserHlsDocument.inline.js'});
const checks = [
  ['source-level highest quality', source.includes('hls.nextLevel=highest')],
  ['bounded startup auto-retry after decoded data', source.includes("requestPlay('decoded-data',false)")],
  ['preserve manual pause', source.includes('explicitPause')],
  ['first fragment instrumentation', source.includes('FIRST_FRAGMENT_LOADED')],
  ['no 12-second restart of active fragment', !source.includes('STARTUP_NO_FRAGMENT')],
  ['no HTML playback-control duplicate', !source.includes('id="controls"') && !source.includes('id="seek"')],
  ['no early 18/30-second fatal deadline', !source.includes('no-decoded-video-after-30s')]
];
for (const [label, okay] of checks) {
  if (!okay) throw new Error('Supjav HLS regression: ' + label);
}
console.log('Supjav HLS JS syntax and playback policy: OK');
