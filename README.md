# CustomDiscs Forge (Minecraft 1.7.10)

**Author / Автор: TcKatFire**

CustomDiscs is a native Forge mod for Minecraft 1.7.10. It adds custom audio to music discs, player heads, and a custom horn item. The server controls playback metadata and hearing range; clients decode, spatially attenuate, and mix audio locally, with independent client-side volume settings.

CustomDiscs targets Forge `10.13.4.1614` and GT New Horizons-compatible environments. Compatibility with a specific GTNH fork has not yet been verified. Java 8 is required.

CustomDiscs — нативный Forge-мод для Minecraft 1.7.10. Он добавляет собственное аудио для музыкальных пластинок, голов игроков и отдельного предмета-рога. Сервер управляет метаданными воспроизведения и радиусом слышимости; клиенты декодируют и микшируют звук локально с позиционным затуханием и отдельной настройкой громкости для каждой категории.

Мод рассчитан на Forge `10.13.4.1614` и совместимые с GT New Horizons сборки. Совместимость с конкретной версией GTNH пока не проверялась. Для сборки и запуска требуется Java 8.

## Features / Возможности

- Custom sounds for supported music discs and player heads, plus the `customdiscs:custom_horn` item.
- WAV, MP3, and FLAC input files.
- Positional playback with server-controlled range (default **63 blocks**) and local per-category volume.
- Client-side hash-verified cache; the server sends audio in bounded chunks only when a client needs the file.
- `/customdiscs` commands, model selection, and localized messages.
- No Simple Voice Chat, microphone, or voice-chat server is used.

- Свои звуки для поддерживаемых пластинок и голов игроков, а также предмет `customdiscs:custom_horn`.
- Аудиофайлы WAV, MP3 и FLAC.
- Позиционное воспроизведение с серверным радиусом (по умолчанию **63 блока**) и локальной громкостью каждой категории.
- Локальный кэш с проверкой SHA-256; сервер передаёт аудио ограниченными пакетами только при отсутствии нужного файла у клиента.
- Команды `/customdiscs`, выбор моделей и локализованные сообщения.
- Simple Voice Chat, микрофон и сервер голосового чата не используются.

## Installation / Установка

1. Download `customdiscs-0.3.1.jar` from the [Releases](https://github.com/TcKatFire/CustomDiscs-Forge-1.7.10/releases) page.
2. Install the **same JAR** in the `mods` folder on the dedicated server and on every client.
3. Start the server once, then put authorized WAV, MP3, or FLAC files in `config/customdiscs/musicdata/` on the **server**.
4. Use `/customdiscs create <filename>` while holding a supported disc, head, or custom horn.

All instances must use the same CustomDiscs version. Forge rejects a client that reports a different mod version during login. Do not install this Forge JAR as a Paper plugin.

1. Скачайте `customdiscs-0.3.1.jar` со страницы [Releases](https://github.com/TcKatFire/CustomDiscs-Forge-1.7.10/releases).
2. Установите **один и тот же JAR** в папку `mods` на выделенном сервере и на каждом клиенте.
3. Один раз запустите сервер, затем поместите разрешённые к использованию файлы WAV, MP3 или FLAC в `config/customdiscs/musicdata/` **на сервере**.
4. Возьмите поддерживаемую пластинку, голову или custom horn и выполните `/customdiscs create <filename>`.

На сервере и клиентах должна быть одна версия CustomDiscs. Forge отклоняет подключение клиента с другой версией мода. Этот Forge JAR не является Paper-плагином.

## Commands / Команды

Server root: `/customdiscs` (no `/cd` or `/customdisc` aliases). Players do not need operator status. Available subcommands include:

| Command | Description |
| --- | --- |
| `/customdiscs create <filename> [title]` | Apply a server musicdata file to the supported item in hand. |
| `/customdiscs download <url> <filename>` | Download a permitted direct audio file using the server connection. |
| `/customdiscs range <blocks>` | Set the held custom item's range within the server-configured maximum. |
| `/customdiscs goatcooldown <ticks>` | Set the held custom horn's cooldown. |
| `/customdiscs revert` | Remove CustomDiscs data from the held item. |
| `/customdiscs setmodel [name-or-id]` | Open the model selector or apply a configured model. |
| `/customdiscs revertmodel` | Remove the held item's custom model ID. |
| `/customdiscs reload` | Reload server configuration and language files. |
| `/customdiscs volume <disc\|head\|horn> <0-100>` | Set local client volume for one category. |

Only `volume` is handled locally by the client; other subcommands are sent to the server. Downloads use server bandwidth and disk space and obey configured size and path-safety limits. Do not use the download command to obtain media without authorization.

Корневая серверная команда: `/customdiscs` (алиасов `/cd` и `/customdisc` нет). Команды доступны игрокам без статуса оператора. Основные подкоманды:

| Команда | Описание |
| --- | --- |
| `/customdiscs create <filename> [title]` | Привязать файл из server musicdata к поддерживаемому предмету в руке. |
| `/customdiscs download <url> <filename>` | Скачать разрешённый прямой аудиофайл через подключение сервера. |
| `/customdiscs range <blocks>` | Установить радиус для предмета в руке в пределах серверного максимума. |
| `/customdiscs goatcooldown <ticks>` | Установить задержку использования custom horn. |
| `/customdiscs revert` | Удалить данные CustomDiscs с предмета в руке. |
| `/customdiscs setmodel [name-or-id]` | Открыть выбор модели или применить настроенную модель. |
| `/customdiscs revertmodel` | Удалить пользовательский ID модели предмета. |
| `/customdiscs reload` | Перезагрузить конфигурацию и языковые файлы сервера. |
| `/customdiscs volume <disc\|head\|horn> <0-100>` | Настроить локальную громкость категории на клиенте. |

Клиент локально обрабатывает только `volume`; остальные подкоманды отправляются серверу. Загрузка расходует трафик и место на сервере, ограничена настройками размера и безопасности путей. Не загружайте материалы, на использование которых у вас нет прав.

## Configuration / Настройки

- Server config: `config/customdiscs.cfg`.
- Server audio: `config/customdiscs/musicdata/`.
- Model definitions: `config/customdiscs/models.yml`.
- Server language files: `config/customdiscs/langs/`.
- Client-local volume config: `config/customdiscs-client.cfg`.
- Client audio cache: `<Minecraft game directory>/customdiscs/cache/`.
- Volume GUI: **Options → Controls → CustomDiscs Volume Settings**, default key **O**.

Disc, head, and horn playback use a server-controlled default range of 63 blocks. Per-item range overrides remain subject to the configured server maximum. On upgrade, legacy range values exactly equal to 16 migrate to 63; other values are preserved. Client category volume defaults to 100% and never changes server range.

The first observation of a jukebox after server/world/chunk load establishes its state without starting an already-inserted custom disc. A later empty-to-custom insertion starts playback. Jukebox reconciliation runs every 10 ticks, so hopper-driven changes may take up to 10 ticks.

Minecraft 1.7.10 has no goat-horn item or modern Custom Model Data. This mod supplies its own horn item and represents custom models with legacy numeric IDs and resource-pack textures.

- Серверная конфигурация: `config/customdiscs.cfg`.
- Аудиофайлы сервера: `config/customdiscs/musicdata/`.
- Описания моделей: `config/customdiscs/models.yml`.
- Языковые файлы сервера: `config/customdiscs/langs/`.
- Локальная громкость клиента: `config/customdiscs-client.cfg`.
- Кэш аудио клиента: `<папка Minecraft>/customdiscs/cache/`.
- GUI громкости: **Options → Controls → CustomDiscs Volume Settings**, клавиша по умолчанию **O**.

Начальный радиус для пластинок, голов и horn — 63 блока; сервер ограничивает индивидуальные значения настроенным максимумом. При обновлении старые значения радиуса, равные ровно 16, заменяются на 63; остальные сохраняются. Локальная громкость каждой категории по умолчанию 100% и не меняет серверный радиус.

Первое обнаружение jukebox после загрузки сервера/мира/чанка только фиксирует состояние: уже вставленная пластинка автоматически не запускается. Последующая смена пустого jukebox на custom disc запускает звук. Проверка выполняется раз в 10 тиков; изменения через воронку могут примениться с задержкой до 10 тиков.

В Minecraft 1.7.10 нет предмета goat horn и современной системы Custom Model Data. Мод добавляет собственный horn и использует старые числовые ID моделей и текстуры resource pack.

## Build and checks / Сборка и проверки

Use a Java 8 JDK:

```powershell
.\gradlew.bat check build --no-daemon
```

The JAR is written to `build/libs/`. The smoke checks cover audio decoding, packet serialization/discriminators, playback lifecycle, jukebox state transitions, command behavior, and volume/range separation. They do not replace a live client/server test.

Используйте JDK Java 8:

```powershell
.\gradlew.bat check build --no-daemon
```

JAR создаётся в `build/libs/`. Smoke-тесты проверяют декодирование аудио, сериализацию и ID сетевых пакетов, жизненный цикл воспроизведения, состояния jukebox, команды и независимость громкости от радиуса. Они не заменяют проверку на реальном сервере и клиенте.

## Troubleshooting / Диагностика

- Confirm server and clients run the same mod version; compare `network_check` lines in their `latest.log`.
- Compare the server's `playback_encode` / `audio_chunk_encode` lines with any client malformed-packet warning.
- Audio source files are read from server `config/customdiscs/musicdata/`; remote clients receive bytes and cache them locally, not by opening a server path.
- Already-active playback is not synchronized to a player who logs in or reconnects.

- Проверьте, что на сервере и клиентах одна версия мода; сравните строки `network_check` в `latest.log`.
- Сопоставьте строки сервера `playback_encode` / `audio_chunk_encode` с предупреждением клиента о некорректном пакете.
- Сервер читает исходные файлы из `config/customdiscs/musicdata/`; клиенты получают байты и сохраняют кэш локально, а не открывают путь на сервере.
- Уже идущее воспроизведение не синхронизируется для игрока, который подключился или переподключился.

## License / Лицензия

See [LICENSE](LICENSE). The existing MIT copyright and license notice is retained.

См. [LICENSE](LICENSE). Исходное уведомление об авторских правах и лицензия MIT сохранены.
