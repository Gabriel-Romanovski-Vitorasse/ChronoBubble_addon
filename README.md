# Chrono Bubble

Addon para **Minecraft 1.16.5 / Forge 36** que amplia a magia de Tempo do mod
**Block Clover**.

O addon acessa o Block Clover por reflexão: compila sem o `.jar` dele e, se o Block Clover
não estiver instalado (ou mudar internamente), os recursos que dependem dele simplesmente não fazem nada.
O addon precisa estar instalado **no servidor e nos clientes**.

## Recursos

| Recurso | Como usar |
|---|---|
| Esfera com anéis de numerais romanos em volta de quem está em Chrono Stasis | automático |
| Chrono Stasis arremessável (congela o alvo que a esfera acertar) | Shift + Chrono Stasis |
| Time Steal arremessável (a vida drenada vira Time) | Shift + Time Steal |
| Chrono Stasis sustentado (prende até N alvos; o dano recebido fica guardado e cai de uma vez ao soltar) | Alt + Shift + Chrono Stasis |
| Chrono Stasis Grigora sustentado (prende todos em volta) | Alt + Chrono Stasis Grigora |
| Zona de Chrono Stasis (para projéteis de outros no ar) | tecla **B** |
| Acelerar o tempo (Velocidade e Pressa para você, Lentidão para os mobs em volta) | tecla **V** |
| Antever ataques (avisa projéteis e mobs prestes a te atingir) | automático para usuários de Tempo |
| Efeitos extras do Time Hop (sem dano de queda, chegada protegida, pulso de tempo parado, bolha fantasma) | automático ao usar o Time Hop |
| Números romanos subindo ao roubar Time | automático |
| Swallowtail: artefato que armazena Time | clique direito guarda, Shift + clique direito devolve |
| Renascimento com grimório de uma página | ver abaixo |
| Tela de configuração | tecla **K** ou Mods → Chrono Bubble → Config |

Todas as teclas podem ser trocadas em Opções → Controles → Chrono Bubble.

### Renascimento e Swallowtail

- Só renasce quem usa magia de Tempo **e** morre carregando uma Swallowtail com Time guardado.
  Sem Time na Swallowtail, a morte é uma morte comum.
- Ao renascer, **tudo zera**: o Time do jogador e o Time de todas as Swallowtails que ele carregava.
- Renascido, o jogador fica com o grimório de uma página: Chrono Stasis e Chrono Stasis Grigora ficam bloqueados,
  não há regeneração passiva de Time e não dá para sacar Time da Swallowtail. O Time só volta roubando.
- Ao chegar ao `unlock_time` (padrão 200), o grimório e os poderes voltam (é preciso equipá-los de novo).

### Receita da Swallowtail

```
M V M      M = Pedregulho com musgo
D C D      V = Trepadeira
M V M      D = Diamante, C = Relógio
```

## Configuração

O arquivo `config/chrono_bubble-common.toml` é criado na primeira vez que o jogo abre.
Em servidor dedicado, valem os valores do arquivo do servidor.

## Compilar

Requer JDK 8.

```bash
./gradlew build
```

O `.jar` fica em `build/libs/`.

## Idiomas

Português (pt_br) e inglês (en_us). Os nomes das opções da tela de configuração ainda estão só em português.

## Licença

MIT — veja [LICENSE](LICENSE).
