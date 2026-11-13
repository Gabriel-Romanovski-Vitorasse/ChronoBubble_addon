# Chrono Bubble

Addon para **Minecraft 1.16.5 / Forge 36** que amplia a magia de Tempo do mod
**Block Clover**.

O addon acessa o Block Clover por reflexão: compila sem o `.jar` dele e, se o Block Clover
não estiver instalado (ou mudar internamente), os recursos que dependem dele ficam desligados. Ao iniciar, o log
diz o que foi encontrado (procure por `[ChronoBubble] Block Clover:`), para ficar fácil ver o que parou de funcionar
depois de uma atualização do Block Clover.

O addon precisa estar instalado **no servidor e nos clientes, na mesma versão**. Quem tentar entrar sem ele, ou com
outra versão, é recusado ao conectar.

## Recursos

| Recurso | Como usar |
|---|---|
| Esfera com anéis de numerais romanos em volta de quem está em Chrono Stasis | automático |
| Chrono Stasis arremessável (congela o alvo que a esfera acertar) | Shift + Chrono Stasis |
| Time Steal arremessável (a vida drenada vira Time) | Shift + Time Steal |
| Soco do Time Steal com apodrecimento: o alvo sente o tempo passar por 3 s e o dano e o Time chegam aos poucos | soco do Time Steal |
| Chrono Stasis sustentado (prende até N alvos; o dano recebido fica guardado e cai de uma vez ao soltar) | Alt + Shift + Chrono Stasis |
| Chrono Stasis Grigora sustentado (prende todos em volta) | Alt + Chrono Stasis Grigora |
| Zona de Chrono Stasis: para projéteis de outros no ar e, depois de 2 s, acelera o tempo deles até se desfazerem | tecla **B** |
| Acelerar o tempo (Velocidade e Pressa para você, Lentidão para os mobs em volta) | tecla **V** |
| Antever ataques (avisa projéteis e mobs prestes a te atingir) | automático com a passiva **Time Magic Zone** do Block Clover ativa |
| Efeitos extras do Time Hop (sem dano de queda, chegada protegida, pulso de tempo parado, bolha fantasma) | automático ao usar o Time Hop |
| Time Hop reformulado (deslize dentro de uma esfera de tempo parado, no lugar do teletransporte) | desligado por padrão; ligue em `hop_rework.enabled` |
| Números romanos subindo ao roubar Time | automático |
| Restauração do tempo: blocos quebrados ou explodidos há pouco voltam (sem duplicar itens; ver abaixo) | Shift + Time Healing |
| Swallowtail: artefato que armazena Time | clique direito guarda, Shift + clique direito devolve |
| Renascimento com grimório de uma página | ver abaixo |
| Tela de configuração | tecla **K** ou Mods → Chrono Bubble → Config |
| Estado do addon para um jogador | `/chronobubble status` (o seu) ou `/chronobubble status <jogador>` (operadores) |
| Conquistas: *Asas do Tempo*, *Reserva Completa*, *O Tempo Não Para* | aba própria no menu de conquistas |

Todas as teclas podem ser trocadas em Opções → Controles → Chrono Bubble.

### Renascimento e Swallowtail

- Só renasce quem usa magia de Tempo **e** morre carregando uma Swallowtail com Time guardado.
  Sem Time na Swallowtail, a morte é uma morte comum.
- Ao renascer, **tudo zera**: o Time do jogador e o Time de todas as Swallowtails que ele carregava.
- Renascido, o jogador fica com o grimório de uma página: Chrono Stasis e Chrono Stasis Grigora ficam bloqueados,
  não há regeneração passiva de Time e não dá para sacar Time da Swallowtail. O Time só volta roubando.
- Ao chegar ao `unlock_time` (padrão 200), o grimório e os poderes voltam (é preciso equipá-los de novo).
- **A passiva Time Ressurection do Block Clover vem antes da Swallowtail.** Se ela estiver pronta (carga cheia e mais
  de 300 de Time), ela salva o jogador até de um golpe que mataria de uma vez, consumindo todo o Time como de costume,
  e a Swallowtail não é usada. Se não estiver pronta, vale o renascimento pela Swallowtail.

### Restauração do tempo

Shift + **Time Healing** faz voltar, em volta de você, os blocos quebrados ou explodidos nos últimos segundos
(padrão: 60 s, raio 8), do mais recente para o mais antigo.

- **Não duplica itens:** um bloco só volta se os itens que ele derrubou ainda estiverem no chão por perto, e eles
  somem quando o bloco volta. Se alguém já pegou, aquele bloco não volta.
- Uma explosão volta inteira ou não volta.
- Ficam de fora: minérios e outros blocos que soltam XP, blocos com conteúdo (baús, fornalhas...) e blocos de duas
  partes (portas, camas, plantas altas). Tochas e outros blocos que caem sozinhos quando o apoio some também não
  são registrados.
- Não apaga nada: se o lugar já foi ocupado por outro bloco, aquele bloco não volta.
- Custo: Time fixo + Time por bloco + mana, com recarga (seção `restoration` da config).

### Tempo avançado (fim do Chrono Stasis)

Quando o Chrono Stasis normal (o soco do Block Clover ou a esfera arremessada comum) acaba sozinho, o tempo do alvo
salta à frente e quem congelou paga Time pela vida dele:

- se o seu Time cobre a vida toda, o alvo **envelhece até virar pó** (padrão: 3 Time por ponto de vida; um zumbi
  custa 60);
- se não cobre, ele leva o dano que o seu Time pagou;
- ignora armadura; o totem da imortalidade ainda salva;
- jogadores também podem envelhecer (opção `aging.players` para servidores sem PvP);
- Wither e Ender Dragon levam no máximo 20% da vida e nunca morrem por isso;
- se o efeito for tirado antes do fim (leite, troca para o sustentado...), nada acontece. O sustentado, o Grigora e
  o Time Hop não envelhecem o alvo.

### Wither

O Wither ignora efeitos de poção, então ganhou regras próprias (seção `wither` da config):

- Chrono Stasis (arremessado, sustentado) e Grigora congelam o Wither só 30% do tempo normal (no máximo 3 s), metade
  disso na segunda fase (abaixo de metade da vida), e depois ele resiste ao tempo por 15 s.
- O Time Steal arremessável drena metade da vida, mas cada ponto vale o dobro de Time.
- O Ender Dragon continua de fora: congelar a IA dele quebraria as fases da luta.

### Grimório do Tempo

Posição e tamanho ajustáveis na config do cliente (tecla **K**, página "Visual e HUD"): lado, altura, frente/trás e
tamanho. Também dá para desligá-lo.

### Receita da Swallowtail

```
M V M      M = Pedregulho com musgo
D C D      V = Trepadeira
M V M      D = Diamante, C = Relógio
```

## Trocar os sons

Os sons ficam em `src/main/resources/assets/chrono_bubble/sounds/` e são ligados aos eventos em `sounds.json`:

| Evento | Quando toca | Arquivo hoje |
|---|---|---|
| `stasis_start` | o tempo para (congelamento, zona, Grigora...) | `stasis_start.ogg` |
| `stasis_end` | o tempo volta a correr | `stasis_end.ogg` |
| `time_dissolve` | a zona desfaz um projétil | `stasis_end.ogg`, mais agudo |

Para trocar: coloque o novo arquivo `.ogg` (Ogg Vorbis, de preferência mono) na pasta `sounds/` e mude o `"name"` do
evento no `sounds.json` (por exemplo `"chrono_bubble:meu_som"` para `sounds/meu_som.ogg`). Não precisa mexer no código.

## Configuração

São dois arquivos:

| Arquivo | O que tem | Onde fica |
|---|---|---|
| `chrono_bubble-server.toml` | regras de jogo (custos, raios, recargas, renascimento...) | `<mundo>/serverconfig/` |
| `chrono_bubble-client.toml` | visual e HUD deste computador | `config/` |

As regras do servidor são enviadas a cada jogador ao entrar, então todos jogam (e veem a zona) com os mesmos valores.
Para que um mundo novo já nasça com as suas regras, copie o `chrono_bubble-server.toml` para a pasta `defaultconfigs/`.

Na tela de configuração (tecla **K**), as regras do servidor só podem ser mudadas num mundo aberto neste computador
(singleplayer ou LAN). Num servidor de outra pessoa elas aparecem só para leitura.

> Versões antigas usavam `config/chrono_bubble-common.toml`. Esse arquivo não é mais lido; se você tinha mudado
> algo nele, repita as mudanças no novo `chrono_bubble-server.toml`.

## Compilar e testar

Requer JDK 8 (o Gradle 7.3 também roda com JDK 11 ou 17, mas não com 21).

```bash
./gradlew build
```

O `.jar` fica em `build/libs/`. O `build` também roda os testes JUnit (`src/test/java`), que cobrem as contas puras:
números romanos, previsão de trajetória de projéteis e o cruzamento de projéteis com a esfera da zona.
Só os testes:

```bash
./gradlew test
```

A cada push, o GitHub Actions (`.github/workflows/build.yml`) compila, roda os testes e publica o `.jar` como artefato.

## Ferramentas

A pasta `tools/` tem scripts Python para gerar a textura da faixa de numerais, gerar o `ChronoConfig.java` a partir
de `tools/config/opts.py` e fazer uma prévia da bolha do Chrono Stasis fora do jogo. Veja `tools/README.md`.

## Estrutura do código

| Classe | Papel |
|---|---|
| `BlockCloverBridge` | todo acesso ao Block Clover (reflexão), com os nomes de classes e ids num lugar só |
| `ChronoConfig` | opções dos dois arquivos; o código lê direto daqui (`ChronoConfig.X.get()`) |
| `ManaDrainAbility` | base abstrata das habilidades liga/desliga que gastam mana por segundo (`ChronoZone`, `TimeAcceleration`) |
| `Msg` | mensagens traduzíveis (`assets/chrono_bubble/lang`) |
| `Client*` | tudo o que só existe no cliente (desenho, teclas, HUD) |

## Idiomas

Português (pt_br) e inglês (en_us). Os nomes das opções da tela de configuração ainda estão só em português.

## Licença

MIT — veja [LICENSE](LICENSE).
