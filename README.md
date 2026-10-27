# Oficina Tech v3 — Java + MySQL

Estrutura inicial para transformar o protótipo HTML em um sistema com banco de dados.

## Incluído
- MySQL: usuarios, veiculos, agendamentos e historico_status
- Backend Java com Spring Boot
- Cadastro de cliente com senha BCrypt
- Login por e-mail/senha
- Cadastro/listagem de veículos
- Agendamento e consulta do cliente
- Lista de agendamentos para o ADM
- Atualização de status pelo ADM

## Antes de executar
1. Instale Java 17+, MySQL e Maven (ou abra como projeto Maven em uma IDE compatível).
2. Execute `banco.sql` no MySQL.
3. Abra `src/main/resources/application.properties`.
4. Troque `COLOQUE_SUA_SENHA_MYSQL` pela senha do seu MySQL.
5. Execute `OficinaTechApplication.java`.
6. A API ficará em `http://localhost:8080/api`.

## Segurança
Não coloque a senha fixa do ADM no HTML/JavaScript. Nesta versão, senhas de usuários são guardadas com BCrypt.
O cadastro do primeiro ADM deve ser feito de forma controlada no backend/banco antes da publicação.

## Próxima etapa
Conectar a interface Oficina Tech v2.1 aos endpoints desta API e criar autenticação/sessão adequada.
