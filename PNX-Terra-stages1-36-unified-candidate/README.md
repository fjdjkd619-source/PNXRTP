# PNX–Terra: единый кандидат исходников стадий 1–36

**Статус: единый кандидат overlay исходников стадий 1–36. Это ещё не готовая upstream-сборка и не пакет для установки.**

## Закреплённые версии

- PowerNukkitX 3.0.6 — `c57499365737654eca32df008751ef6bca5af142`
- PNX-Terra 1.0.0 — `74318b4a1eae95322550f12d5eb8a76d92a93731`
- Terra 6.6.1-BETA — `05ddc8b8a66e17412670647cce78ef837b091256`
- Origen 2.2.0 — `228426bb192a8d6029952aae1a77008dea5f3cda`
- Java compile target — 21

## Содержимое

- `source/` — объединённый adapter/core overlay стадий 1–36; новые модули стадий 27–34 описаны в `STAGES27-34.md`, а entity-finalization adapter стадий 35–36 — в `STAGE35.md` и `STAGE36.md`.
- `tests/` — 18 тестовых наборов стадий 15–34, включая WAL v4, entity-intent finalization, physics dispatch, property aliases, diagnostics, bounds и lifecycle. Отдельный Stage 35–36 PNX entity-applier contract suite запускается через `validation/run-entity-spawn-contract-compile.sh`.
- `patches/` — 22 активных патча цепочки стадий 1–26, Stage 27–34 core overlay, два Stage 35 adapter/PNX lifecycle patch и два Stage 36 deferred-entity lifecycle patch; исходные эксперименты Stage 26 сохранены в `history/experimental-stage26/`.
- `history/original-archives/` — исходные ZIP стадий, использованные при восстановлении.
- `history/reports/` — отчёты стадий 1, 2, 13 и 14 и исходный каталог границ Origen.
- `validation/` — воспроизводимая компиляция независимого ядра и результаты проверок.

Это единое **дерево overlay исходников проекта**, но не полный checkout трёх upstream-репозиториев. Terra Core и PNX Core по-прежнему изменяются отдельными патчами, которые должны применяться к точным закреплённым upstream-коммитам.

## Дополнительные hardening-исправления в активной цепочке 1–26

### 1. Уникальность PlacementKey

`AnchorSession` теперь выдаёт каждому корневому размещению собственный монотонный `placementOrdinal`. `PNXServerWorld` использует ordinal из `PlacementTicket`, а не константу `0L`. Ordinal назначается под scheduler lock; исчерпание диапазона приводит к явному отказу, а не к переполнению и повторному ID.

Условие детерминированного восстановления: Terra должен повторять корневые вызовы для одного anchor в том же последовательном порядке. Счётчик начинается с нуля при повторном запуске незавершённого anchor; это позволяет повторно получить прежние ключи для уже записанных placements. Параллельное недетерминированное изменение порядка feature-вызовов этим решением не маскируется и должно быть запрещено/проверено интеграционным тестом.

### 2. Durable save перед digest acknowledgement

- Stage 11 `LevelDBStorage.writeChunkDurable(IChunk)` выполняет LevelDB write с `WriteOptions.sync(true)`.
- `PnxChunkHandle.saveDurably()` больше не вызывает обычный `LevelDBProvider.saveChunk()`, который использует `sync(false)`.
- Адаптер требует именно закреплённый `LevelDBProvider`; неизвестный provider отклоняется без недолговечного fallback.
- Dirty-флаг очищается до сериализации по той же race discipline, что и PNX autosave; при ошибке он восстанавливается.
- `ChunkFinalizationCoordinator.persist()` сохраняет чанк и только после успешного возврата вызывает `PlacementJournal.appendChunkApplied()`. WAL append выполняет `channel.force(true)`. При ошибке save acknowledgement не записывается.

Добавлена проверка, что ошибка сохранения не приводит к `ChunkApplied`.

## Порядок патчей

В `patches/` оставлена активная последовательность для стадий 1–26. Точные базовые ревизии и порядок зафиксированы в `apply-stages1-26.sh`:

1. Stage 1 — `01-stage1-terra-placement-scope.patch` (Terra Core).
2. Stage 2 — `02-stage2-placement-transactions.patch` (PNX-Terra).
3. Stages 3–6 — ранние самостоятельные прототипы сохранены в `history/original-archives/`; их финальные обязанности заменены объединённой реализацией Stage 7 (PNX async pipeline) и Stage 8 (adapter core). Эти старые снимки не надо накладывать поверх позднего кода.
4. Stages 7–11 — `07-stage7-core.patch`, `08-stage8-adapter.patch`, `09-stage9-adapter.patch`, `10-stage10-adapter.patch`, `11-stage11-pnx-durable-storage.patch`, `11-stage11-adapter.patch`.
5. Stages 12–15 — bounds/footprints и Origen guard объединены в `15-stage15-terra-core.patch` и `15-stage15-adapter.patch`; исходные отчёты и реестр сохранены в `history/reports/`.
6. Stages 16–17 — `16-17-stage16-17-adapter.patch` и `16-17-stage16-17-terra-generator.patch`.
7. Stages 18–20 — `18-stage18-pnx-core.patch` и `18-20-stage18-20-adapter.patch`.
8. Stages 21–25 — `21-25-stage21-25-adapter.patch`.
9. Финальное исправление найденных дефектов внутри цепочки 1–25 — `supplemental-stages15-25-runtime-hardening.patch` (ordinal, durable save, ограниченный runtime fallback, повтор неудавшихся generation requests и регрессионные тесты).
10. Stage 22 BlockEntity integration increment — five patches add the pinned little-endian NBT codec/hash marker, transactional intent applier, same-Level-scheduler finalization and early durable save, TerraScript staged Sign/MobSpawner/Container capture, and PNX MobSpawner Delay persistence. Supported TerraScript `state`/`loot` capture is implemented; full upstream/runtime certification remains open.

11. Stage 26 — `26-stage26-deterministic-placement-ordinal.patch`: добавляет ограниченный монотонный ordinal корневого размещения в пределах AnchorSession, включает его в PlacementKey и проверяет уникальность ordinal при нескольких корневых размещениях. Патч размещён в правильных Maven roots (`src/main/java`, `src/test/java`).

Запуск: `./apply-stages1-26.sh --pnx /path/to/PowerNukkitX --terra /path/to/Terra --adapter /path/to/PNX-Terra`. Скрипт проверяет точные исходные commit SHA и чистоту трёх checkout, затем применяет патчи по порядку. Он **не** означает, что последующая Gradle-сборка уже прошла. Важно: исходный PNX `c574993` пока имеет отдельный Protocol API build blocker; успешный baseline с PNX successor `ff86b15` был собран с неизменённым PNX-Terra, но применение всей этой patch-chain к successor ещё не проверено.

Повторяющиеся копии патчей и вспомогательная выборка Stage 15 перемещены в `history/duplicate-patches/`. Экспериментальные варианты Stage 26 сохранены в `history/experimental-stage26/`; проверенный ordinal-патч Stage 26 включён в активную цепочку.

## Current candidate archive

The current R14 archive contains the Stage 1–36 source overlay and patch chain. It is not a complete upstream checkout or an installable JAR; full modified-source build and runtime certification remain blocked.

## Bounded runtime fallback for the Origen pack

The three statically audited profiles remain available as exact profiles. Other structure IDs now resolve to a strict runtime fallback rather than being silently disabled:
- horizontal write/read envelope: +/-32 blocks from the root origin;
- vertical envelope: +/-128 blocks;
- maximum 65,536 writes and 65,536 reads per root placement;
- entity spawns: rejected by Terra live capture until Terra spawn-call intents are captured; PNX durable entity construction/UUID replay/rollback is now present in Stages 35–36;
- all nested scripts share the same root guard (`*` dependency allowance).

This is **not** a claim that every script's theoretical extent was statically proven. It is a hard runtime cap: every read/write is checked at the adapter boundary, and any out-of-envelope operation poisons and aborts the entire root placement. The scheduler prefetch horizon is 32 blocks, covering all writes that the fallback can commit. That corresponds to up to 25 source anchors for a target chunk; the overlap is intentionally conservative and should be load-tested on the target server. Entity-bearing roots (including the known mangrove profile) remain disabled until entity intents can be durably committed.

## Additional fixes to Stage 17 and adapter safety

Повторная проверка выявила две связанные опасные гонки в планировщике:

1. Retry-loop повторно отправлял запросы даже для anchor, чей запрос PNX уже принял. Runtime requester при таком повторе мог сбросить выполняемый chunk обратно в `GENERATED`, создавая риск повторного запуска Terra PopulateStage. Теперь повторяются только отклонённые/ещё не принятые запросы.
2. Целевой anchor входит в собственный overlap envelope. Ранее `PopulateStage` регистрировал зависимости до открытия `AnchorSession`, поэтому мог запросить повторную генерацию самого chunk, который уже обрабатывался. Теперь сессия открывается до регистрации, а scheduler пропускает активные сессии и ждёт их WAL-маркера завершения.
3. Ранее принятый PNX-запрос не переотправляется, пока PNX удерживает его в generation queue. Если PNX освободил queue slot без durable anchor-complete marker (ошибка до или во время PopulateStage), scheduler теперь снимает устаревшую отметку и повторно отправляет запрос. Runtime использует существующий `Level.isChunkGenerating(x,z)`; добавлен regression test на принятый, затем провалившийся запрос.
4. `PNXChunkDelegate.getX/getZ` теперь возвращают координаты реального `IChunk`, а не константу `0`.
5. `PNXBlockStateDelegate.has/get/set` поддерживают только точное совпадение ID, Java-типа и полного набора значений Terra/PNX property. Сериализация использует `getSerializedValue()`. Несовместимые свойства отклоняются явно вместо `false/null`.
6. `PNXServerWorld.getBlockEntity` outside a placement still returns a read-only wrapper from an already loaded chunk. Inside an active placement, staged sign/spawner/container blocks receive detached Terra delegates; `update(false)` records journal intents instead of mutating live PNX objects. Unsupported BlockEntity types remain fail-closed.

Добавлены regression tests для scheduler и отдельный Java 21 contract behavior test для property mapping, координат chunk и read-only BlockEntity wrapper. Отдельно устранён синхронный force-load в `PNXServerWorld.getChunkAt`: метод `Level.getChunk(x,z)` в закреплённом PNX действительно вызывает `forceLoadChunk`, даже с `create=false`. Адаптер теперь использует подтверждённый в PNX `getChunkIfLoaded` и отказывает при отсутствующем/неинициализированном chunk.

## Проверки, выполненные в этой среде

- ZIP CRC исходных архивов: PASS ранее проверен для всех архивов в `history/original-archives/`.
- Java 21 `javac --release 21 -Xlint:all -Werror` для независимого ядра и тестов: PASS.
- 18 тестовых наборов × 10 повторов в R12 = **180 успешных запусков**.
- `PnxGenerationJournalRuntime` прошёл отдельную Java 21 contract-stub компиляцию с API `Level.isChunkGenerating`.
- `PNXBlockStateDelegate`, `PNXChunkDelegate` и `PNXBlockEntityDelegate` прошли Java 21 contract compile + behavior test: property get/set/serialization, отказ при несовместимой схеме, реальные координаты чанка и отказ от незаписанных BE mutations.
- Все 23 patch-файла проходят `git apply --stat`; Stage 27–34 patch проходит синтетическое применение и byte-for-byte сравнение с overlay.
- Все 13 исходных ZIP-архивов проходят CRC/readability проверку.
- Включены тесты WAL/recovery, dependency scheduler, конфликтов, intent serialization, water policy, diagnostics, bounds guard и finalization.

См. `validation/VALIDATION.md`, `validation/BLOCK-ENTITY-UPSTREAM-API-AUDIT-2026-11-28.md` and `validation/run-pnx-be-contract-compile.sh`, `validation/run-core-tests.sh`, `validation/run-runtime-contract-compile.sh`, `validation/run-delegate-contract-compile.sh` и `validation/run-pnx-be-contract-compile.sh`.

## Что ещё НЕ завершено

1. **Базовые upstream Gradle-проверки выполнены, но сборка изменённых стадий 1–25 не выполнена.** Terra 6.6.1-BETA успешно собран на закреплённом коммите `05ddc8b...`; PNX commit `ff86b15...` (API 3.0.6 successor / Protocol Beta8) и исходный PNX-Terra 1.0.0 успешно собраны вместе на Java 21. Отдельная сборка исходного PNX `c574993...` падает из-за несовпадения camera Protocol Beta7 API, а исходный PNX-Terra с плавающим `nightly-SNAPSHOT` — из-за class-file version 69 (Java 25) при целевой Java 21.
2. **Полное применение всей цепочки патчей к чистым upstream checkout не подтверждено.** Нужно выполнить `git apply --check` и затем Gradle build уже изменённых исходников на успешной совместимой базе.
3. **Block Entity integration подключена на уровне TerraScript capture, но ещё не runtime-certified.** Active placement now returns detached staged delegates for Sign (`text0..text3`), MobSpawner (Terra's documented spawner properties), and inventory containers used by loot tables. Their updates are serialized into journal intents. PNX core patch persists spawner `Delay`; finalization applies intents on the Level scheduler, durably saves before the next spawner heartbeat, and rolls back newly registered entities if that save fails. Unknown BlockEntity types/capacities remain fail-closed. Full upstream compile and crash/restart runtime tests remain open.
4. **Создание обычных сущностей не реализовано.** Mangrove profile по-прежнему имеет entity budget и остаётся fail-closed.
5. **Physics executor не подключён.** Physics intents остаются fail-closed; `WaterLayerPolicy` существует как отдельный контракт; Stage 28 добавляет deterministic dispatcher, но фактический PNX Level physics callback ещё не подключён.
6. **Полная таблица Terra↔PNX блоков и свойств не заполнена.** Exact-match property mapping подключён к `PNXBlockStateDelegate`, но нестандартные имена и несовпадающие типы/значения требуют явных alias entries; полный реестр ещё не построен.
7. **Диагностика не охватывает все стадии.** Sink и типы событий существуют, но глобальный stage exception wrapper и маршрутизация всех отказов ещё не подключены.
8. **Каталог Origen не расширен до полного статического покрытия.** В нём три проверенных профиля; остальные скрипты используют жёсткий runtime fallback +/-32 блоков по X/Z и +/-128 по Y. Это предотвращает выход за пределы заданного бюджета, но крупные/динамические структуры, превышающие его, будут отменены. Entity-bearing mangrove и другие структуры с сущностями не принимаются.
9. **Полный интеграционный runtime test и JAR packaging не выполнялись.** Продакшен-сервер и его мир не изменялись.

Базовая совместимость PNX successor + исходного PNX-Terra подтверждена, но это не готовая генерационная система. Пакет нельзя устанавливать на production. Следующий корректный шаг — применить цепочку в чистых рабочих копиях к успешной совместимой базе, устранить конфликты, реализовать оставшиеся адаптеры и провести полный integration/recovery test.


## Stage 26 integration and path correction

Stage 26 is now part of the active patch sequence. `AnchorSession` allocates a unique per-anchor placement ordinal while holding the scheduler lock, rejects exhaustion before overflow, and exposes the value through `PlacementTicket.ordinal()`. `PNXServerWorld` writes that value into `PlacementKey` instead of reusing `0L`. The ordinal counter resets with a newly replayed anchor session, preserving deterministic identity when Terra repeats the same ordered root-placement calls. The Stage 26 regression assertion verifies consecutive tickets receive 0 and 1 and that aborting the second ticket does not block completion.

A path audit also found that the Stage 21–25 patch had package paths rooted at `org/` rather than the adapter's Maven source root. The active patch now targets `src/main/java/org/`; Stage 26 targets `src/main/java/org/` and `src/test/java/`. This corrects patch placement but does not replace the still-pending clean upstream patch-chain application and full build.

## Stages 27–36 integration overlay

The candidate includes the Stage 27–34 core overlay, Stage 35 entity adapter/rollback seam, and Stage 36 deferred PNX entity lifecycle. Stage 36 fixes an important Stage 35 flaw: before the durable chunk write an entity is absent from the ticking Level; it is staged in the chunk save set, then activated and published only after the synchronous save succeeds. UUID-based replay matching and transaction-owned rollback remain. Terra’s `ServerWorld.spawnEntity` capture path still fails closed, so entity-bearing Origen structures remain disabled. Physics callbacks are still fail-closed. See `STAGES27-34.md`, `STAGE35.md`, and `STAGE36.md`. This is not an installable release.


For the PNX revision gate, `apply-stages1-26.sh` and `apply-stages1-36.sh` accept `--pnx-ref`. The default remains the exact original 3.0.6 commit `c574993...`; the known standalone-compatible successor `ff86b15...` can be selected explicitly for an application/build experiment. The full Stage 1–36 patch chain has not yet been verified on that successor.
