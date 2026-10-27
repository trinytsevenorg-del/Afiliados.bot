// Compacta e embaralha o JavaScript gerado (nomes curtos, sem comentários) antes de ir para a imagem pública.
import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { build } from "esbuild";

const arquivos = [];
const andar = (d) => readdirSync(d).forEach((n) => { const p = join(d, n); statSync(p).isDirectory() ? andar(p) : p.endsWith(".js") && arquivos.push(p); });
andar("dist");
await build({ entryPoints: arquivos, outdir: "dist", outbase: "dist", allowOverwrite: true, minify: true, format: "esm", platform: "node", target: "node22", legalComments: "none", logLevel: "warning" });
console.log(`${arquivos.length} arquivos embaralhados`);
