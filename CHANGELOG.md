# Changelog

## 1.1.0 (não lançado)

### Adicionado
- **A zona acelera o tempo dos projéteis parados**, como no anime: depois de 2 s parado, o projétil se desfaz em
  partículas, sem dano e sem item. Enquanto isso, a faixa do orbe em volta dele gira cada vez mais rápido e, no fim,
  a bolha encolhe e pisca. Tridentes nunca se desfazem (são itens de jogador); flechas comuns, conforme a config.
  Opções `zone.dissolve_seconds` (0 = comportamento antigo), `zone.dissolve_mana` e `zone.dissolve_arrows`.
- Som próprio para o projétil desfeito (`time_dissolve`), por enquanto o mesmo arquivo do fim do Chrono Stasis, mais
  agudo, para poder ser trocado depois sem mexer no código.
- **Magias de tempo no Wither**, com regras de chefe. O Wither ignora efeitos de poção, então o Chrono Stasis do Block
  Clover nunca pegava nele. Agora a esfera arremessada (normal ou sustentada) e o Grigora o congelam com um
  congelamento próprio: só 30% do tempo normal (máximo 3 s), metade disso na segunda fase, e 15 s de "resistência ao
  tempo" depois de cada congelamento. O Time Steal arremessável drena metade da vida no Wither, mas cada ponto rende o
  dobro de Time, e avisa quando o Wither ainda está nascendo (invulnerável). Seção `wither` da config. O Ender Dragon
  continua de fora (`grigora.skip_bosses`).
- **Visual novo do Chrono Stasis**, mais próximo do anime, em todos os lugares onde a bolha aparece (mobs congelados,
  zona, projéteis parados, bolha fantasma do Time Hop e a esfera arremessada):
  - esfera com efeito de bolha (fresnel): borda azul, centro quase transparente e um brilho suave, igual de qualquer
    lado;
  - uma faixa só, fina, inclinada na direção de quem olha e girando devagar, com textura nova (fundo azul
    translúcido, bordas brancas, numerais brancos irregulares com letrinhas, sem divisórias), sem numerais esticados
    nem espelhados na frente;
  - a esfera arremessada ganhou a mesma faixa em miniatura;
  - segundo anel opcional: `visual.second_ring` (desligado por padrão);
  - cada bolha tem a faixa num ângulo e sentido próprios (sorteados pelo id do mob, fixos), como no anime.
- **Restauração do tempo** (Shift + Time Healing): faz voltar os blocos quebrados ou explodidos há pouco em volta do
  jogador, do mais recente para o mais antigo, aos poucos e com partículas. Sem duplicar itens: um bloco só volta se
  os itens que ele derrubou ainda estiverem no chão, e eles somem quando o bloco volta; explosões voltam inteiras ou
  não voltam; minérios (dão XP), blocos com conteúdo (baús...) e blocos de duas partes (portas, camas) ficam de fora.
  Custo em Time (fixo + por bloco) e mana, com recarga. Seção `restoration` da config.
- Posição e tamanho do grimório do Tempo ajustáveis na config do cliente (`visual.grimoire_side`, `grimoire_height`,
  `grimoire_front`, `grimoire_scale`) e opção para desligá-lo (`visual.grimoire_enabled`).
- Pasta `tools/` com os geradores da textura da faixa, da config e uma prévia da bolha fora do jogo.
- "Antever ataques" agora exige a passiva **Time Magic Zone** do Block Clover (a magia que "sente ataques futuros")
  desbloqueada e ativa. Opção `precognition.require_time_magic_zone`.
- A passiva **Time Ressurection** do Block Clover age antes do renascimento pela Swallowtail: se estiver pronta, salva
  o jogador também de golpes que matariam de uma vez (o Block Clover só a checava no tick seguinte) e a Swallowtail
  fica intacta. Opção `rebirth.time_ressurection_first`.
- `/chronobubble status` mostra se a Time Magic Zone está ativa e se a Time Ressurection está pronta.
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
  já teleportado, apagando o ponto de partida. Agora o `Post` é ignorado. (Uma primeira versão dessa correção
  escutava só o `Pre` e quebrou os arremessos, porque Chrono Stasis e Time Steal disparam o evento base, sem `Pre`.)

## 1.0.0
- Primeira versão.
