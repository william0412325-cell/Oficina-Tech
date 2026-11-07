# Oficina Tech v4 — sistema integrado

Spring Boot 3 / Java 17 + PostgreSQL. Front-end estático (`static/index.html`, `style.css`, `app.js`) servido pelo próprio Spring.
Todos os dados agora ficam no banco, e todo acesso passa pela API com login (JWT).

## Publicar no Render (passo a passo)
1. **Banco:** Render → New → PostgreSQL. Copie a **Internal Database URL**.
2. **Serviço web:** use este projeto (repositório Git ou "Blueprint" com o `render.yaml`). Ambiente: **Docker** (usa o `Dockerfile`).
3. **Variáveis de ambiente** do serviço web:

| Variável | Obrigatória | Para quê |
|---|---|---|
| `DATABASE_URL` | sim | Internal Database URL do PostgreSQL |
| `JWT_SECRET` | sim | texto aleatório com 32+ caracteres (assina os logins) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | sim | cria o administrador (senha de 10+ caracteres). Para trocar a senha depois, altere a variável e reinicie |
| `APP_URL` | recomendada | endereço público, ex.: `https://oficina-tech.onrender.com` (links do e-mail de recuperação) |
| `ANTHROPIC_API_KEY` | opcional | ativa a IA real do assistente (sem ela, responde com mensagens padrão). `ANTHROPIC_MODEL` troca o modelo (padrão `claude-haiku-5-5`) |
| `RESEND_API_KEY` + `MAIL_FROM` | opcional | e-mail (recuperação de senha e lembretes). Conta em resend.com; `MAIL_FROM` é um remetente verificado lá |
| `WHATSAPP_TOKEN` + `WHATSAPP_PHONE_ID` | opcional | WhatsApp Cloud API (Meta). `WHATSAPP_TEMPLATE` = nome de um template aprovado (necessário para avisar clientes fora da janela de 24 h) |
| `ADMIN_WHATSAPP` | opcional | número que recebe aviso de novas solicitações |

4. Faça o deploy. As tabelas são criadas sozinhas (`schema.sql` só usa `IF NOT EXISTS`, nunca apaga dados).
5. Acesse o site e use **"Acesso restrito"** (rodapé) ou `/#admin` para entrar como administrador.

## O que está implementado (do relatório)
Cadastro/login/recuperação/troca de senha; perfil com foto e gênero; veículos dentro do cliente (marca, modelo, ano, placa, cor, km, foto);
histórico do veículo em 3 categorias com autor e origem; histórico e calendário de agendamentos com busca; orçamento completo
(mão de obra + peças + total + observações) com aprovação/recusa; avaliações (serviço e atendimento); controle de peças vinculável a serviço/veículo;
lembretes por data ou km (sistema, e-mail, WhatsApp); notificações internas; assistente de IA com encaminhamento humano; registro de alterações (auditoria).
**Instagram (item 8)** não foi iniciado, conforme o relatório.

## Segurança
Senhas com BCrypt; login com JWT (12 h); rotas `/api/admin/**` só para ADM; cliente só acessa os próprios dados (404 nos demais);
limite de tentativas de login; link de recuperação com token de uso único (1 h, guardado só como hash); cabeçalhos de segurança/CSP; sem senha fixa no código.

## Observações
- Plano gratuito do Render: o serviço "dorme" quando ocioso; os lembretes são verificados a cada hora **enquanto o serviço estiver ativo**.
  Verifique também as regras do plano gratuito do PostgreSQL no Render (podem expirar).
- Sem `RESEND_API_KEY`, o link de recuperação de senha é escrito no log do serviço (apenas para testes).
- Rodar localmente: PostgreSQL local + `DB_USER`, `DB_PASSWORD`, `JDBC_URL` (ou `DATABASE_URL`) + `mvn spring-boot:run`.
