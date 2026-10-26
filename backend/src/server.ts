import { criarApp, criarDeps } from "./app.js";
import { loadConfig } from "./config.js";
import { manterAcordado } from "./lib/manterAcordado.js";

const cfg = loadConfig();
const deps = criarDeps(cfg);
const app = await criarApp(cfg, deps);
await app.listen({ port: cfg.PORT, host: cfg.HOST });
// Reconecta o WhatsApp automático, se já houver sessão salva.
void deps.whatsapp.iniciar();
// Posta o que venceu na fila do servidor, a cada 30 s.
deps.fila.iniciar();
// Sem cron de fora: o servidor se visita para o Render grátis não dormir.
manterAcordado(process.env.KEEPALIVE_URL ?? process.env.RENDER_EXTERNAL_URL);
