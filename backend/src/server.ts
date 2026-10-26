import { criarApp, criarDeps } from "./app.js";
import { loadConfig } from "./config.js";

const cfg = loadConfig();
const deps = criarDeps(cfg);
const app = await criarApp(cfg, deps);
await app.listen({ port: cfg.PORT, host: cfg.HOST });
// Reconecta o WhatsApp automático, se já houver sessão salva.
void deps.whatsapp.iniciar();
