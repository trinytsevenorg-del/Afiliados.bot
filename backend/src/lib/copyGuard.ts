// Confere o texto da IA contra os dados reais do produto.
// Qualquer preço, porcentagem ou promessa sem base vira um bloqueio.

export interface FatosProduto {
  nome: string;
  preco?: number | null;
  precoAnterior?: number | null;
  descontoPercentual?: number | null;
  caracteristicas?: string[];
  descricaoOriginal?: string | null;
  urgenciaFato?: string | null;
}

const PROMESSAS: { re: RegExp; motivo: string; liberadoPorUrgencia?: boolean }[] = [
  { re: /\b(o\s+)?mais\s+barat[oa]s?\b|\bmenor\s+pre[çc]o\b|\bpre[çc]o\s+mais\s+baixo\b/i, motivo: "Diz que é o mais barato sem comprovação" },
  { re: /[úu]ltimas?\s+unidades|estoque\s+(limitado|acabando)|poucas\s+unidades/i, motivo: "Cria escassez sem dado de estoque" },
  { re: /\b(s[óo]\s+hoje|acaba\s+(hoje|logo|j[áa])|corre\s+que\s+acaba|por\s+tempo\s+limitado)\b/i, motivo: "Urgência sem fato que justifique", liberadoPorUrgencia: true },
  { re: /\bmais\s+vendid[oa]s?\b|\bmilhares\s+de\s+vendas\b/i, motivo: "Fala de vendas sem dado" },
  { re: /\b(\d[\d.,]*\s*)?(estrelas|avalia[çc][õo]es)\b/i, motivo: "Fala de avaliações sem dado" },
  { re: /\b(entrega|chega)\s+(r[áa]pida|amanh[ãa]|em\s+\d+)|frete\s+gr[áa]tis/i, motivo: "Promete entrega ou frete sem dado" },
  { re: /\b(ganhe|lucre|renda\s+extra|dinheiro\s+f[áa]cil)\b/i, motivo: "Promete ganho financeiro" },
  { re: /\bgarantid[oa]\b/i, motivo: "Promessa de garantia sem dado" },
];

function numeros(texto: string): number[] {
  const out: number[] = [];
  for (const m of texto.matchAll(/\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?/g)) {
    const bruto = m[0];
    const n = bruto.includes(",") ? Number(bruto.replace(/\./g, "").replace(",", ".")) : Number(bruto);
    if (Number.isFinite(n)) out.push(n);
  }
  return out;
}

export function numerosPermitidos(f: FatosProduto): Set<number> {
  const s = new Set<number>();
  const add = (n?: number | null) => {
    if (n === null || n === undefined || !Number.isFinite(n)) return;
    s.add(Math.round(n * 100) / 100);
    s.add(Math.round(n));
    s.add(Math.floor(n));
  };
  add(f.preco);
  add(f.precoAnterior);
  add(f.descontoPercentual);
  if (f.preco && f.precoAnterior && f.precoAnterior > f.preco) add(f.precoAnterior - f.preco);
  const textos = [f.nome, f.descricaoOriginal ?? "", f.urgenciaFato ?? "", ...(f.caracteristicas ?? [])].join(" ");
  numeros(textos).forEach(add);
  return s;
}

export function conferirTexto(texto: string, f: FatosProduto): string[] {
  const problemas: string[] = [];
  if (/https?:\/\/|www\.|\b[a-z0-9-]+\.(com|br|la|ee)(\/|\b)/i.test(texto)) problemas.push("O texto contém link. O link é colocado pelo app, sem alteração.");
  const ok = numerosPermitidos(f);
  // Só números com cara de preço ou porcentagem precisam bater com os dados.
  for (const m of texto.matchAll(/R\$\s*(\d[\d.,]*)|(\d[\d.,]*)\s*%/g)) {
    const [n] = numeros(m[1] ?? m[2] ?? "");
    if (n !== undefined && !ok.has(Math.round(n * 100) / 100) && !ok.has(Math.round(n))) problemas.push(`Número sem base nos dados: "${m[0].trim()}"`);
  }
  if (/%/.test(texto) && (f.descontoPercentual === null || f.descontoPercentual === undefined)) {
    if (!/%/.test(f.descricaoOriginal ?? "") && !(f.caracteristicas ?? []).some((c) => c.includes("%"))) problemas.push("Fala em porcentagem, mas não há desconto comprovado");
  }
  const fatos = [f.nome, f.descricaoOriginal ?? "", ...(f.caracteristicas ?? [])].join(" \n ");
  for (const p of PROMESSAS) {
    if (p.liberadoPorUrgencia && f.urgenciaFato) continue;
    // Se o próprio dado do produto diz isso (ex.: "frete grátis" vindo da loja), pode.
    if (p.re.test(fatos)) continue;
    if (p.re.test(texto)) problemas.push(p.motivo);
  }
  return [...new Set(problemas)];
}
