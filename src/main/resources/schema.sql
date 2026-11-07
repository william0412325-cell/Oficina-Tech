-- Executado a cada inicialização. Usa apenas IF NOT EXISTS: nunca apaga dados existentes.
CREATE TABLE IF NOT EXISTS usuarios (
  id BIGSERIAL PRIMARY KEY,
  nome VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  telefone VARCHAR(25),
  genero VARCHAR(30),
  foto_url TEXT,
  senha_hash VARCHAR(255) NOT NULL,
  perfil VARCHAR(10) NOT NULL DEFAULT 'CLIENTE' CHECK (perfil IN ('CLIENTE','ADM')),
  reset_hash VARCHAR(100),
  reset_expira TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS veiculos (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  marca VARCHAR(60) NOT NULL,
  modelo VARCHAR(60) NOT NULL,
  ano INT,
  placa VARCHAR(10),
  cor VARCHAR(40),
  km INT,
  foto_url TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS agendamentos (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  veiculo_id BIGINT NOT NULL REFERENCES veiculos(id) ON DELETE CASCADE,
  servico VARCHAR(100) NOT NULL,
  data_servico DATE NOT NULL,
  horario TIME NOT NULL,
  problema TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'SOLICITADO',
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS orcamentos (
  id BIGSERIAL PRIMARY KEY,
  agendamento_id BIGINT NOT NULL UNIQUE REFERENCES agendamentos(id) ON DELETE CASCADE,
  descricao TEXT NOT NULL,
  mao_de_obra NUMERIC(10,2) NOT NULL DEFAULT 0,
  valor_pecas NUMERIC(10,2) NOT NULL DEFAULT 0,
  total NUMERIC(10,2) NOT NULL DEFAULT 0,
  observacoes TEXT,
  decisao VARCHAR(12) NOT NULL DEFAULT 'PENDENTE' CHECK (decisao IN ('PENDENTE','APROVADO','RECUSADO')),
  decidido_em TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS historico_status (
  id BIGSERIAL PRIMARY KEY,
  agendamento_id BIGINT NOT NULL REFERENCES agendamentos(id) ON DELETE CASCADE,
  status_anterior VARCHAR(40),
  status_novo VARCHAR(40) NOT NULL,
  alterado_por BIGINT,
  alterado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS historico_veiculo (
  id BIGSERIAL PRIMARY KEY,
  veiculo_id BIGINT NOT NULL REFERENCES veiculos(id) ON DELETE CASCADE,
  categoria VARCHAR(20) NOT NULL CHECK (categoria IN ('OFICINA','EXTERNO','MODIFICACAO')),
  data_servico DATE NOT NULL,
  descricao TEXT NOT NULL,
  pecas TEXT,
  valor NUMERIC(10,2),
  origem VARCHAR(20) NOT NULL CHECK (origem IN ('OFICINA_TECH','EXTERNO')),
  registrado_por BIGINT,
  registrado_por_nome VARCHAR(100),
  registrado_por_perfil VARCHAR(10),
  agendamento_id BIGINT,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS avaliacoes (
  id BIGSERIAL PRIMARY KEY,
  agendamento_id BIGINT NOT NULL UNIQUE REFERENCES agendamentos(id) ON DELETE CASCADE,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  nota_servico SMALLINT NOT NULL CHECK (nota_servico BETWEEN 1 AND 5),
  nota_atendimento SMALLINT NOT NULL CHECK (nota_atendimento BETWEEN 1 AND 5),
  comentario TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS pecas (
  id BIGSERIAL PRIMARY KEY,
  nome VARCHAR(120) NOT NULL,
  marca VARCHAR(80),
  fornecedor VARCHAR(150),
  preco_compra NUMERIC(10,2),
  garantia VARCHAR(80),
  quantidade INT NOT NULL DEFAULT 0,
  agendamento_id BIGINT REFERENCES agendamentos(id) ON DELETE SET NULL,
  veiculo_id BIGINT REFERENCES veiculos(id) ON DELETE SET NULL,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS lembretes (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  veiculo_id BIGINT NOT NULL REFERENCES veiculos(id) ON DELETE CASCADE,
  descricao VARCHAR(160) NOT NULL,
  data_prevista DATE,
  km_prevista INT,
  canal VARCHAR(10) NOT NULL DEFAULT 'SISTEMA' CHECK (canal IN ('SISTEMA','EMAIL','WHATSAPP','TODOS')),
  antecedencia_dias INT NOT NULL DEFAULT 7,
  enviado_em TIMESTAMP,
  criado_por BIGINT,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS notificacoes (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  titulo VARCHAR(160) NOT NULL,
  mensagem TEXT,
  lida BOOLEAN NOT NULL DEFAULT FALSE,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS auditoria (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT,
  usuario_nome VARCHAR(100),
  acao VARCHAR(60) NOT NULL,
  detalhe TEXT,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS atendimento_humano (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  mensagem TEXT,
  status VARCHAR(12) NOT NULL DEFAULT 'ABERTO',
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  resolvido_em TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_veiculos_usuario ON veiculos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_agend_usuario ON agendamentos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_agend_data ON agendamentos(data_servico);
CREATE INDEX IF NOT EXISTS idx_hist_veiculo ON historico_veiculo(veiculo_id);
CREATE INDEX IF NOT EXISTS idx_notif_usuario ON notificacoes(usuario_id, lida);
