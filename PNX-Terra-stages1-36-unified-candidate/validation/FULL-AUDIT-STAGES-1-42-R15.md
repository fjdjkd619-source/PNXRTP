# Полный аудит стадий 1–42 — R15

Дата аудита: 2026-11-28  
Проверенная вершина ветки до записи этого отчёта: `a368f825a3890fde0dea13dfa8d13d29101bcbf4`  
Репозиторий: `fjdjkd619-source/PNXRTP`, ветка `R15`

## Итог

**Статус: BLOCKED — стадии 1–42 нельзя считать полностью интегрированными или готовыми к серверу.**

Проверено дерево R15: 277 tree entries / 230 файлов, 50 Java-файлов в source overlay, 19 Java-тестов и 27 активных patch-файлов. Ветвь содержит существенную платформонезависимую реализацию и regression coverage, но это не эквивалентно полному применению и сборке модифицированных PNX, Terra и PNX-Terra исходников.

## Критические находки

### F-01 — Stage 7 и Stage 18 конфликтуют в активной patch-chain

`apply-stages1-26.sh` применяет `07-stage7-core.patch`, а затем `18-stage18-pnx-core.patch`. Оба патча изменяют одни и те же PNX call sites: `GenerateStage.java`, `Generator.java` и `Level.java`.

Stage 7 уже вводит `GenerateStage.applyAsync`, меняет pipeline `Generator` и добавляет success/failure callbacks в Level generation path. Stage 18 повторно меняет эти же методы, причём его исходные diff hunks основаны на старой pre-Stage-7 реализации. Поэтому последовательная применимость этой цепочки не доказана и имеет явный конфликт контекстов. Нужен единый rebased/merged patch и проверка `git apply --check` по всей цепочке на чистых pinned checkout. Одного `git apply --stat` недостаточно.

### F-02 — Стадии 37–42 не входят в upstream apply chain

В R15 нет активных patch-файлов Stage 37, 38, 39, 40, 41 или 42. Изменённый `PlacementJournal.java` и тест Stage 37 находятся в source overlay, но `apply-stages1-36.sh` заканчивает применение на Stage 36. Следовательно, существующий apply runner не переносит WAL hardening 37–42 в реальный PNX-Terra checkout. Нужны отдельный интеграционный patch/runner для 37–42 либо новая единая patch-chain.

### F-03 — Нет полной сборки изменённых upstream-исходников

В candidate directory нет полного Gradle checkout и wrapper. `verify-upstream-build.sh` — только проверочный скрипт; он не является результатом сборки. В `validation/VALIDATION.md` зафиксированы baseline-сборки отдельных upstream ревизий, но не сборка всех изменённых исходников после применения этой цепочки. Нет доказательства, что весь patch-chain применяется к чистым PNX/Terra/adapter checkout и затем компилируется вместе.

### F-04 — Нет ограниченного роста WAL и in-memory индексов

`PlacementJournal` держит все события в `events`, все placements в `placements`, индексы по target chunk и acknowledgement maps в памяти. WAL append-only; compaction/checkpoint/retention или удаление полностью применённых placements не реализованы. Stage 41 добавляет счётчики, но не ограничивает рост. Для бесконечного исследования мира это существенный долгосрочный риск по RAM, диску и времени восстановления после рестарта.

## Функциональные блокеры

### F-05 — Physics dispatcher не подключён к PNX

`PhysicsIntentBatch` реализует общий детерминированный dispatcher, но `PnxChunkHandle` не переопределяет `applyPhysicsIntents`. Наследуется fail-closed default из `ChunkFinalizationCoordinator.ChunkHandle`, который выбрасывает `UnsupportedOperationException` при любом intent с `applyPhysics=true`. Поэтому physics-enabled placements не имеют работающего PNX executor/callback.

### F-06 — WaterLayerPolicy не включена в фактический путь записи

`WaterLayerPolicy` существует как отдельный контракт, но не подключена к `PNXServerWorld.setBlockState` или `PnxChunkHandle`. Фактическая обработка второго слоя в `PNXServerWorld` ограничена отдельными случаями Kelp, Waterlily, Water и Flowing Water. Общая классификация waterlogged-состояний и полноценная интеграция Bedrock two-layer semantics не завершены.

### F-07 — Terra entity-spawn capture остаётся отключённым

`PNXServerWorld.spawnEntity(...)` проверяет guard и затем выбрасывает `UnsupportedOperationException`. Стадии 27 и 35–36 добавляют формат intent, PNX construction/save/activation/rollback contracts, но Terra spawn-call capture и proxy/transaction integration не включены. Entity-bearing структуры остаются отключёнными; профиль Mangrove с entity budget 3 отклоняется.

### F-08 — Каталог структур не покрывает целевой мир

В `OrigenBoundsCatalog` статически описаны только три профиля: waterfall, dripstone_column и mangrove_tree_procedural. Остальные структуры используют жёсткий fallback envelope ±32 по X/Z и ±128 по Y, с бюджетом 65,536 reads/writes и без сущностей. Это runtime safety cap, а не доказательство, что все скрипты Origen укладываются в него. Структуры за пределами envelope будут отменяться.

Кроме того, текущий `TerraGenerator` загружает существующий ConfigPack и выполняет его stages. Стадии 1–42 в основном строят надёжность cross-chunk placement, WAL, finalization и adapter. Они сами по себе не добавляют полноценный авторский каталог medieval-fantasy контента: замки, древние города, руины, плавающие острова, подземные комплексы и их процедурные комбинации ещё не реализованы этим набором стадий.

### F-09 — Property aliases и глобальная диагностика неполны

Текущий PNX property bridge требует точного совпадения схемы; полный проверенный Terra↔PNX alias catalog не заполнен. Stage 30 guard существует, но не обёрнут вокруг всех Terra/PNX stage entry points. Поэтому нельзя считать, что все ошибки генерации получают единую структурированную диагностику.

## Проверки и что именно они доказывают

### Подтверждено

- GitHub Actions run [46146027245](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46146027245) завершился SUCCESS на commit `26f73b2c4d12a4cc425bcf7458655ea59bb275ff`.
- Он выполнил Java 21 compile с `javac --release 21 -Xlint:all -Werror` и 18 platform-neutral test suites, включая `Stage37WalRecoveryTest` (28 assertions).
- Сравнение commit `26f73b2` с R15 head `a368f825` показывает, что после успешного core run менялись workflow/docs/validation artifacts, но не source Java или test Java. Поэтому этот PASS соответствует текущему source/test overlay.
- Stage 37 tests проверяют complete payload/CRC corruption, invalid magic, нераспознанный хвост, восстановление неполного хвоста, evidence collision, sequence indexes, metrics и close lifecycle.
- В R15 имеются отдельные contract scripts для PNX delegates, BlockEntity, runtime, entity spawn и Stage 36 API patch.

### Не подтверждено

- Новая расширенная CI workflow, добавленная в R15 для запуска всех contract scripts, пока не имеет отдельного успешного run в Actions.
- Полное применение patch-chain к чистым pinned upstream checkout.
- Полная Gradle сборка изменённых PNX/Terra/PNX-Terra.
- Live server generation, physics callback, waterlogged/two-layer correctness, BlockEntity/entity persistence, restart/replay, chunk unload/reload, cross-chunk stress и длительный soak test.
- WAL compaction/рост RAM и диска на длительном исследовании мира.

### Ограничение core test runner

`validation/run-core-tests.sh` намеренно исключает классы, требующие полных PNX/Terra classpaths: `TerraGenerator`, `PNXServerWorld`, PNX codecs/appliers, `PnxChunkHandle`, `PnxCrossChunkFinalizer`, runtime и staged BlockEntity delegates. Их отдельные contract scripts используют narrow API stubs. Это полезные contract tests, но не полная upstream compile/integration.

## Матрица стадий

| Стадии | Что есть в R15 | Аудиторский статус |
|---|---|---|
| 1–2 | Terra placement scope и adapter transaction seam | Patch/source есть; clean upstream apply/build не подтверждены |
| 3–6 | Исторические прототипы; обязанности заменены Stage 7–8 | Не отдельные активные стадии в финальной цепочке |
| 7–11 | Async pipeline, adapter core, durable storage | Код/patch есть; patch overlap с Stage 18 и upstream build не закрыты |
| 12–15 | Bounds, footprints, Origen runtime guard | Реализовано частично; статический каталог ограничен тремя профилями |
| 16–17 | WAL runtime, cross-chunk scheduler, retries, placement tickets | Core tests есть; полный PNX pipeline integration не подтверждён |
| 18–20 | Target projection, async finalization, durable acknowledgement | Core tests есть; upstream pipeline patch conflict не закрыт |
| 21–25 | BlockEntity intents, NBT, water/physics policy, diagnostics | Contracts есть; physics/water/global diagnostics wiring неполные |
| 26 | Deterministic placement ordinal | Regression coverage есть; clean upstream patch apply не подтверждён |
| 27 | Entity spawn intent format, WAL v4, finalization hooks | Core contracts есть; Terra capture выключен |
| 28 | Physics batch dispatcher | Только общий contract; PNX callback не подключён |
| 29 | Strict property alias contract | Только exact-match path; полный alias catalog отсутствует |
| 30 | GenerationStageGuard | Guard есть; глобальная обвязка отсутствует |
| 31 | Bounds manifest validator | Validator есть; полный static Origen catalog отсутствует |
| 32 | Runtime close sequence | Core lifecycle test есть |
| 33 | Exact-ref build gate script | Скрипт есть; изменённый upstream build не выполнен |
| 34 | Fail-closed release packager | Блокирует выпуск корректно; production package не создан |
| 35–36 | PNX entity rollback/deferred lifecycle | Stub contracts есть; Terra spawn capture и full upstream integration отсутствуют |
| 37–38 | WAL fail-closed recovery | Source overlay и tests есть; нет upstream apply patch |
| 39 | Bounded frame recovery allocation | Source overlay есть; upstream apply patch отсутствует |
| 40 | Sequence indexes | Source overlay и tests есть; upstream apply patch отсутствует |
| 41 | Journal metrics | Source overlay есть; compaction/retention отсутствуют |
| 42 | Close/poisoned append lifecycle | Source overlay и tests есть; upstream apply patch отсутствует |

## Рекомендуемый порядок закрытия блокеров

1. Пересобрать PNX core patch sequence: объединить/rebase Stage 7 и Stage 18 изменения в `GenerateStage`, `Generator`, `Level`; прогнать всю цепочку последовательно на чистых pinned checkout.
2. Создать и подключить patch/runner для Stage 37–42, включая WAL source и regression tests.
3. Выполнить clean exact-ref patch application и полные Gradle builds PNX, Terra и adapter; не смешивать baseline build с modified-source build.
4. Подключить PNX physics executor и общую water-layer policy, затем добавить интеграционные тесты.
5. Завершить Terra entity spawn capture или оставить entity-bearing structures официально выключенными.
6. Реализовать WAL compaction/checkpoint с crash-safe semantics и soak test роста памяти/диска.
7. Завершить alias catalog, глобальную диагностику и аудит bounds всего используемого Origen pack.
8. Только после этого — live server generation, restart/replay, persistence, lighting и cross-chunk stress tests.
9. Отдельно наполнить ConfigPack требуемым medieval-fantasy контентом; infrastructure stages 1–42 этого не заменяют.

**Решение аудита:** не устанавливать R15 на production. Core overlay имеет успешные Java 21 тесты, но полный путь 1–42 не интегрирован в upstream patch chain и имеет функциональные незакрытые области.


## Follow-up validation — bounded WAL encoder

После исходного аудита исправлен узкий дефект F-04 на уровне сериализации placement frame: `PlacementJournal.encodePlacement` теперь пишет через ограниченный поток с пределом `MAX_FRAME` (64 MiB), который отклоняет превышение до передачи байтов в backing buffer. Добавлен `PlacementJournalEncodingLimitTest`: точная граница, одиночный overflow, bulk overflow, отсутствие роста backing buffer при отказе и нулевой лимит.

GitHub Actions [46146537903](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46146537903) на commit `0f1d6508b57581d4825e9e4591f163512e3ef69e` завершился SUCCESS: Java 21 compile, platform-neutral suites, PNX delegate/BlockEntity/runtime/entity-spawn contracts, Stage 36 API fixture, shell syntax и patch parsing.

Это устраняет только риск чрезмерного временного выделения памяти при кодировании одной большой placement-записи. **F-04 остаётся открытым в части неограниченного WAL и in-memory индексов:** compaction/checkpoint/retention ещё нет. F-02 также остаётся открытым: Stage 37–42 пока не добавлены в upstream apply chain; этот source-overlay fix не является применённым PNX/Terra upstream patch.


## Follow-up: source and patch-chain corrections (R15)

After the initial audit, the Stage 37–42 adapter patch and `apply-stages1-42.sh` were added. GitHub Actions verified that the patch applies to the extracted Stage 36 overlay baseline and that the resulting adapter source and tests byte-match the current R15 overlay.

The PNX patch sequence was then checked against the exact pinned upstream commit `c57499365737654eca32df008751ef6bca5af142`. Stage 18 was rebased on Stage 7; Stage 11 was rebased to the actual LevelDBStorage API and corrected to use `WriteOptions.sync(true)` as a separate call; Stage 36 was rebased against the actual PNX Entity.java after Stage 35. The workflow now applies the PNX sequence Stage 7 → 11 → 18 → 22 → 35 → 36 successfully. Stage 36 verification confirms the staged spawn-event path occurs before chunk and Level registration.

GitHub Actions [46147409727](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46147409727) completed SUCCESS. Java 21 core and contract suites passed; Stage37WalRecoveryTest passed 36 assertions and PlacementJournalEncodingLimitTest passed 6 assertions. This is stronger than the previous synthetic patch-context checks, but it still proves patch applicability and contract tests—not a full PNX Gradle build or live server integration.

Remaining open findings: WAL compaction/checkpointing and retention are absent; PNX/Terra adapter full clean patch application has not been run as one integrated three-repository chain; full Gradle builds, JAR packaging, live generation, restart/replay, and long-soak tests remain outstanding. Physics executor, generic water-layer policy, Terra entity spawn capture, Origen bounds coverage, and complete property aliases remain unresolved. Production gate stays BLOCKED.


## Recheck after initial audit — R15, 2026-11-28

This section supersedes the time-of-audit statements in F-01 and F-02 where noted; earlier findings remain as historical observations.

### Resolved integration findings

- **F-01 resolved for pinned revisions:** the active workflow now applies Stage 7 followed by the rebased Stage 18 PNX patch to a clean checkout at `c57499365737654eca32df008751ef6bca5af142`. The complete Stage 1–42 chain subsequently applied successfully to clean pinned PNX, Terra and PNX-Terra checkouts. This proves patch application for these exact revisions, not for other snapshots.
- **F-02 resolved:** `37-42-wal-hardening.patch` is active and `apply-stages1-42.sh` applies it after Stage 1–36. The patch is validated against the exact Stage 36 overlay baseline and byte-compared with the Stage 37–42 `PlacementJournal.java` source. Regression tests remain in the Java validation overlay.
- **Stage 15 forwarding correction:** `BufferedWorld.beginPlacement` now forwards an origin translated by the buffer offsets before invoking the platform guard. Feature and TerraScript nested structure IDs fall back to `StringIdentifiable` or class name when no `IdentifiedStructure` implementation is present.

### Latest verified validation

GitHub Actions [46150102841](https://github.com/fjdjkd619-source/PNXRTP/actions/runs/46150102841) Java 21 job completed SUCCESS. It parsed every active patch, checked shell syntax, applied Stage 37–42 to the exact Stage 36 overlay baseline and compared the resulting source, applied Stage 7 then Stage 18 to pinned PNX, and passed the platform-neutral and PNX delegate/BlockEntity/runtime/entity-spawn/Stage 36 API contract suites.

### Full Gradle build status

The first exact-upstream Gradle attempt completed patch application and reached Terra `:common:api:compileJava`, then failed in `:platforms:bukkit:common:validateAccessWidener` with `java.nio.file.ClosedFileSystemException`. No Java source compilation error was reported before that task failure. The verification script was adjusted to use `--no-configuration-cache --max-workers=1 --stacktrace` for each upstream build. The rerun must complete successfully before the full upstream build can be marked PASS. The exact patch chain is therefore confirmed applied, but the modified upstream projects are not yet certified as built.

### Remaining production blockers

WAL compaction/checkpoint and bounded retained indexes remain absent; PNX physics executor remains fail-closed; WaterLayerPolicy remains unwired; Terra entity-spawn capture remains disabled; Origen static bounds and Terra↔PNX property alias catalog remain incomplete; runtime server generation, restart/replay, persistence and long soak tests remain outstanding. The desired medieval-fantasy ConfigPack content is not supplied by infrastructure stages 1–42. **Do not deploy R15.**
