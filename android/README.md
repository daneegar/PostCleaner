# PostCleaner for Android

Нативное приложение на Kotlin + Jetpack Compose для ручного разбора своих постов X: дата и публичные метрики, свайпы «оставить / в очередь», отмена свайпа, проверка очереди и подтверждение удаления.

Монорепозиторий с Android и местом под будущий iOS: [daneegar/PostCleaner](https://github.com/daneegar/PostCleaner).
Отдельный Android-репозиторий: [daneegar/PostCleaner-Android](https://github.com/daneegar/PostCleaner-Android).

## Сборка

Откройте эту папку в Android Studio. Нужны Android SDK 36 и совместимый Gradle JDK, рекомендуется 21. Конфигурация Gradle daemon указывает JetBrains JDK 21. Java 25 с этой версией Kotlin/Gradle использовать не следует.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Подключение X

1. Создайте приложение в [X Developer Console](https://developer.x.com/).
2. Включите OAuth 2.0, тип **Native App**, разрешите чтение и запись.
3. Зарегистрируйте Callback URI: `app.postcleaner://oauth/callback`.
4. Website URL: `https://github.com/daneegar/PostCleaner`.
5. Скопируйте OAuth 2.0 Client ID из Keys and tokens и введите в приложении. Client Secret и app-only Bearer Token не нужны.

Приложение запрашивает `tweet.read tweet.write users.read`, использует PKCE S256 и проверяет state. Необходим доступ X к endpoints чтения и удаления; условия оплаты определяются X.

## Ограничения

Access token, очередь и прогресс хранятся в памяти сессии. После завершения процесса нужен повторный вход. Медиа пока не отображаются. Репосты исключены, ответы включены, посты загружаются страницами по 50 от новых к старым. Полнота истории зависит от X API.

Удаление необратимо и выполняется последовательно после отдельного подтверждения. При ошибке оставшаяся очередь сохраняется в текущей сессии. При потере ответа сервер мог уже удалить пост — проверьте его в X перед повтором.

Сборка и 4 unit-теста OAuth прошли при создании проекта. Реальный вход и удаление в X требуют проверки на аккаунте владельца. Первая версия не подготовлена к публичному релизу в магазине приложений.

## Исходники двух репозиториев

Основная разработка ведётся в папке `android/` монорепозитория. Отдельный Android-репозиторий публикуется через `git subtree split --prefix=android`; автоматическая синхронизация пока не настроена.
