// Serveur MVP : reçoit les positions (POST /api/location), carte admin (/?key=TOKEN),
// liens de partage publics en lecture seule (/t/<code>) pour suivre UN appareil en temps réel.
// Lancer : TOKEN=secret [PUBLIC_URL=https://xxx.trycloudflare.com] node server.js   (aucune dépendance)
const http = require('http');
const crypto = require('crypto');
const TOKEN = process.env.TOKEN || 'dev';
const PORT = process.env.PORT || 3000;
const PUBLIC_URL = (process.env.PUBLIC_URL || process.env.RENDER_EXTERNAL_URL || '').replace(/\/$/, '');
const devices = {}; // id -> {id, lat, lng, acc, speed, ts, received}
const shareCode = id => crypto.createHmac('sha256', TOKEN).update(id).digest('hex').slice(0, 16); // stable, non devinable

const HEAD = `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<link rel=stylesheet href=https://unpkg.com/leaflet@1.9.4/dist/leaflet.css>
<script src=https://unpkg.com/leaflet@1.9.4/dist/leaflet.js></script>
<style>html,body,#m{height:100%;margin:0;font-family:sans-serif}
#bar{position:absolute;z-index:1000;top:8px;left:50px;right:8px;background:#fffd;padding:6px 10px;border-radius:6px;font-size:14px}
button{margin-left:6px}</style><div id=bar>…</div><div id=m></div>
<script>
const map=L.map('m').setView([46.6,2.4],5),mk={},trail={};let fit=true;
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(map);
const PUB=${JSON.stringify(PUBLIC_URL)}||location.origin;
function draw(list){
  for(const d of list){
    const age=Math.round((Date.now()-d.received)/1000), t=d.id+' · '+age+'s';
    if(mk[d.id]){mk[d.id].setLatLng([d.lat,d.lng]).setTooltipContent(t);trail[d.id].addLatLng([d.lat,d.lng])}
    else{mk[d.id]=L.marker([d.lat,d.lng]).addTo(map).bindTooltip(t,{permanent:true});trail[d.id]=L.polyline([[d.lat,d.lng]]).addTo(map)}
  }
  if(fit&&list.length){map.setView([list[0].lat,list[0].lng],16);fit=false}
}
`;

const ADMIN = HEAD + `
const KEY=new URLSearchParams(location.search).get('key')||'';
async function tick(){
  const r=await fetch('/api/locations?key='+encodeURIComponent(KEY));
  if(r.status==401){bar.textContent='Token invalide (?key=...)';return}
  const list=await r.json(); draw(list);
  bar.innerHTML=list.length?list.map(d=>d.id+' <button onclick="cp(\\''+d.share+'\\')">Copier le lien de partage</button>').join(' · '):'Aucun appareil — démarre l\\'app';
}
function cp(s){const u=PUB+'/t/'+s;navigator.clipboard?navigator.clipboard.writeText(u).then(()=>alert('Copié : '+u)):prompt('Lien',u)}
setInterval(tick,2000);tick();
</script>`;

const SHARE = HEAD + `
const CODE=location.pathname.split('/').pop();
async function tick(){
  const r=await fetch('/api/share/'+CODE);
  if(r.status==404){bar.textContent='En attente de la position…';return}
  const d=await r.json(); draw([d]); map.panTo([d.lat,d.lng]);
  bar.textContent='Position en direct · il y a '+Math.round((Date.now()-d.received)/1000)+' s';
}
setInterval(tick,2000);tick();
</script>`;

const json = (res, o, c = 200) => { res.writeHead(c, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
const html = (res, s) => { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); res.end(s); };

http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  const p = u.pathname;
  if (req.method === 'GET' && p === '/') return html(res, ADMIN);
  if (req.method === 'GET' && p.startsWith('/t/')) return html(res, SHARE);
  if (req.method === 'GET' && p === '/api/locations') {
    if (u.searchParams.get('key') !== TOKEN) { res.writeHead(401); return res.end(); }
    return json(res, Object.values(devices).map(d => ({ ...d, share: shareCode(d.id) })));
  }
  if (req.method === 'GET' && p.startsWith('/api/share/')) {
    const code = p.slice(11);
    const d = Object.values(devices).find(d => shareCode(d.id) === code);
    return d ? json(res, { id: d.id, lat: d.lat, lng: d.lng, acc: d.acc, received: d.received }) : json(res, {}, 404);
  }
  if (req.method === 'POST' && p === '/api/location') {
    if (req.headers.authorization !== 'Bearer ' + TOKEN) { res.writeHead(401); return res.end(); }
    let body = '';
    req.on('data', c => { body += c; if (body.length > 4096) req.destroy(); });
    req.on('end', () => {
      try {
        const d = JSON.parse(body);
        if (typeof d.id !== 'string' || !isFinite(d.lat) || !isFinite(d.lng)) throw 0;
        const id = d.id.slice(0, 64);
        devices[id] = { ...d, id, received: Date.now() };
        res.writeHead(204); res.end();
      } catch { res.writeHead(400); res.end(); }
    });
    return;
  }
  res.writeHead(404); res.end();
}).listen(PORT, '0.0.0.0', () => {
  console.log('Carte admin (PC) : http://localhost:' + PORT + '/?key=' + TOKEN);
  for (const list of Object.values(require('os').networkInterfaces()))
    for (const i of list) if (i.family === 'IPv4' && !i.internal)
      console.log('URL dans l\'app  : http://' + i.address + ':' + PORT + '   (token : ' + TOKEN + ')');
});
