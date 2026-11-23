#!/usr/bin/env bash
# Teste de fumaça ponta a ponta pelo BFF (sessão, CSRF, MFA, RBAC, regras e auditoria).
# Uso: BASE=http://localhost:8080 ./scripts/teste-fumaca.sh
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
J="$(mktemp)"
FALHAS=0
trap 'rm -f "$J"' EXIT

verificar() { # descricao esperado obtido
  if [ "$2" = "$3" ]; then echo "  OK   $1 ($3)"; else echo "  FALHA $1: esperado $2, obtido $3"; FALHAS=$((FALHAS + 1)); fi
}
token() { grep XSRF-TOKEN "$J" | awk '{print $7}'; }
req() { # metodo caminho [corpo] -> imprime status
  local m="$1" c="$2" b="${3:-}"
  if [ -n "$b" ]; then
    curl -s -o /tmp/resp.json -w "%{http_code}" -b "$J" -c "$J" -X "$m" -H "X-XSRF-TOKEN: $(token)" -H "Content-Type: application/json" -d "$b" "$BASE$c"
  else
    curl -s -o /tmp/resp.json -w "%{http_code}" -b "$J" -c "$J" -X "$m" -H "X-XSRF-TOKEN: $(token)" "$BASE$c"
  fi
}

echo "Teste de fumaça em $BASE"
curl -s -c "$J" -b "$J" "$BASE/api/v1/auth/csrf" > /dev/null
verificar "sem sessão" 401 "$(req GET /api/v1/configuracao/contratos)"
verificar "N2 sem MFA" 401 "$(req POST /api/v1/auth/login '{"usuarioId":"10000000-0000-4000-8000-000000000004"}')"
verificar "N2 com MFA" 200 "$(req POST /api/v1/auth/login '{"usuarioId":"10000000-0000-4000-8000-000000000004","codigoMfa":"123456"}')"
verificar "N2 não escreve" 403 "$(req POST /api/v1/configuracao/contratadas '{"cnpj":"x","razaoSocial":"x"}')"
verificar "login gestor" 200 "$(req POST /api/v1/auth/login '{"usuarioId":"10000000-0000-4000-8000-000000000002"}')"
verificar "painel" 200 "$(req GET /api/v1/configuracao/painel)"
verificar "lista contratos" 200 "$(req GET '/api/v1/configuracao/contratos?situacao=ATIVO&size=5&sort=numero,desc')"
verificar "CNPJ com DV inválido" 422 "$(req POST /api/v1/configuracao/contratadas '{"cnpj":"12.ABC.345/01DE-36","razaoSocial":"X"}')"
SUF=$(date +%s | tail -c 5)
BASE12="ZZ${SUF}0000001"; BASE12="${BASE12:0:12}"
DV=$(node -e "const t='$BASE12';const d=s=>{let x=0,p=2;for(let i=s.length-1;i>=0;i--){x+=(s.charCodeAt(i)-48)*p;p=p==9?2:p+1}const r=x%11;return r<2?0:11-r};const a=d(t);console.log(''+a+d(t+a))" 2>/dev/null || echo "")
if [ -n "$DV" ]; then
  verificar "CNPJ alfanumérico válido" 201 "$(req POST /api/v1/configuracao/contratadas "{\"cnpj\":\"${BASE12}${DV}\",\"razaoSocial\":\"Empresa Fumaca ${SUF}\"}")"
fi
verificar "SLAs" 200 "$(req GET '/api/v1/configuracao/slas?termo=SLA-ALFA')"
verificar "portfólio" 200 "$(req GET '/api/v1/configuracao/portfolio/sistemas?somentePendentes=true')"
verificar "governança" 200 "$(req GET '/api/v1/configuracao/auditoria/governanca?size=5')"
verificar "trilha LGPD" 200 "$(req GET '/api/v1/auditoria/acessos?size=5')"
verificar "exportação assíncrona" 202 "$(req POST /api/v1/auditoria/exportacoes '{"tipo":"GOVERNANCA","filtros":{}}')"
verificar "logout" 204 "$(req POST /api/v1/auth/logout)"

if [ "$FALHAS" -gt 0 ]; then echo "$FALHAS verificação(ões) falharam."; exit 1; fi
echo "Todas as verificações passaram."
