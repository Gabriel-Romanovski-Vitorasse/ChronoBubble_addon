# Changelog

## Não lançado

### Mudado
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

## 1.0.0
- Primeira versão.
