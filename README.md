# VProg Tracker

[Читать на русском](#vprog-tracker--русский) | [Read in English](#vprog-tracker--english)

---

# Русский

Tracker - Android-приложение для сбора и передачи геолокационных данных в связке с информацией о сотовых сетях. Поддерживает фоновую работу, буферизацию данных и отправку через ZeroMQ.

## Содержание

- [Возможности](#возможности)
- [Архитектура](#архитектура)
- [Модули](#модули)
- [Многопоточность](#многопоточность)
- [Протокол передачи и формат данных](#протокол-randomwalk-и-формат-данных)
- [Сетевое взаимодействие (ZeroMQ)](#сетевое-взаимодействие-zeromq)
- [Требования и разрешения](#требования-и-разрешения)
- [Структура проекта](#структура-проекта)

---

## Возможности

| Модуль | Описание |
|---|---|
| Геолокация | Фоновый сбор координат через Fused Location Provider |
| Сотовые сети | Чтение параметров LTE / GSM / NR (5G) |
| ZeroMQ | Отправка данных на сервер через REQ/REP сокет |
| Буферизация | Сохранение неотправленных данных на диск |
| Медиаплеер | Воспроизведение аудио с плейлистами |
| Калькулятор | Вычисление математических выражений |

---

## Архитектура

```
HubActivity (главный экран)
├── LocationActivity  ->  LocationForeground (Foreground Service)
│                             ├── FusedLocationProviderClient
│                             ├── TelephonyManager
│                             └── ZmqHandler
├── CellActivity      ->  TelephonyManager (LTE / GSM / NR)
├── MediaActivity     ->  MediaPlayer + RecyclerView
└── CalcActivity      ->  Expression Parser
```

---

## Модули

### LocationActivity + LocationForeground

Двухуровневая система геолокации.

`LocationActivity` - UI-экран с отображением координат в реальном времени через `BroadcastReceiver`. Позволяет настроить интервал обновления (1–5 секунд) через `Spinner` + `SharedPreferences`.

`LocationForeground` - `Service` с типом `FOREGROUND_SERVICE_TYPE_LOCATION`, работающий даже при свёрнутом приложении. Собирает GPS, данные сот и отправляет пакеты на сервер по ZMQ.

### CellActivity

Считывает параметры всех видимых сот через `TelephonyManager.allCellInfo`:

- LTE: Band, CI, EARFCN, MCC, MNC, PCI, TAC, RSRP, RSRQ, RSSI, RSSNR, CQI, Timing Advance
- GSM: CID, BSIC, ARFCN, LAC, MCC, MNC, RSSI, Timing Advance
- NR (5G): Band, NCI, PCI, NRARFCN, TAC, MCC, MNC, SS-RSRP, SS-RSRQ, SS-SINR

### ZmqSockets

Вспомогательный класс для одиночных ZeroMQ-запросов с настраиваемыми таймаутами, обёрнутый в `suspend`-функцию.

### MediaActivity

Полнофункциональный аудиоплеер. Поддерживает открытие одного трека или целой папки, отображает плейлист в `RecyclerView` с подсветкой текущего трека, считывает метаданные через `MediaMetadataRetriever` (обложка, исполнитель, длительность). Управление: play/pause, prev/next, loop, seekbar.

### CalcActivity

Парсер математических выражений без внешних зависимостей. Реализует двухпроходное вычисление с учётом приоритета операций: первый проход - умножение и деление (`*`, `/`), второй - сложение и вычитание (`+`, `-`).

---

## Многопоточность

В проекте применяется многоуровневая модель конкурентности на основе Kotlin Coroutines.

```
Main Thread (UI)
|
+-- CoroutineScope(Dispatchers.Main + SupervisorJob)
|       |
|       +-- launch(zmqDispatcher)   // однопоточный Executor для ZMQ
|               +-- sendData()          // отправка пакета
|               +-- flushBufferToDisk() // сброс буфера на диск
|               +-- trySendBackup()     // повторная отправка из файла
|
+-- LocationCallback (Looper.getMainLooper())
        +-- processLocation()  ->  запускает корутину в zmqDispatcher
```

| Компонент | Dispatcher | Причина |
|---|---|---|
| `ZmqHandler.sendData()` | `zmqDispatcher` (single-thread Executor) | ZeroMQ-сокеты не потокобезопасны; единственный поток исключает гонки |
| `LocationCallback` | `Looper.getMainLooper()` | Требование FusedLocationProvider |
| `flushBufferToDisk()` | `zmqDispatcher` | Запись файла изолирована от UI-потока |
| `trySendBackup()` | `zmqDispatcher` | Синхронный цикл отправки на отдельном потоке |

`SupervisorJob` гарантирует, что сбой одной дочерней корутины не отменяет остальные - критично для долгоживущего сервиса.

---

## Протокол передачи и формат данных

Сервер реализует динамическое управление сбором данных. Каждый ответ сервера представляет собой 4-битную строку флагов (`activeFlags`), которая определяет, что именно собирать на следующем шаге.

### Формат запроса

Каждый пакет - строка с полями, разделёнными точкой с запятой:

```
<locPart>;<cellPart>
```

`locPart` (если `activeFlags[0] == '1'`):
```
lat;lon;alt;timestamp_sec;accuracy
```

`cellPart` для LTE:
```
LTE;band;ci;earfcn;mcc;mnc;pci;tac;asuLevel;0;dbm;0;dbm;0;0
```

`cellPart` для NR (5G):
```
NR;band;nci;pci;nrarfcn;tac;mcc;mnc;dbm;0;0;0
```

Пример полного пакета:
```
55.7558;37.6176;156.3;1718000000;4.2;LTE;3;12345678;1850;250;01;123;456;1;20;0;-85;0;-85;0;0
```

### Формат ответа (activeFlags)

Сервер отвечает строкой из 4 символов `'0'` / `'1'`:

```
activeFlags = "1011"
               |||+-- бит 0: GPS координаты
               ||+--- бит 1: LTE данные
               |+---- бит 2: (зарезервировано)
               +----- бит 3: NR (5G) данные
```

Примеры:

```
"1111"  ->  собирать всё
"1010"  ->  только GPS + NR, LTE пропустить
"0000"  ->  заглушить весь сбор
```

Если ответ не является строкой из 4 бит или содержит `"ERROR"`, флаги не обновляются - приложение продолжает работать с последними известными настройками:

```kotlin
if (resp.length >= 4 && resp.all { it == '0' || it == '1' }) activeFlags = resp
```

### Буферизация и надёжная доставка

```
processLocation()
      |
      v
dataBuffer.add(data)           // добавить в RAM-буфер
      |
      v
zmqHandler.sendData(data)
      |
   SUCCESS  ->  dataBuffer.remove(data)
      |
   ERROR
      |
      v
dataBuffer.size >= BUFFER_SIZE?
      |
      v
flushBufferToDisk()            // сброс в Documents/location.txt
      |
      v
trySendBackup()                // переименовать, отправить построчно, вернуть несохранённое
```

Такой подход обеспечивает at-least-once delivery: данные не теряются даже при временной потере сети.

---

## Сетевое взаимодействие (ZeroMQ)

Используется паттерн REQ/REP (Request–Reply) из библиотеки `jeromq`.

```
Android (REQ) ---- TCP ----> Сервер :20077 (REP)
     |                              |
     |   "lat;lon;...;LTE;..."      |
     | ---------------------------> |
     |                              |  обработка + RandomWalk
     |          "1011"              |
     | <--------------------------- |
```

### Параметры сокета

| Параметр | Значение | Назначение |
|---|---|---|
| `receiveTimeOut` | 3000 мс | Не блокировать поток бесконечно |
| `sendTimeOut` | 3000 мс | Таймаут на отправку |
| `linger` | 0 | Немедленное закрытие без ожидания |

### Стратегия переподключения

При любой ошибке отправки `ZmqHandler` пересоздаёт сокет:

```kotlin
private fun reconnect() {
    context.destroySocket(socket)
    socket = context.createSocket(ZMQ.REQ)
    connect()
}
```

Это решает проблему застрявшего REQ-сокета в ZeroMQ: после таймаута сокет входит в некорректное состояние, и единственный способ восстановления - пересоздание.

---

## Требования и разрешения

```
minSdk: 26 (Android 8.0)
targetSdk: 34 (Android 14)
```

| Разрешение | Зачем |
|---|---|
| `ACCESS_FINE_LOCATION` | Точные GPS-координаты |
| `ACCESS_COARSE_LOCATION` | Данные сотовых вышек |
| `FOREGROUND_SERVICE` | Фоновый сервис геолокации |
| `FOREGROUND_SERVICE_LOCATION` | API 34+ требует явного типа |
| `READ_PHONE_STATE` | `TelephonyManager.allCellInfo` |
| `POST_NOTIFICATIONS` | Android 13+ уведомление сервиса |
| `READ_MEDIA_AUDIO` | Доступ к аудиофайлам |
| `MANAGE_EXTERNAL_STORAGE` | Запись `location.txt` в Documents |

---

## Структура проекта

```
app/src/main/java/com/UwU/students/
├── HubActivity.kt          // главное меню навигации
├── LocationActivity.kt     // UI геолокации + BroadcastReceiver
├── LocationForeground.kt   // Foreground Service + ZmqHandler
├── CellActivity.kt         // отображение параметров сот
├── MediaActivity.kt        // аудиоплеер с плейлистами
├── CalcActivity.kt         // калькулятор выражений
└── data/
    └── ZmqSockets.kt       // suspend-обёртка для ZeroMQ
```

---

<br><br>

---

# English

Tracker is an Android application for collecting and transmitting geolocation data combined with cellular network parameters. It supports background operation, data buffering, and transmission over ZeroMQ.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Modules](#modules)
- [Concurrency Model](#concurrency-model)
- [Transmission protocol and Data Format](#randomwalk-protocol-and-data-format)
- [Network Layer (ZeroMQ)](#network-layer-zeromq)
- [Requirements and Permissions](#requirements-and-permissions)
- [Project Structure](#project-structure)

---

## Features

| Module | Description |
|---|---|
| Geolocation | Background coordinate collection via Fused Location Provider |
| Cellular | Read LTE / GSM / NR (5G) cell parameters |
| ZeroMQ | Send data to server over REQ/REP socket |
| Buffering | Persist unsent data to disk for reliable delivery |
| Media Player | Audio playback with playlist support |
| Calculator | Evaluate mathematical expressions |

---

## Architecture

```
HubActivity (main screen)
├── LocationActivity  ->  LocationForeground (Foreground Service)
│                             ├── FusedLocationProviderClient
│                             ├── TelephonyManager
│                             └── ZmqHandler
├── CellActivity      ->  TelephonyManager (LTE / GSM / NR)
├── MediaActivity     ->  MediaPlayer + RecyclerView
└── CalcActivity      ->  Expression Parser
```

---

## Modules

### LocationActivity + LocationForeground

A two-tier location system.

`LocationActivity` is the UI screen that displays real-time coordinates via a `BroadcastReceiver`. It allows configuring the update interval (1–5 seconds) through a `Spinner` backed by `SharedPreferences`.

`LocationForeground` is a `Service` with `FOREGROUND_SERVICE_TYPE_LOCATION` that runs even when the app is minimized. It collects GPS data and cell info, then sends packets to the server.

### CellActivity

Reads parameters of all visible cells via `TelephonyManager.allCellInfo`:

- LTE: Band, CI, EARFCN, MCC, MNC, PCI, TAC, RSRP, RSRQ, RSSI, RSSNR, CQI, Timing Advance
- GSM: CID, BSIC, ARFCN, LAC, MCC, MNC, RSSI, Timing Advance
- NR (5G): Band, NCI, PCI, NRARFCN, TAC, MCC, MNC, SS-RSRP, SS-RSRQ, SS-SINR

### ZmqSockets

A helper class for standalone ZeroMQ requests with configurable timeouts, exposed as a `suspend` function.

### MediaActivity

A fully-featured audio player. Supports opening a single track or an entire folder, displays a `RecyclerView` playlist with current-track highlight, and reads metadata via `MediaMetadataRetriever` (cover art, artist, duration). Controls: play/pause, prev/next, loop, seekbar.

### CalcActivity

A mathematical expression parser with no external dependencies. Implements two-pass evaluation respecting operator precedence: first pass handles multiplication and division (`*`, `/`), second pass handles addition and subtraction (`+`, `-`).

---

## Concurrency Model

The project uses a multi-level concurrency model built on Kotlin Coroutines.

```
Main Thread (UI)
|
+-- CoroutineScope(Dispatchers.Main + SupervisorJob)
|       |
|       +-- launch(zmqDispatcher)   // single-thread Executor for ZMQ
|               +-- sendData()          // send packet
|               +-- flushBufferToDisk() // flush buffer to disk
|               +-- trySendBackup()     // retry unsent from file
|
+-- LocationCallback (Looper.getMainLooper())
        +-- processLocation()  ->  launches coroutine on zmqDispatcher
```

| Component | Dispatcher | Reason |
|---|---|---|
| `ZmqHandler.sendData()` | `zmqDispatcher` (single-thread Executor) | ZeroMQ sockets are not thread-safe; a single thread eliminates data races |
| `LocationCallback` | `Looper.getMainLooper()` | Required by FusedLocationProvider |
| `flushBufferToDisk()` | `zmqDispatcher` | File I/O isolated from the UI thread |
| `trySendBackup()` | `zmqDispatcher` | Synchronous retry loop on a dedicated thread |

`SupervisorJob` ensures that a failure in one child coroutine does not cancel the others, which is critical for a long-lived service.

---

## Transmission protocol and Data Format

The server implements a dynamic transmission control over what the client collects. Each server response is a 4-bit flag string (`activeFlags`) that dictates what to collect on the next step.

### Request Format

Each packet is a semicolon-delimited string:

```
<locPart>;<cellPart>
```

`locPart` (when `activeFlags[0] == '1'`):
```
lat;lon;alt;timestamp_sec;accuracy
```

`cellPart` for LTE:
```
LTE;band;ci;earfcn;mcc;mnc;pci;tac;asuLevel;0;dbm;0;dbm;0;0
```

`cellPart` for NR (5G):
```
NR;band;nci;pci;nrarfcn;tac;mcc;mnc;dbm;0;0;0
```

Full packet example:
```
55.7558;37.6176;156.3;1718000000;4.2;LTE;3;12345678;1850;250;01;123;456;1;20;0;-85;0;-85;0;0
```

### Response Format (activeFlags)

The server replies with a 4-character string of `'0'` / `'1'`:

```
activeFlags = "1011"
               |||+-- bit 0: GPS coordinates
               ||+--- bit 1: LTE data
               |+---- bit 2: (reserved)
               +----- bit 3: NR (5G) data
```

Examples:

```
"1111"  ->  collect everything
"1010"  ->  GPS + NR only, skip LTE
"0000"  ->  suppress all collection
```

If the response is not a 4-bit string or contains `"ERROR"`, flags are not updated and the app continues with the last known settings:

```kotlin
if (resp.length >= 4 && resp.all { it == '0' || it == '1' }) activeFlags = resp
```

### Buffering and Reliable Delivery

```
processLocation()
      |
      v
dataBuffer.add(data)           // add to RAM buffer
      |
      v
zmqHandler.sendData(data)
      |
   SUCCESS  ->  dataBuffer.remove(data)
      |
   ERROR
      |
      v
dataBuffer.size >= BUFFER_SIZE?
      |
      v
flushBufferToDisk()            // write to Documents/location.txt
      |
      v
trySendBackup()                // rename, send line by line, requeue unsent
```

This approach provides at-least-once delivery: no data is lost even during temporary network outages.

---

## Network Layer (ZeroMQ)

Uses the REQ/REP (Request–Reply) pattern from the `jeromq` library.

```
Android (REQ) ---- TCP ----> Server :20077 (REP)
     |                              |
     |   "lat;lon;...;LTE;..."      |
     | ---------------------------> |
     |                              |  processing + RandomWalk
     |          "1011"              |
     | <--------------------------- |
```

### Socket Parameters

| Parameter | Value | Purpose |
|---|---|---|
| `receiveTimeOut` | 3000 ms | Prevent the thread from blocking indefinitely |
| `sendTimeOut` | 3000 ms | Timeout for the send operation |
| `linger` | 0 | Immediate close with no pending drain |

### Reconnection Strategy

On any send error, `ZmqHandler` recreates the socket:

```kotlin
private fun reconnect() {
    context.destroySocket(socket)
    socket = context.createSocket(ZMQ.REQ)
    connect()
}
```

This addresses the stuck REQ socket issue in ZeroMQ: after a timeout the socket enters an invalid state, and the only recovery is recreation.

---

## Requirements and Permissions

```
minSdk: 26 (Android 8.0)
targetSdk: 34 (Android 14)
```

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Precise GPS coordinates |
| `ACCESS_COARSE_LOCATION` | Cell tower data |
| `FOREGROUND_SERVICE` | Background location service |
| `FOREGROUND_SERVICE_LOCATION` | API 34+ requires explicit type |
| `READ_PHONE_STATE` | `TelephonyManager.allCellInfo` |
| `POST_NOTIFICATIONS` | Android 13+ service notification |
| `READ_MEDIA_AUDIO` | Access to audio files |
| `MANAGE_EXTERNAL_STORAGE` | Write `location.txt` to Documents |

---

## Project Structure

```
app/src/main/java/com/UwU/students/
├── HubActivity.kt          // main navigation menu
├── LocationActivity.kt     // location UI + BroadcastReceiver
├── LocationForeground.kt   // Foreground Service + ZmqHandler
├── CellActivity.kt         // cell network parameter display
├── MediaActivity.kt        // audio player with playlists
├── CalcActivity.kt         // expression calculator
└── data/
    └── ZmqSockets.kt       // suspend wrapper for ZeroMQ
```
