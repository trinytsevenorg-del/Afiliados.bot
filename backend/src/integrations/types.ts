export type EstadoIntegracao =
  | "nao_configurado"
  | "configurado"
  | "aguardando_autorizacao"
  | "erro_autenticacao"
  | "indisponivel";

export interface Operacao {
  id: string;
  descricao: string;
  /** Existe código que faz isso de verdade? */
  implementada: boolean;
  /** Pode ser usada agora, com as credenciais atuais? */
  disponivel: boolean;
  motivo?: string;
}

export interface StatusIntegracao {
  id: "shopee" | "mercadolivre" | "ia" | "whatsapp" | "telegram";
  nome: string;
  estado: EstadoIntegracao;
  detalhe: string;
  operacoes: Operacao[];
  comoConfigurar?: string;
  verificadoEm: string;
}

export class IntegracaoErro extends Error {
  constructor(
    public readonly codigo: "nao_configurado" | "sem_acesso" | "autenticacao" | "limite" | "indisponivel" | "resposta_invalida" | "nao_encontrado",
    message: string,
    public readonly httpStatus = 502,
  ) {
    super(message);
  }
}
