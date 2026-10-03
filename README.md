# Screen Translator 0.6

Проект подготовлен для обычной Gradle-сборки: все Android/ML Kit/OkHttp-зависимости указаны как Maven dependencies, поэтому Gradle сам скачивает их при первой сборке.

## Сборка

### Android Studio
Открой корневую папку проекта (`ScreenTranslator`). Android Studio предложит синхронизацию Gradle и скачает зависимости из Google Maven, Maven Central и Gradle Plugin Portal.

### Командная строка
Если в системе установлен Gradle 8.9+:

```bash
gradle assembleDebug
```

Для AGP 8.7.3 рекомендуется Gradle 8.9 и JDK 17+.

> В архиве оставлен `gradlew`/`gradlew.bat` и `gradle/wrapper/gradle-wrapper.properties` с Gradle 8.9. В этой среде не удалось получить бинарный `gradle-wrapper.jar`, поэтому для полностью автономного `./gradlew` его нужно один раз сгенерировать командой `gradle wrapper --gradle-version 8.9` или дать Android Studio создать Wrapper.

## Что исправлено относительно v0.5
- Добавлена настройка URL LibreTranslate-совместимого API прямо в приложении.
- Значение API сохраняется между запусками.
- Добавлено разрешение POST_NOTIFICATIONS для Android 13+.
- Все внешние библиотеки подключаются через Maven, без локальных JAR/AAR.
- Wrapper настроен на Gradle 8.9.

## Запуск
1. Разреши «поверх других приложений».
2. Укажи URL LibreTranslate-совместимого сервера.
3. Нажми «Запустить переводчик» и разреши захват экрана.
4. Нажми «ВЫБРАТЬ».
5. Выдели область с английским текстом.
6. Нажми «ПЕРЕВЕСТИ».

Публичный LibreTranslate-сервер может требовать API-ключ или ограничивать запросы. Сам OCR работает локально через Google ML Kit.
