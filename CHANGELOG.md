# Changelog

## 1.1.0 (não lançado)

### Adicionado
- Comando `/chronobubble status [jogador]` com Time, mana, renascimento, Swallowtail e habilidades ativas.
- Conquistas: *Asas do Tempo* (obter uma Swallowtail), *Reserva Completa* (encher uma) e *O Tempo Não Para*
  (renascer).
- Time Hop reformulado (`hop_rework`), que já existia no código mas nunca era usado, agora pode ser ligado na
  config (desligado por padrão).
- Log ao iniciar dizendo o que foi encontrado no Block Clover.
- Testes JUnit e build automático no GitHub Actions.

### Mudado
- **A config foi dividida** em `chrono_bubble-server.toml` (regras, na pasta do mundo, enviadas aos clientes) e
  `chrono_bubble-client.toml` (visual). O antigo `chrono_bubble-common.toml` não é mais lido.
- Na tela de configuração, as regras do servidor ficam só para leitura fora de um mundo local; nomes e dicas das
  opções agora são traduzidos; a tela cabe em janelas estreitas.
- Cliente e servidor precisam ter o addon na mesma versão para conectar.
- O Chrono Stasis sustentado pela esfera substitui uma sessão do Grigora em vez de entrar nela com o custo e o
  limite de alvos do Grigora.
- O bloqueio da regeneração durante o renascimento não desfaz mais roubos pequenos (+1/+2) feitos pelo addon.
- Zona e aceleração do tempo agora compartilham a mesma base (`ManaDrainAbility`).
- Renascimento: só acontece se houver Time guardado na Swallowtail. Ao morrer, todo o Time de todas as Swallowtails
  carregadas é gasto; ao renascer, o Time do jogador e o das Swallowtails zeram. As opções `reborn_time`,
  `swallowtail_reserve` e `reserve_cost` foram removidas.
- Renascido, não dá mais para sacar Time da Swallowtail (o Time só volta roubando).
- Mensagens do jogo, dicas da Swallowtail e botões da tela de configuração agora são traduzíveis (pt_br e en_us).

### Corrigido
- O bloqueio da regeneração passiva de Time durante o renascimento só olhava um tick a cada segundo e podia deixar
  a regeneração passar.
- Projéteis parados pelo pulso do Time Hop ficavam presos no ar para sempre se o servidor fechasse durante o pulso,
  e os pulsos continuavam rodando no próximo mundo aberto.
- Projéteis parados pelo pulso do Time Hop podiam continuar andando na tela do cliente.
- A proteção de chegada do Time Hop bloqueava também o dano do vazio e o `/kill`.
- Esferas fantasmas depois de trocar de dimensão ou renascer.
- A esfera de Chrono Stasis só aparecia para quem já estava por perto quando o efeito começou.
- A zona de Chrono Stasis sumia da própria tela ao trocar de dimensão.
- Mana por segundo com casas decimais (ex.: 1.5) aparecia arredondada para baixo.
- Mapas internos do servidor que não eram limpos quando o jogador saía.
- O addon registrava no log cada habilidade usada.
- A esfera piscando (tempo instável) só aparecia para quem já estava por perto.
- A esfera de Time Steal ou sustentada voltava como esfera comum ao recarregar o mundo.
- Mensagens do Time Hop reformulado não eram traduzíveis.
- Os efeitos extras do Time Hop (sem dano de queda, chegada protegida, pulso, bolha fantasma) nunca disparavam:
  o addon escutava tanto o `AbilityUseEvent.Pre` quanto o `.Post` do Block Clover, e o `Post` chegava com o jogador
  já teleportado, apagando o ponto de partida. Agora só o `Pre` é escutado.

## 1.0.0
- Primeira versão.
