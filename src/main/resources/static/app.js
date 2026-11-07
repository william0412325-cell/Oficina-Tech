'use strict';
/* ===== Utilidades ===== */
const $=id=>document.getElementById(id);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>Number(n||0).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const dBR=d=>d?String(d).slice(0,10).split('-').reverse().join('/'):'—';
const today=()=>new Date().toLocaleDateString('sv');
const ST={SOLICITADO:['Solicitado',''],CONFIRMADO:['Confirmado','blue'],AGUARDANDO_APROVACAO:['Aguardando aprovação','amber'],APROVADO:['Orçamento aprovado','blue'],EM_ANDAMENTO:['Em andamento','blue'],AGUARDANDO_RETIRADA:['Aguardando retirada','amber'],CONCLUIDO:['Concluído','green'],RECUSADO:['Orçamento recusado','red'],CANCELADO:['Cancelado','red']};
const pill=s=>{const[t,c]=ST[s]||[s,''];return `<span class="pill ${c}">${esc(t)}</span>`};
const stars=n=>'★'.repeat(n)+'☆'.repeat(5-n);
const safe=f=>(...a)=>Promise.resolve().then(()=>f(...a)).catch(e=>toast(e.message,true));
const panel=(t,inner)=>`<div class="panel">${t?`<h3>${t}</h3>`:''}${inner}</div>`;
const cards=a=>`<div class="cards">${a.map(([l,v])=>`<div class="card"><span class="label">${l}</span><strong>${v}</strong></div>`).join('')}</div>`;
const empty=t=>`<p class="muted">${t}</p>`;
const vLabel=v=>`${v.marca} ${v.modelo}${v.placa?' · '+v.placa:''}`;

let token=localStorage.getItem('ot_token'),me=null,area='cliente',page='home',authMode='login',pollTimer=null,resetToken=null;
let VC={},AP=null,CHAT=[];

async function api(path,method='GET',body){
  const h={'Content-Type':'application/json'};if(token)h.Authorization='Bearer '+token;
  let r;try{r=await fetch('/api'+path,{method,headers:h,body:body!==undefined?JSON.stringify(body):undefined})}catch{throw new Error('Sem conexão com o servidor.')}
  let d=null;try{d=await r.json()}catch{}
  if(r.status===401&&token&&!path.startsWith('/auth/')){logout('Sessão expirada. Entre novamente.');throw new Error('Sessão expirada.')}
  if(!r.ok)throw new Error((d&&d.erro)||'Erro inesperado ('+r.status+').');
  return d;
}
function toast(t,err){const e=document.createElement('div');e.className='toast'+(err?' err':'');e.textContent=t;document.body.appendChild(e);setTimeout(()=>e.classList.add('out'),3200);setTimeout(()=>e.remove(),3700)}
function modal(html,wide){let m=$('modal');if(!m){m=document.createElement('div');m.className='modal-bg';m.id='modal';m.innerHTML='<div class="modal" role="dialog" aria-modal="true"></div>';m.addEventListener('mousedown',e=>{if(e.target===m)closeModal()});document.body.appendChild(m)}
  const b=m.firstChild;b.className='modal'+(wide?' wide':'');b.innerHTML=`<button class="x" onclick="closeModal()" aria-label="Fechar">×</button>${html}`}
function closeModal(){$('modal')?.remove()}
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeModal()});
function readImg(file,max){return new Promise((ok,no)=>{if(!file.type.startsWith('image/'))return no(new Error('Escolha um arquivo de imagem.'));const fr=new FileReader();fr.onerror=()=>no(new Error('Falha ao ler a imagem.'));fr.onload=()=>{const im=new Image();im.onerror=()=>no(new Error('Imagem inválida.'));im.onload=()=>{const k=Math.min(1,max/Math.max(im.width,im.height)),c=document.createElement('canvas');c.width=Math.round(im.width*k);c.height=Math.round(im.height*k);c.getContext('2d').drawImage(im,0,0,c.width,c.height);ok(c.toDataURL('image/jpeg',.82))};im.src=fr.result};fr.readAsDataURL(file)})}

/* ===== Telas e autenticação ===== */
function showOnly(v){['landing','auth','app'].forEach(i=>$(i).classList.toggle('hidden',i!==v));$('topExit').classList.toggle('hidden',v!=='app');window.scrollTo(0,0)}
function openAuth(a,mode){area=a;showOnly('auth');renderAuth(mode||'login')}
function renderAuth(mode){
  authMode=mode;const adm=area==='admin';
  const E='<div class="field"><label for="fEmail">E-mail</label><input id="fEmail" type="email" autocomplete="email"></div>';
  const P=(l,ac)=>`<div class="field"><label for="fPass">${l}</label><input id="fPass" type="password" autocomplete="${ac}"></div>`;
  const titles={login:[adm?'Acesso administrativo':'Acesse sua conta',adm?'Entre com as credenciais administrativas.':'Entre para gerenciar seus veículos e serviços.'],register:['Criar conta','Cadastre-se para agendar serviços e acompanhar seu veículo.'],forgot:['Recuperar senha','Informe seu e-mail para receber o link de recuperação.'],reset:['Nova senha','Defina uma nova senha para sua conta.']};
  $('authTitle').textContent=titles[mode][0];$('authDesc').textContent=titles[mode][1];
  let f='';
  if(mode==='register')f=`<div class="field"><label for="fName">Nome completo</label><input id="fName" autocomplete="name"></div>${E}<div class="row2"><div class="field"><label for="fPhone">Telefone (WhatsApp)</label><input id="fPhone" type="tel" autocomplete="tel" placeholder="(71) 90000-0000"></div><div class="field"><label for="fGender">Gênero</label><select id="fGender"><option value="">Prefiro não informar</option><option>Feminino</option><option>Masculino</option><option>Outro</option></select></div></div>${P('Senha (mínimo 8 caracteres)','new-password')}<button class="btn primary" onclick="doRegister()">Criar conta</button>`;
  else if(mode==='forgot')f=`${E}<button class="btn primary" onclick="doForgot()">Enviar link</button>`;
  else if(mode==='reset')f=`${P('Nova senha (mínimo 8 caracteres)','new-password')}<button class="btn primary" onclick="doReset()">Salvar nova senha</button>`;
  else f=`${E}${P('Senha','current-password')}<button class="btn primary" onclick="doLogin()">Entrar</button>`;
  $('authFields').innerHTML=f;$('authMsg').innerHTML='';
  $('authLinks').innerHTML=adm?'<button class="link" onclick="logout()">Voltar</button>':mode==='login'?'Não tem conta? <button class="link" onclick="renderAuth(\'register\')">Criar cadastro</button> · <button class="link" onclick="renderAuth(\'forgot\')">Esqueci minha senha</button>':'<button class="link" onclick="renderAuth(\'login\')">Voltar ao login</button>';
  $('authFields').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();$('authFields').querySelector('.primary')?.click()}};
}
function authMsg(t,err){$('authMsg').innerHTML=`<div class="msg ${err?'err':''}">${esc(t)}</div>`}
const authRun=f=>safe(async()=>{try{await f()}catch(e){authMsg(e.message,true)}});
function setSession(r){token=r.token;localStorage.setItem('ot_token',token);me=r.usuario;startApp()}
const doLogin=authRun(async()=>{const r=await api('/auth/login','POST',{email:$('fEmail').value,senha:$('fPass').value});if((area==='admin')!==(r.usuario.perfil==='ADM'))throw new Error('E-mail ou senha incorretos.');setSession(r)});
const doRegister=authRun(async()=>{setSession(await api('/auth/register','POST',{nome:$('fName').value,email:$('fEmail').value,telefone:$('fPhone').value,genero:$('fGender').value,senha:$('fPass').value}))});
const doForgot=authRun(async()=>{const r=await api('/auth/forgot','POST',{email:$('fEmail').value});authMsg(r.mensagem)});
const doReset=authRun(async()=>{const r=await api('/auth/reset','POST',{token:resetToken,senha:$('fPass').value});resetToken=null;renderAuth('login');authMsg(r.mensagem)});

function startApp(){area=me.perfil==='ADM'?'admin':'cliente';showOnly('app');renderNav();CHAT=[];$('p-assistant').dataset.ready='';show('home');refreshBadge();clearInterval(pollTimer);pollTimer=setInterval(refreshBadge,60000)}
function logout(msg){token=null;me=null;localStorage.removeItem('ot_token');clearInterval(pollTimer);closeModal();showOnly('landing');if(typeof msg==='string')toast(msg,true)}

const NAV={cliente:[['home','Visão geral'],['vehicles','Meus veículos'],['booking','Novo agendamento'],['services','Serviços e orçamentos'],['notifications','Notificações'],['profile','Meu perfil'],['assistant','Atendimento']],
 admin:[['home','Dashboard'],['clients','Clientes e veículos'],['calendar','Calendário'],['history','Histórico de agendamentos'],['stock','Peças'],['reminders','Lembretes'],['reviews','Avaliações'],['support','Atendimento humano'],['notifications','Notificações'],['audit','Registro de alterações']]};
const TITLES={home:'Visão geral',vehicles:'Meus veículos',booking:'Novo agendamento',services:'Serviços e orçamentos',notifications:'Notificações',profile:'Meu perfil',assistant:'Atendimento',clients:'Clientes e veículos',calendar:'Calendário de agendamentos',history:'Histórico de agendamentos',stock:'Controle de peças',reminders:'Lembretes de manutenção',reviews:'Avaliações dos clientes',support:'Atendimento humano',audit:'Registro de alterações'};
function renderNav(){$('sidebar').innerHTML=`<div class="side-title">${area==='admin'?'Administração':'Minha conta'}</div>`+NAV[area].map(([id,l])=>`<button class="nav" id="nav-${id}" onclick="show('${id}')">${l}${id==='notifications'?'<span class="badge hidden" id="badge"></span>':''}</button>`).join('')+`<div class="side-foot">Oficina Tech · ${area==='admin'?'Painel restrito':'Área do cliente'}</div>`}
function show(id){page=id;document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active',p.id==='p-'+id));document.querySelectorAll('.sidebar .nav').forEach(b=>b.classList.toggle('active',b.id==='nav-'+id));$('pageTitle').textContent=TITLES[id]||id;$('pageSub').textContent=area==='admin'?'Painel de controle da oficina':'Olá, '+me.nome.split(' ')[0];
  if(id!=='assistant')$('p-'+id).innerHTML='<div class="spin"></div>';if(LOAD[id])safe(LOAD[id])()}
function setBadge(n){const b=$('badge');if(b){b.textContent=n;b.classList.toggle('hidden',!n)}}
async function refreshBadge(){try{setBadge((await api('/notificacoes')).naoLidas)}catch{}}

const LOAD={};

/* ===== Início ===== */
LOAD.home=async()=>{
  if(area==='admin')return homeAdmin();
  const[ap,vs,lem]=await Promise.all([api('/agendamentos'),api('/veiculos'),api('/lembretes')]);
  const pend=ap.filter(a=>a.decisao==='PENDENTE'&&a.status==='AGUARDANDO_APROVACAO').length;
  $('p-home').innerHTML=cards([['Meus veículos',vs.length],['Solicitações',ap.length],['Orçamentos para aprovar',pend],['Concluídos',ap.filter(a=>a.status==='CONCLUIDO').length]])
   +panel('Acompanhe seus serviços',ap.length?`<div class="items">${ap.slice(0,5).map(a=>`<div class="item"><div class="grow"><b>${esc(a.servico)}</b><p>${esc(vLabel(a))} · ${dBR(a.data)} ${esc(a.horario)}</p></div>${pill(a.status)}</div>`).join('')}</div><button class="link" onclick="show('services')">Ver todos</button>`:empty('Nenhuma solicitação ainda. Cadastre um veículo e peça um agendamento.'))
   +panel('Próximas manutenções',lem.length?`<div class="items">${lem.map(l=>`<div class="item"><div class="grow"><b>${esc(l.descricao)}</b><p>${esc(vLabel(l))} · ${l.data?dBR(l.data):''}${l.km_prevista?' · '+l.km_prevista+' km':''}</p></div></div>`).join('')}</div>`:empty('Nenhum lembrete programado.'));
};
async function homeAdmin(){
  const[d,ap]=await Promise.all([api('/admin/dashboard'),api('/agendamentos')]);
  $('p-home').innerHTML=cards([['Clientes',d.clientes],['Veículos',d.veiculos],['Pendentes',d.pendentes],['Agendados para hoje',d.hoje],['Concluídos',d.concluidos],['Nota média',Number(d.media).toFixed(1)+' / 5'],['Atendimentos abertos',d.atendimentos]])
   +panel('Agendamentos recentes',apptTable(ap.slice(0,8)));
}

/* ===== Veículos ===== */
function vehicleCard(v,uid){VC[v.id]=v;return `<div class="item"><div style="display:flex;gap:12px;align-items:center;min-width:0">${v.foto?`<img class="thumb" src="${esc(v.foto)}" alt="Foto do veículo">`:''}<div><b>${esc(v.marca)} ${esc(v.modelo)}</b><p>${esc(v.ano||'—')} · ${esc(v.placa||'sem placa')} · ${esc(v.cor||'cor não informada')}${v.km!=null?' · '+Number(v.km).toLocaleString('pt-BR')+' km':''}</p></div></div><div class="actions"><button class="btn secondary" onclick="openHistory(${v.id},'${esc(vLabel(v).replace(/['"\\&<>]/g,''))}')">Histórico</button><button class="btn outline" onclick="vehicleModal(${v.id},${uid||0})">Editar</button>${area==='cliente'?`<button class="btn danger" onclick="delVehicle(${v.id})">Excluir</button>`:''}</div></div>`}
LOAD.vehicles=async()=>{const vs=await api('/veiculos');$('p-vehicles').innerHTML=`<div class="actions" style="margin-bottom:14px"><button class="btn primary" onclick="vehicleModal()">Adicionar veículo</button></div>`+panel('Veículos cadastrados',vs.length?`<div class="items">${vs.map(v=>vehicleCard(v)).join('')}</div>`:empty('Nenhum veículo cadastrado.'))};
function vehicleModal(id,uid){const v=id?VC[id]:{};modal(`<h3>${id?'Editar veículo':'Adicionar veículo'}</h3><div class="row2"><div class="field"><label>Marca</label><input id="vBrand" value="${esc(v.marca)}" placeholder="Toyota"></div><div class="field"><label>Modelo</label><input id="vModel" value="${esc(v.modelo)}" placeholder="Corolla"></div><div class="field"><label>Ano</label><input id="vYear" type="number" value="${esc(v.ano)}"></div><div class="field"><label>Placa</label><input id="vPlate" maxlength="10" value="${esc(v.placa)}"></div><div class="field"><label>Cor</label><input id="vColor" value="${esc(v.cor)}"></div><div class="field"><label>Quilometragem atual</label><input id="vKm" type="number" min="0" value="${esc(v.km)}"></div></div><div class="field"><label>Foto (opcional${id?'; deixe vazio para manter a atual':''})</label><input id="vPhoto" type="file" accept="image/*"></div><div class="actions"><button class="btn primary" onclick="saveVehicle(${id||0},${uid||0})">Salvar</button></div>`)}
const saveVehicle=safe(async(id,uid)=>{const b={marca:$('vBrand').value,modelo:$('vModel').value,ano:$('vYear').value,placa:$('vPlate').value,cor:$('vColor').value,km:$('vKm').value};const f=$('vPhoto').files[0];if(f)b.foto=await readImg(f,640);
  if(id)await api('/veiculos/'+id,'PUT',b);else{if(uid)b.usuarioId=uid;await api('/veiculos','POST',b)}closeModal();toast('Veículo salvo.');show(page)});
const delVehicle=safe(async id=>{if(!confirm('Excluir este veículo?'))return;await api('/veiculos/'+id,'DELETE');toast('Veículo excluído.');show('vehicles')});

/* ===== Histórico do veículo ===== */
const CATS=[['OFICINA','Na Oficina Tech'],['EXTERNO','Fora da Oficina Tech'],['MODIFICACAO','Alterações e modificações']];
let HV={id:0,label:'',cat:'OFICINA',list:[]};
const openHistory=safe(async(id,label)=>{HV={id,label,cat:'OFICINA',list:[]};modal('<div class="spin"></div>',true);await loadHistory()});
async function loadHistory(){HV.list=await api(`/veiculos/${HV.id}/historico`);drawHistory()}
function setHcat(c){HV.cat=c;drawHistory()}
function drawHistory(){
  const adm=area==='admin',l=HV.list.filter(x=>x.categoria===HV.cat),canAdd=adm||HV.cat!=='OFICINA';
  const origem={OFICINA_TECH:'<span class="pill blue">Oficina Tech</span>',EXTERNO:'<span class="pill">Externo</span>'};
  modal(`<h3>Histórico · ${esc(HV.label)}</h3><div class="tabs">${CATS.map(([c,t])=>`<button class="tab ${c===HV.cat?'active':''}" onclick="setHcat('${c}')">${t} (${HV.list.filter(x=>x.categoria===c).length})</button>`).join('')}</div>
   ${l.length?`<div class="items">${l.map(x=>`<div class="item"><div class="grow"><b>${dBR(x.data)}</b> · ${esc(x.descricao)}${x.pecas?`<p>Peças: ${esc(x.pecas)}</p>`:''}<p>${origem[x.origem]||''} ${x.valor!=null?'· '+money(x.valor):''} · Registrado por ${esc(x.por||'—')} (${x.por_perfil==='ADM'?'Oficina':'Cliente'})</p></div>${adm||x.por_perfil==='CLIENTE'?`<button class="btn danger" onclick="delHist(${x.id})">Excluir</button>`:''}</div>`).join('')}</div>`:empty('Nenhum registro nesta categoria.')}
   ${canAdd?`<h4 style="margin:18px 0 8px">Adicionar registro</h4><div class="row2"><div class="field"><label>Data</label><input id="hDate" type="date" max="${today()}" value="${today()}"></div><div class="field"><label>Valor gasto (opcional)</label><input id="hValor" type="number" min="0" step=".01"></div></div>${adm&&HV.cat==='MODIFICACAO'?'<div class="field"><label>Origem</label><select id="hOrig"><option value="EXTERNO">Externo</option><option value="OFICINA_TECH">Oficina Tech</option></select></div>':''}<div class="field"><label>Descrição</label><textarea id="hDesc" placeholder="O que foi feito?"></textarea></div><div class="field"><label>Peças utilizadas (opcional)</label><input id="hPecas"></div><button class="btn primary" onclick="addHist()">Registrar</button>`:'<p class="muted" style="margin-top:12px">Serviços feitos na Oficina Tech são registrados automaticamente quando concluídos.</p>'}`,true);
}
const addHist=safe(async()=>{const b={categoria:HV.cat,data:$('hDate').value,descricao:$('hDesc').value,pecas:$('hPecas').value,valor:$('hValor').value};if(area==='admin'&&HV.cat==='MODIFICACAO')b.origem=$('hOrig').value;await api(`/veiculos/${HV.id}/historico`,'POST',b);toast('Registro adicionado.');await loadHistory()});
const delHist=safe(async id=>{if(!confirm('Excluir este registro?'))return;await api('/veiculos/historico/'+id,'DELETE');await loadHistory()});

/* ===== Agendamento e serviços (cliente) ===== */
LOAD.booking=async()=>{const vs=await api('/veiculos');
  $('p-booking').innerHTML=panel('Solicitar atendimento',vs.length?`<div class="row2"><div class="field"><label>Veículo</label><select id="bVehicle">${vs.map(v=>`<option value="${v.id}">${esc(vLabel(v))}</option>`).join('')}</select></div><div class="field"><label>Serviço desejado</label><select id="bService"><option>Revisão geral</option><option>Troca de óleo</option><option>Freios</option><option>Suspensão</option><option>Diagnóstico</option><option>Outro</option></select></div><div class="field"><label>Data desejada</label><input id="bDate" type="date" min="${today()}"></div><div class="field"><label>Horário preferido</label><input id="bTime" type="time"></div></div><div class="field"><label>Descreva o problema</label><textarea id="bProblem"></textarea></div><button class="btn primary" onclick="book()">Enviar solicitação</button>`:`${empty('Cadastre um veículo antes de solicitar um agendamento.')}<button class="btn primary" onclick="show('vehicles')">Cadastrar veículo</button>`)};
const book=safe(async()=>{await api('/agendamentos','POST',{veiculoId:$('bVehicle').value,servico:$('bService').value,data:$('bDate').value,horario:$('bTime').value,problema:$('bProblem').value});toast('Solicitação enviada para análise da oficina.');show('services')});
const orcBlock=a=>a.orc_descricao==null?'':`<div class="panel" style="margin:10px 0;background:#f8fafc"><b>Orçamento</b><p>${esc(a.orc_descricao)}</p><div class="kv"><div><span>Mão de obra</span>${money(a.mao_de_obra)}</div><div><span>Peças</span>${money(a.valor_pecas)}</div></div><div class="total">Total: ${money(a.total)}</div>${a.orc_obs?`<p>Observações: ${esc(a.orc_obs)}</p>`:''}<p>Situação: <b>${{PENDENTE:'aguardando sua decisão',APROVADO:'aprovado',RECUSADO:'recusado'}[a.decisao]}</b></p>${a.decisao==='PENDENTE'&&a.status!=='CANCELADO'?`<div class="actions"><button class="btn primary" onclick="decide(${a.id},'APROVADO')">Aprovar orçamento</button><button class="btn danger" onclick="decide(${a.id},'RECUSADO')">Recusar</button></div>`:''}</div>`;
LOAD.services=async()=>{const ap=await api('/agendamentos');
  $('p-services').innerHTML=panel('Meus serviços, orçamentos e histórico',ap.length?`<div class="items">${ap.map(a=>`<div class="item" style="align-items:flex-start"><div class="grow"><b>${esc(a.servico)} · ${esc(vLabel(a))}</b><p>${dBR(a.data)} às ${esc(a.horario)} · ${pill(a.status)}</p>${a.problema?`<p>${esc(a.problema)}</p>`:''}${orcBlock(a)}
   ${['SOLICITADO','CONFIRMADO','AGUARDANDO_APROVACAO'].includes(a.status)?`<button class="btn danger" onclick="cancelAppt(${a.id})">Cancelar agendamento</button>`:''}
   ${a.status==='CONCLUIDO'?(a.avaliado?'<p><span class="pill green">Serviço avaliado</span></p>':`<div class="panel" style="margin:10px 0 0"><b>Avalie este serviço</b><div class="row2" style="margin-top:8px"><div class="field"><label>Serviço realizado</label><select id="rs-${a.id}">${[5,4,3,2,1].map(n=>`<option value="${n}">${n} ${n>1?'estrelas':'estrela'}</option>`).join('')}</select></div><div class="field"><label>Atendimento</label><select id="ra-${a.id}">${[5,4,3,2,1].map(n=>`<option value="${n}">${n} ${n>1?'estrelas':'estrela'}</option>`).join('')}</select></div></div><div class="field"><label>Comentário</label><textarea id="rc-${a.id}"></textarea></div><button class="btn secondary" onclick="rate(${a.id})">Enviar avaliação</button></div>`):''}</div></div>`).join('')}</div>`:empty('Você ainda não tem serviços.'))};
const decide=safe(async(id,d)=>{if(d==='RECUSADO'&&!confirm('Recusar este orçamento?'))return;await api(`/agendamentos/${id}/orcamento/decisao`,'POST',{decisao:d});toast(d==='APROVADO'?'Orçamento aprovado.':'Orçamento recusado.');show('services')});
const cancelAppt=safe(async id=>{if(!confirm('Cancelar este agendamento?'))return;await api(`/agendamentos/${id}/cancelar`,'PUT');toast('Agendamento cancelado.');show('services')});
const rate=safe(async id=>{await api(`/agendamentos/${id}/avaliacao`,'POST',{notaServico:$('rs-'+id).value,notaAtendimento:$('ra-'+id).value,comentario:$('rc-'+id).value});toast('Obrigado pela avaliação!');show('services')});

/* ===== Notificações ===== */
LOAD.notifications=async()=>{const r=await api('/notificacoes');setBadge(r.naoLidas);
  $('p-notifications').innerHTML=panel('',`<div class="actions" style="justify-content:space-between;margin-bottom:12px"><h3 style="margin:0">Suas notificações</h3><button class="btn secondary" onclick="readAll()">Marcar todas como lidas</button></div>${r.itens.length?`<div class="items">${r.itens.map(n=>`<div class="item ${n.lida?'':'unread'}"><div class="grow"><b>${esc(n.titulo)}</b><p>${esc(n.mensagem)}</p><p style="font-size:12px">${esc(n.quando)}</p></div></div>`).join('')}</div>`:empty('Nenhuma notificação.')}`)};
const readAll=safe(async()=>{await api('/notificacoes/lidas','POST');setBadge(0);show('notifications')});

/* ===== Perfil ===== */
LOAD.profile=async()=>{me=await api('/me');
  $('p-profile').innerHTML=panel('Dados pessoais',`<div class="prof"><div class="avatar">${me.foto?`<img src="${esc(me.foto)}" alt="Foto de perfil">`:esc(me.nome[0].toUpperCase())}</div><div><div class="field" style="margin:0"><label>Foto de perfil</label><input id="pPhoto" type="file" accept="image/*" onchange="setPhoto()"></div>${me.foto?'<button class="link" onclick="rmPhoto()">Remover foto</button>':''}</div></div>
   <div class="row2"><div class="field"><label>Nome</label><input id="pName" value="${esc(me.nome)}"></div><div class="field"><label>E-mail</label><input id="pEmail" type="email" value="${esc(me.email)}"></div><div class="field"><label>Telefone</label><input id="pPhone" value="${esc(me.telefone||'')}"></div><div class="field"><label>Gênero</label><select id="pGender">${['','Feminino','Masculino','Outro'].map(g=>`<option value="${g}" ${(me.genero||'')===g?'selected':''}>${g||'Prefiro não informar'}</option>`).join('')}</select></div></div><button class="btn primary" onclick="saveProfile()">Salvar perfil</button>`)
   +panel('Alterar senha',`<div class="row2"><div class="field"><label>Senha atual</label><input id="oldPass" type="password" autocomplete="current-password"></div><div class="field"><label>Nova senha (mín. 8)</label><input id="newPass" type="password" autocomplete="new-password"></div></div><button class="btn secondary" onclick="changePass()">Atualizar senha</button>`)};
const profBody=()=>({nome:$('pName').value,email:$('pEmail').value,telefone:$('pPhone').value,genero:$('pGender').value});
const saveProfile=safe(async()=>{me=await api('/me','PUT',profBody());toast('Perfil atualizado.');show('profile')});
const setPhoto=safe(async()=>{const f=$('pPhoto').files[0];if(!f)return;me=await api('/me','PUT',{...profBody(),foto:await readImg(f,320)});toast('Foto atualizada.');show('profile')});
const rmPhoto=safe(async()=>{me=await api('/me','PUT',{...profBody(),foto:''});show('profile')});
const changePass=safe(async()=>{const r=await api('/me/senha','PUT',{atual:$('oldPass').value,nova:$('newPass').value});toast(r.mensagem);$('oldPass').value=$('newPass').value=''});

/* ===== Assistente ===== */
LOAD.assistant=async()=>{const p=$('p-assistant');if(p.dataset.ready)return;p.dataset.ready='1';
  p.innerHTML=panel('Assistente de atendimento',`<div class="notice">Este assistente orienta sobre a oficina e possíveis problemas, mas <b>não substitui a avaliação presencial de um mecânico</b> e não confirma diagnósticos nem orçamentos.</div><div id="chatlog" class="chat"><div class="bubble">Olá! Posso orientar sobre agendamentos, serviços, orçamentos e o acompanhamento do seu veículo. Como posso ajudar?</div></div><div class="actions"><input id="chatInput" class="inp" style="flex:1" maxlength="1000" placeholder="Digite sua dúvida" onkeydown="if(event.key==='Enter')chatSend()"><button class="btn primary" onclick="chatSend()">Enviar</button></div><button class="link" onclick="humanHandoff()">Falar com atendente humano</button><div id="handoff"></div>`)};
function bubble(t,me){const d=document.createElement('div');d.className='bubble'+(me?' me':'');d.textContent=t;$('chatlog').appendChild(d);$('chatlog').scrollTop=1e9;return d}
const chatSend=safe(async()=>{const i=$('chatInput'),q=i.value.trim();if(!q)return;i.value='';bubble(q,true);CHAT.push({role:'user',content:q});const w=bubble('…');
  try{const r=await api('/assistente','POST',{mensagem:q,historico:CHAT.slice(0,-1)});w.textContent=r.resposta;CHAT.push({role:'assistant',content:r.resposta});if(r.humano)$('handoff').innerHTML='<div class="msg">Este caso pode precisar de uma pessoa. Use “Falar com atendente humano” para registrar o pedido.</div>'}
  catch(e){w.remove();CHAT.pop();throw e}});
const humanHandoff=safe(async()=>{const last=CHAT.filter(m=>m.role==='user').pop();const r=await api('/assistente/humano','POST',{mensagem:last?last.content:''});$('handoff').innerHTML=`<div class="msg">${esc(r.mensagem)}</div>`});

/* ===== Admin: tabela de agendamentos ===== */
function apptTable(l){return `<div class="tablewrap"><table class="tbl"><thead><tr><th>Cliente</th><th>Veículo</th><th>Serviço</th><th>Data / hora</th><th>Situação</th><th>Orçamento</th><th></th></tr></thead><tbody>${l.length?l.map(a=>`<tr><td>${esc(a.cliente)}</td><td>${esc(vLabel(a))}</td><td>${esc(a.servico)}</td><td>${dBR(a.data)} ${esc(a.horario)}</td><td>${pill(a.status)}</td><td>${a.total!=null?money(a.total)+' · '+{PENDENTE:'pendente',APROVADO:'aprovado',RECUSADO:'recusado'}[a.decisao]:'—'}</td><td><button class="btn secondary" onclick="manageAppt(${a.id})">Gerenciar</button></td></tr>`).join(''):'<tr><td colspan="7">Nenhum agendamento.</td></tr>'}</tbody></table></div>`}
const manageAppt=safe(async id=>{modal('<div class="spin"></div>',true);const[a]=await api('/agendamentos?id='+id);if(!a){closeModal();throw new Error('Agendamento não encontrado.')}AP=a;
  modal(`<h3>Agendamento #${a.id}</h3><div class="kv"><div><span>Cliente</span>${esc(a.cliente)}</div><div><span>Contato</span>${esc(a.telefone||a.email)}</div><div><span>Veículo</span>${esc(vLabel(a))}</div><div><span>Serviço</span>${esc(a.servico)}</div><div><span>Data / hora</span>${dBR(a.data)} ${esc(a.horario)}</div><div><span>Situação atual</span>${pill(a.status)}</div></div>${a.problema?`<p><b>Problema relatado:</b> ${esc(a.problema)}</p>`:''}
   <div class="field"><label>Alterar situação</label><select id="mStatus">${Object.entries(ST).map(([k,[t]])=>`<option value="${k}" ${k===a.status?'selected':''}>${t}</option>`).join('')}</select></div>
   <h4 style="margin:16px 0 8px">Orçamento${a.decisao?` · ${{PENDENTE:'aguardando cliente',APROVADO:'aprovado pelo cliente',RECUSADO:'recusado pelo cliente'}[a.decisao]}`:''}</h4>
   <div class="field"><label>Descrição do serviço</label><textarea id="mDesc">${esc(a.orc_descricao)}</textarea></div>
   <div class="row2"><div class="field"><label>Mão de obra (R$)</label><input id="mMao" type="number" min="0" step=".01" value="${a.mao_de_obra??''}" oninput="calcTotal()"></div><div class="field"><label>Peças (R$)</label><input id="mPecas" type="number" min="0" step=".01" value="${a.valor_pecas??''}" oninput="calcTotal()"></div></div>
   <p class="total">Total: <span id="mTotal">${money(a.total)}</span></p><div class="field"><label>Observações</label><textarea id="mObs">${esc(a.orc_obs)}</textarea></div>
   <div class="actions"><button class="btn primary" onclick="saveAppt(${a.id})">Salvar</button><button class="btn outline" onclick="openHistory(${a.veiculo_id},'${esc(vLabel(a).replace(/['"\\&<>]/g,''))}')">Histórico do veículo</button></div>`,true)});
function calcTotal(){$('mTotal').textContent=money((+$('mMao').value||0)+(+$('mPecas').value||0))}
const saveAppt=safe(async id=>{const st=$('mStatus').value,d=$('mDesc').value.trim(),mao=$('mMao').value,pc=$('mPecas').value,obs=$('mObs').value;
  const orcChanged=d&&(d!==(AP.orc_descricao||'')||(+mao||0)!==Number(AP.mao_de_obra||0)||(+pc||0)!==Number(AP.valor_pecas||0)||obs!==(AP.orc_obs||''));
  if(st!==AP.status)await api(`/admin/agendamentos/${id}/status`,'PUT',{status:st});
  if(orcChanged)await api(`/admin/agendamentos/${id}/orcamento`,'PUT',{descricao:d,maoDeObra:mao,valorPecas:pc,observacoes:obs});
  closeModal();toast('Atendimento atualizado.');show(page)});

/* ===== Admin: clientes ===== */
let CLI=[],ctimer;
LOAD.clients=async()=>{$('p-clients').innerHTML=panel('',`<input id="cSearch" class="inp" style="width:100%" placeholder="Buscar por nome ou e-mail" oninput="searchClients()"><div id="cList" class="items" style="margin-top:12px"></div>`);await drawClients('')};
const searchClients=()=>{clearTimeout(ctimer);ctimer=setTimeout(safe(()=>drawClients($('cSearch').value)),300)};
async function drawClients(q){CLI=await api('/admin/clientes?q='+encodeURIComponent(q));
  $('cList').innerHTML=CLI.length?CLI.map(c=>`<div class="panel" style="margin:0"><div class="actions" style="justify-content:space-between"><div><b>${esc(c.nome)}</b><p class="muted" style="margin:2px 0">${esc(c.email)} · ${esc(c.telefone||'sem telefone')} · cliente desde ${esc(c.desde)}</p></div><div class="actions"><button class="btn outline" onclick="clientModal(${c.id})">Editar cliente</button><button class="btn secondary" onclick="vehicleModal(0,${c.id})">Adicionar veículo</button></div></div><div class="items" style="margin-top:10px">${c.veiculos.length?c.veiculos.map(v=>vehicleCard(v,c.id)).join(''):empty('Nenhum veículo.')}</div></div>`).join(''):empty('Nenhum cliente encontrado.')}
function clientModal(id){const c=CLI.find(x=>x.id===id);modal(`<h3>Editar cliente</h3><div class="row2"><div class="field"><label>Nome</label><input id="ceName" value="${esc(c.nome)}"></div><div class="field"><label>E-mail</label><input id="ceEmail" value="${esc(c.email)}"></div><div class="field"><label>Telefone</label><input id="cePhone" value="${esc(c.telefone||'')}"></div><div class="field"><label>Gênero</label><select id="ceGender">${['','Feminino','Masculino','Outro'].map(g=>`<option value="${g}" ${(c.genero||'')===g?'selected':''}>${g||'Não informado'}</option>`).join('')}</select></div></div><button class="btn primary" onclick="saveClient(${id})">Salvar</button>`)}
const saveClient=safe(async id=>{await api('/admin/clientes/'+id,'PUT',{nome:$('ceName').value,email:$('ceEmail').value,telefone:$('cePhone').value,genero:$('ceGender').value});closeModal();toast('Cliente atualizado.');drawClients($('cSearch')?.value||'')});

/* ===== Admin: calendário e histórico ===== */
const CAL={y:0,m:0,sel:null};
LOAD.calendar=async()=>{const d=new Date();if(!CAL.y){CAL.y=d.getFullYear();CAL.m=d.getMonth()}CAL.sel=null;
  $('p-calendar').innerHTML=panel('',`<div id="calGrid"></div>`)+panel('Agendamentos',`<div class="field"><label>Buscar por nome do cliente</label><input id="calName" class="inp" placeholder="Digite o nome ou e-mail" oninput="calSearch()"></div><div id="calList">${empty('Selecione um dia no calendário ou busque por um cliente.')}</div>`);await drawCal()};
async function drawCal(){const{y,m}=CAL,mes=`${y}-${String(m+1).padStart(2,'0')}`,cs=Object.fromEntries((await api('/admin/calendario?mes='+mes)).map(c=>[c.data,c.total]));
  const first=new Date(y,m,1).getDay(),days=new Date(y,m+1,0).getDate(),t=today();
  let g=['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'].map(d=>`<div class="dow">${d}</div>`).join('')+'<div class="day empty"></div>'.repeat(first);
  for(let d=1;d<=days;d++){const k=`${mes}-${String(d).padStart(2,'0')}`;g+=`<button class="day ${cs[k]?'has':''} ${k===t?'today':''} ${k===CAL.sel?'sel':''}" onclick="selDay('${k}')">${d}${cs[k]?`<small>${cs[k]}</small>`:''}</button>`}
  $('calGrid').innerHTML=`<div class="cal-head"><button class="btn outline" onclick="calMove(-1)" aria-label="Mês anterior">‹</button><h4>${new Date(y,m,1).toLocaleDateString('pt-BR',{month:'long',year:'numeric'})}</h4><button class="btn outline" onclick="calMove(1)" aria-label="Próximo mês">›</button></div><div class="cal">${g}</div>`}
const calMove=safe(async n=>{CAL.m+=n;if(CAL.m<0){CAL.m=11;CAL.y--}if(CAL.m>11){CAL.m=0;CAL.y++}await drawCal()});
const selDay=safe(async k=>{CAL.sel=k;$('calName').value='';await drawCal();await drawCalList()});
let calT;const calSearch=()=>{clearTimeout(calT);calT=setTimeout(safe(drawCalList),300)};
async function drawCalList(){const q=$('calName').value.trim();let l,t;
  if(q){l=await api('/agendamentos?q='+encodeURIComponent(q));t=`Resultados para “${esc(q)}”`}
  else if(CAL.sel){l=await api('/agendamentos?data='+CAL.sel);t='Agendamentos de '+dBR(CAL.sel)}
  else{$('calList').innerHTML=empty('Selecione um dia no calendário ou busque por um cliente.');return}
  $('calList').innerHTML=`<p class="muted">${t}</p>`+apptTable(l)}
LOAD.history=async()=>{$('p-history').innerHTML=panel('Pesquisar agendamentos anteriores',`<div class="row2"><div class="field"><label>Nome do cliente</label><input id="hq" class="inp" placeholder="Nome ou e-mail" oninput="histSearch()"></div><div class="field"><label>Data</label><input id="hd" type="date" class="inp" onchange="histSearch()"></div></div><button class="link" onclick="$('hq').value='';$('hd').value='';histSearch()">Limpar filtros</button><div id="hList"></div>`);await drawHist()};
let histT;const histSearch=()=>{clearTimeout(histT);histT=setTimeout(safe(drawHist),300)};
async function drawHist(){const q=$('hq').value.trim(),d=$('hd').value;$('hList').innerHTML=apptTable(await api(`/agendamentos?q=${encodeURIComponent(q)}&data=${d}`))}

/* ===== Admin: peças ===== */
LOAD.stock=async()=>{const[p,ag]=await Promise.all([api('/admin/pecas'),api('/agendamentos')]);
  $('p-stock').innerHTML=panel('Registrar peça',`<div class="row2"><div class="field"><label>Nome / identificação</label><input id="sName"></div><div class="field"><label>Marca</label><input id="sBrand"></div><div class="field"><label>Fornecedor</label><input id="sSup"></div><div class="field"><label>Preço de compra (R$)</label><input id="sPrice" type="number" min="0" step=".01"></div><div class="field"><label>Garantia</label><input id="sWar" placeholder="Ex.: 90 dias"></div><div class="field"><label>Quantidade</label><input id="sQty" type="number" min="0" value="1"></div></div><div class="field"><label>Vincular ao serviço/veículo (opcional)</label><select id="sAp"><option value="">Sem vínculo</option>${ag.slice(0,150).map(a=>`<option value="${a.id}">#${a.id} · ${esc(a.cliente)} · ${esc(vLabel(a))} · ${esc(a.servico)}</option>`).join('')}</select></div><button class="btn primary" onclick="addPart()">Adicionar peça</button>`)
   +panel('Peças registradas',`<div class="tablewrap"><table class="tbl"><thead><tr><th>Peça</th><th>Marca</th><th>Fornecedor</th><th>Preço de compra</th><th>Qtd.</th><th>Garantia</th><th>Vínculo</th><th></th></tr></thead><tbody>${p.length?p.map(x=>`<tr><td>${esc(x.nome)}</td><td>${esc(x.marca||'—')}</td><td>${esc(x.fornecedor||'—')}</td><td>${x.preco_compra!=null?money(x.preco_compra):'—'}</td><td>${x.quantidade}</td><td>${esc(x.garantia||'—')}</td><td>${x.agendamento_id?'#'+x.agendamento_id+' · '+esc(x.veiculo||''):'—'}</td><td><button class="btn danger" onclick="delPart(${x.id})">Excluir</button></td></tr>`).join(''):'<tr><td colspan="8">Nenhuma peça registrada.</td></tr>'}</tbody></table></div>`)};
const addPart=safe(async()=>{await api('/admin/pecas','POST',{nome:$('sName').value,marca:$('sBrand').value,fornecedor:$('sSup').value,precoCompra:$('sPrice').value,garantia:$('sWar').value,quantidade:$('sQty').value,agendamentoId:$('sAp').value});toast('Peça registrada.');show('stock')});
const delPart=safe(async id=>{if(!confirm('Excluir esta peça?'))return;await api('/admin/pecas/'+id,'DELETE');show('stock')});

/* ===== Admin: lembretes ===== */
let RC=[];
LOAD.reminders=async()=>{const[cl,ls]=await Promise.all([api('/admin/clientes?q='),api('/admin/lembretes')]);RC=cl.filter(c=>c.veiculos.length);
  $('p-reminders').innerHTML=panel('Novo lembrete de manutenção',RC.length?`<div class="row2"><div class="field"><label>Cliente</label><select id="rCli" onchange="fillRVeh()">${RC.map(c=>`<option value="${c.id}">${esc(c.nome)}</option>`).join('')}</select></div><div class="field"><label>Veículo</label><select id="rVeh"></select></div><div class="field"><label>Manutenção</label><input id="rDesc" list="rSug" placeholder="Ex.: Troca de óleo"><datalist id="rSug"><option value="Troca de óleo"><option value="Troca de filtros"><option value="Revisão periódica"><option value="Manutenção de componentes"></datalist></div><div class="field"><label>Canal de aviso</label><select id="rCanal"><option value="SISTEMA">Somente no sistema</option><option value="EMAIL">Sistema + e-mail</option><option value="WHATSAPP">Sistema + WhatsApp</option><option value="TODOS">Sistema + e-mail + WhatsApp</option></select></div><div class="field"><label>Data prevista</label><input id="rDate" type="date"></div><div class="field"><label>Quilometragem prevista</label><input id="rKm" type="number" min="0"></div><div class="field"><label>Avisar com antecedência (dias)</label><input id="rAnt" type="number" min="0" max="90" value="7"></div></div><p class="muted">Informe a data, a quilometragem ou ambas. O aviso é enviado quando a data estiver próxima ou o veículo chegar perto da quilometragem (atualizada no cadastro do veículo).</p><button class="btn primary" onclick="addRem()">Cadastrar lembrete</button>`:empty('Cadastre clientes com veículos para criar lembretes.'))
   +panel('Lembretes cadastrados',`<div class="tablewrap"><table class="tbl"><thead><tr><th>Cliente</th><th>Veículo</th><th>Manutenção</th><th>Previsão</th><th>Canal</th><th>Situação</th><th></th></tr></thead><tbody>${ls.length?ls.map(l=>`<tr><td>${esc(l.cliente)}</td><td>${esc(vLabel(l))}</td><td>${esc(l.descricao)}</td><td>${l.data?dBR(l.data):''} ${l.km_prevista?l.km_prevista+' km':''}</td><td>${esc(l.canal)}</td><td>${l.avisado?'<span class="pill green">Avisado</span>':'<span class="pill amber">Pendente</span>'}</td><td><button class="btn danger" onclick="delRem(${l.id})">Excluir</button></td></tr>`).join(''):'<tr><td colspan="7">Nenhum lembrete.</td></tr>'}</tbody></table></div>`);
  if(RC.length)fillRVeh()};
function fillRVeh(){const c=RC.find(x=>x.id==$('rCli').value);$('rVeh').innerHTML=c.veiculos.map(v=>`<option value="${v.id}">${esc(vLabel(v))}</option>`).join('')}
const addRem=safe(async()=>{await api('/admin/lembretes','POST',{usuarioId:$('rCli').value,veiculoId:$('rVeh').value,descricao:$('rDesc').value,dataPrevista:$('rDate').value,kmPrevista:$('rKm').value,canal:$('rCanal').value,antecedenciaDias:$('rAnt').value});toast('Lembrete cadastrado.');show('reminders')});
const delRem=safe(async id=>{if(!confirm('Excluir este lembrete?'))return;await api('/admin/lembretes/'+id,'DELETE');show('reminders')});

/* ===== Admin: avaliações, atendimento, auditoria ===== */
LOAD.reviews=async()=>{const r=await api('/admin/avaliacoes');
  $('p-reviews').innerHTML=panel('Avaliações dos clientes',r.length?`<div class="items">${r.map(x=>`<div class="item"><div class="grow"><b>${esc(x.cliente)}</b> · ${esc(x.servico)} <span class="muted">· ${esc(x.quando)}</span><p>Serviço <span class="stars">${stars(x.nota_servico)}</span> · Atendimento <span class="stars">${stars(x.nota_atendimento)}</span></p>${x.comentario?`<p>${esc(x.comentario)}</p>`:''}</div></div>`).join('')}</div>`:empty('Nenhuma avaliação recebida.'))};
LOAD.support=async()=>{const l=await api('/admin/atendimentos');
  $('p-support').innerHTML=panel('Pedidos de atendimento humano',l.length?`<div class="items">${l.map(x=>`<div class="item"><div class="grow"><b>${esc(x.cliente)}</b> ${x.status==='ABERTO'?'<span class="pill amber">Aberto</span>':'<span class="pill green">Resolvido</span>'}<p>${esc(x.email)} · ${esc(x.telefone||'sem telefone')} · ${esc(x.quando)}</p>${x.mensagem?`<p>“${esc(x.mensagem)}”</p>`:''}</div>${x.status==='ABERTO'?`<button class="btn secondary" onclick="resolveSup(${x.id})">Marcar como resolvido</button>`:''}</div>`).join('')}</div>`:empty('Nenhum pedido.'))};
const resolveSup=safe(async id=>{await api(`/admin/atendimentos/${id}/resolver`,'PUT');show('support')});
LOAD.audit=async()=>{const l=await api('/admin/auditoria');
  $('p-audit').innerHTML=panel('Últimas alterações (quem fez o quê)',`<div class="tablewrap"><table class="tbl"><thead><tr><th>Quando</th><th>Usuário</th><th>Ação</th><th>Detalhe</th></tr></thead><tbody>${l.map(x=>`<tr><td>${esc(x.quando)}</td><td>${esc(x.usuario_nome)}</td><td>${esc(x.acao)}</td><td>${esc(x.detalhe)}</td></tr>`).join('')||'<tr><td colspan="4">Sem registros.</td></tr>'}</tbody></table></div>`)};

/* ===== Início ===== */
(async function boot(){
  const p=new URLSearchParams(location.search);
  if(p.get('reset')){resetToken=p.get('reset');history.replaceState(null,'',location.pathname);area='cliente';showOnly('auth');renderAuth('reset');return}
  if(token){try{me=await api('/me');startApp();return}catch{token=null;localStorage.removeItem('ot_token')}}
  if(location.hash==='#admin'){openAuth('admin','login');return}
  showOnly('landing');
})();
