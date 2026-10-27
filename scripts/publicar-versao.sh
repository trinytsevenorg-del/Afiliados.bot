#!/usr/bin/env bash
# Publica uma versão do app para os clientes:
#   1. gera o APK de venda (assinado, com a data desta versão);
#   2. copia para backend/releases e atualiza versoes.json;
#   3. depois do commit + push, o Render do servidor central serve em /baixar.
# Uso: scripts/publicar-versao.sh "Novidade 1" "Novidade 2"
# Antes: suba versionCode e versionName em android/app/build.gradle.kts.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
gradle="$raiz/android/app/build.gradle.kts"
codigo=$(grep -oP 'versionCode = \K\d+' "$gradle")
versao=$(grep -oP 'versionName = "\K[^"]+' "$gradle")
data=$(date +%s%3N)
cd "$raiz/android"
./gradlew --no-daemon -q :app:assembleRelease -PdataVersao="$data"
apk="app/build/outputs/apk/release/app-release.apk"
[ -f "$apk" ] || { echo "APK não gerado (falta android/chave/assinatura.properties?)"; exit 1; }
mkdir -p "$raiz/backend/releases"
cp "$apk" "$raiz/backend/releases/afiliados-bot-$versao.apk"
node -e '
const fs=require("fs");const [arq,codigo,versao,data,...nov]=process.argv.slice(1);
let j={versoes:[]};try{j=JSON.parse(fs.readFileSync(arq,"utf8"))}catch{}
j.versoes=j.versoes.filter(v=>v.versionCode!==Number(codigo));
j.versoes.push({versionCode:Number(codigo),versao,data:Number(data),arquivo:`afiliados-bot-${versao}.apk`,novidades:nov});
j.versoes.sort((a,b)=>b.versionCode-a.versionCode);
fs.writeFileSync(arq,JSON.stringify(j,null,2)+"\n");
' "$raiz/backend/releases/versoes.json" "$codigo" "$versao" "$data" "$@"
echo "Versão $versao ($codigo) pronta em backend/releases. Faça commit e push."
