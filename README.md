# PomoLume

Aplicativo Android offline de Pomodoro, em Kotlin e Jetpack Compose.
Android 7.0 (API 24) ou superior. Sem conta, servidor ou permissão de internet.

## Uso

O padrão é 25 minutos de foco, 5 de pausa curta e 15 de pausa longa,
com pausa longa após quatro focos concluídos. A tela principal permite
iniciar, pausar, continuar, reiniciar, pular e cancelar a sequência.

- **Reiniciar:** encerra a tentativa iniciada como `CANCELLED`, registrando
  o tempo efetivamente utilizado. Prepara a mesma sessão em `IDLE`, com
  duração original, tipo e ciclo preservados; aguarda um novo início.
- **Cancelar Pomodoro:** pede confirmação e encerra a sequência. Uma sessão
  iniciada fica no histórico como `CANCELLED`, com duração real. O aplicativo
  volta ao foco parado, com contador zerado e duração das configurações
  atuais. Cancelar entre sessões não cria uma sessão fictícia.
- **Pular:** registra `SKIPPED` e prepara a próxima sessão. Um foco pulado
  não incrementa os Pomodoros concluídos.

Mudanças de duração durante uma sessão valem para a próxima sessão.
O reinício da sessão atual continua usando sua duração original.
Pausas e focos podem começar automaticamente, conforme opções independentes.

A engrenagem abre durações, ciclos, automação, notificações, som, vibração,
confirmações, tela ligada, tema e aparência do timer. A personalização oferece
presets gratuitos de estilo e cores, persistidos localmente. Os modelos
incluem metadados para uma possível oferta premium futura; esta versão
não possui cobrança, assinatura ou integração com billing.

## Histórico

Os filtros usam períodos do calendário local:

- **Hoje:** início do dia até o início do próximo.
- **Semana:** segunda-feira até o início da segunda seguinte.
- **Mês:** primeiro dia do mês até o primeiro do próximo.
- **Ano:** primeiro dia do ano até o primeiro do seguinte.
- **Tudo:** histórico completo, sem limite de período.

O resumo acompanha o filtro selecionado. Tempo de foco, Pomodoros concluídos
e dias ativos consideram somente sessões de foco com status `COMPLETED`.
Pausas, cancelamentos e saltos continuam visíveis na lista detalhada,
com horário, duração e status, agrupados por data.

Room agrega os resultados por dia local em SQL, respeitando o fuso do
dispositivo. Os limites dos períodos são calculados com `java.time`,
incluindo meses de durações diferentes, anos bissextos e mudanças de
horário de verão. Consultas limitadas usam comparações diretas no índice
de `endedAt`; estatísticas não precisam carregar todas as entidades.

A semana usa barras diárias, o mês usa um calendário de intensidade e o
ano reúne doze barras mensais. Os gráficos em Compose/Canvas recebem dados
já preparados, oferecem descrições acessíveis e permitem rolagem quando
a largura disponível ou o tamanho da fonte exigir. Agrupamento e preparação
dos dados acontecem fora da thread principal.

## Estrutura ativa

- `feature/pomodoro`: máquina de estados, casos de uso e apresentação.
- `feature/history`: histórico, períodos, agregações e gráficos.
- `feature/settings`: preferências validadas, aparência e apresentação.
- `core/database`: Room v2 e migração não destrutiva de v1.
- `core/datastore`: recuperação persistente do timer.
- `core/service`: serviço em primeiro plano e agendamento do término.
- `core/notification`: controles e avisos respeitando as preferências.
- `core/design`: tokens Material 3, componentes, estilos do timer e previews.
- `core/navigation`: rotas tipadas com Navigation Compose.
- `di/AppModule.kt`: dependências Koin.

A UI usa ViewModels e casos de uso; não acessa diretamente Room,
DataStore, o serviço ou NotificationManager. ViewModels expõem estados
imutáveis por `StateFlow`, observados com APIs conscientes do lifecycle.

A navegação preserva o estado das abas; o período selecionado do histórico
é mantido em `SavedStateHandle`. As configurações ficam em DataStore.
Os textos da interface estão em recursos em português. A anotação
`AppThemePreview` oferece previews claros e escuros dos componentes,
além dos cenários de telas pequenas e fontes ampliadas.

## Recuperação e consistência

O prazo persistido determina o tempo restante. Atualizações visuais
não decrementam um contador e não gravam no banco a cada segundo.
Pausar armazena o saldo; retomar calcula um novo prazo.

Transições finais são persistidas antes da escrita no histórico.
IDs únicos e inserção idempotente evitam sessões duplicadas após uma
interrupção. Ao recuperar um prazo expirado, apenas a sessão realmente
iniciada é concluída; o aplicativo não inventa sessões durante o período
em que o aparelho esteve desligado. Se o início automático estiver ligado,
a próxima sessão começa no momento da recuperação.

O serviço mantém controles na notificação e agenda o prazo no AlarmManager.
A permissão de notificações é contextual; recusá-la não impede o timer.
Sons e vibração também respeitam os canais e as preferências do Android.
Em Android 12/12L, sem acesso a alarmes exatos, o agendamento usa a alternativa
permitida pelo sistema: o saldo continua correto por timestamps, mas o
aviso pode atrasar em repouso profundo.

Após reiniciar o aparelho, abrir o aplicativo reconstrói a sessão salva.
Um encerramento forçado pelas configurações do Android impede execução em
segundo plano até o usuário abrir o app novamente. O sistema também pode
interromper e recriar o processo; os dados persistidos permitem recuperar
o timer e concluir transições pendentes.

Existe uma janela rara entre o commit final da transição e a emissão do
evento de conclusão: uma morte abrupta do processo nesse intervalo pode
impedir o aviso de conclusão. O histórico já confirmado permanece salvo,
sem duplicação da sessão.

## Build e testes

Use o JDK configurado no Android Studio e o SDK 37.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

O último comando exige emulador ou aparelho conectado.

Os testes JVM usam relógio controlado e implementações de teste das
interfaces; verificam ciclos, pausa/retomada, reinício, cancelamento,
início automático, concorrência, falhas de persistência e recuperação.
Também verificam períodos do calendário, horário de verão, agregações
e seleção de filtro. Os testes de DataStore recriam instâncias sobre
arquivos temporários reais.

Os testes Compose e de Room/migração estão em `app/src/androidTest`.
Eles cobrem controles, confirmações, navegação, preferências, histórico,
inserções idempotentes, migração e agregação por dia local.

As cinco jornadas naturais em `app/src/journeysTest` cobrem:

1. Conclusão de foco, pausa/retomada e histórico.
2. Alteração da duração.
3. Timer em segundo plano e controles pela notificação.
4. Recriação da Activity.
5. Reinício da sessão e cancelamento da sequência.

As jornadas são executadas pelo Android Studio e declaram suas condições
iniciais. Testes instrumentados e jornadas são separados da suíte JVM.

APK de desenvolvimento: `app/build/outputs/apk/debug/app-debug.apk`.

## Validação da rodada de refinamentos

- APK de debug e APK de testes compilados; suíte JVM com 108 testes aprovados.
- Suíte instrumentada completa: 29 testes aprovados no emulador,
  cobrindo controles, navegação, configurações e Room.
- Lint sem erros; um aviso de atualização disponível para `core-ktx`.
- No emulador Pixel 10 Pro / Android 17, foram conferidos início,
  pausa/retomada pelo aplicativo e pela notificação, reinício preservando
  o ciclo, cancelamento voltando ao ciclo inicial e encerramento do serviço.
- Uma sessão real de um minuto concluiu em segundo plano, gerou notificação,
  iniciou a pausa automaticamente e apareceu uma única vez no histórico.
  Pular essa pausa preparou o foco do ciclo 2 sem iniciá-lo automaticamente.
- Uma sessão pausada em `00:57` foi recuperada com o mesmo saldo após
  encerramento forçado e nova abertura do processo.
- O filtro Mês foi preservado ao alternar abas. Foram conferidos gradiente,
  temas claro/escuro, seletor de duração e layout de 320 dp com fonte em 200%.
  Nessa combinação, os controles permanecem acessíveis por rolagem.
- Os quatro ciclos completos e todas as combinações de início automático
  são verificados com relógio controlado nos testes da engine.

Os previews principais foram renderizados no Android Studio, incluindo
estados do timer, tema escuro, fonte ampliada, seletores e gráficos.
Os dados de preview não acessam repositórios reais.

O executor das jornadas naturais retornou `Model query failed: UNKNOWN`
antes de executar os passos. Seus fluxos principais foram exercitados
manualmente; não se declara aprovação automática dessas jornadas.

A inspeção de logcat não encontrou crash ou ANR nos fluxos manuais.
A medição de debug no emulador, antes de adiar a inicialização do histórico,
teve abertura fria de aproximadamente 2,7 segundos e registrou quadros
atrasados. Esses números não constituem validação de desempenho em
produção: startup, fluidez, som e vibração
física ainda precisam ser conferidos em aparelho real com build de release.
As preferências alteradas para os testes manuais foram restauradas ao padrão.

## Manutenção

O schema Room é exportado para `app/schemas`. Qualquer alteração futura
do schema deve incrementar a versão e fornecer migração preservando dados.
O estado de um timer em andamento é excluído de backup/transferência para
evitar restaurar uma sessão ativa em outro aparelho.

As declarações de serviço `specialUse` e alarme exato representam o
cronômetro iniciado pelo usuário. Na publicação, essas funcionalidades
devem constar das declarações correspondentes do Play Console.
