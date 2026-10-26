import { criarApp, criarDeps } from "./app.js";
import { loadConfig } from "./config.js";

const cfg = loadConfig();
const app = await criarApp(cfg, criarDeps(cfg));
await app.listen({ port: cfg.PORT, host: cfg.HOST });
