# PostCleaner

Нативное Android-приложение для ручного разбора своих постов X. iOS зарезервирован отдельно.

## Запуск

1. Откройте `android/` в Android Studio (JDK 17–23, рекомендуется 17 или 21; Android SDK 36).
2. Запустите `./gradlew :app:assembleDebug` или Run в Android Studio.
3. В X Developer Console включите OAuth 2.0, тип **Native App / public client**, права чтения и записи. Зарегистрируйте callback **app.postcleaner://oauth/callback** точно в таком виде.
4. Введите OAuth 2.0 **Client ID** в приложении и нажмите «Подключить X». Client Secret и app-only Bearer Token не нужны.
5. Разбирайте реальные посты: влево — очередь удаления, вправо — оставить. Откройте очередь, проверьте выбор и отдельно подтвердите удаление.

## Поведение и границы первой версии

- OAuth Authorization Code + PKCE S256, случайный state и проверка callback. Pending OAuth state/verifier сохраняются в закрытом хранилище приложения на время входа, истекают через 10 минут и удаляются после callback.
- Access token только в памяти, без логирования; нет refresh token и фонового удаления. При истечении сессии нужен повторный вход. Очередь и история переживают поворот экрана, но не завершение процесса. Резервное копирование отключено.
- GET users/me → GET users/:id/tweets с датой, текстом и public_metrics, пагинация по 50. Репосты исключены; ответы входят. История показывается от новых к старым. Медиа пока не отображаются.
- Загрузка следующей страницы запускается пользователем. Доступная глубина истории и стоимость определяются X; полная история аккаунта не гарантируется. Демо-контент не используется.
- DELETE tweets/:id выполняется последовательно только после подтверждения. Успешные удаления убираются из очереди сразу; при первой ошибке операция останавливается, оставшиеся доступны для повтора. При потере ответа сервер мог уже удалить пост — сверяйте такой пост в X перед повтором.
- Обработка HTTP 401, 402, 403, 429; без автоматических повторов платных запросов.
- Приложению нужен разрешённый доступ X API. Наличие developer account само по себе не гарантирует доступ к endpoints или бесплатное использование.
- Не квалифицировано для публичного релиза: нужны проверка входа на реальном аккаунте, скринридер/разные размеры экранов, privacy policy и production OAuth redirect (предпочтительно verified HTTPS App Link).

## Источники

- https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code
- https://docs.x.com/x-api/users/get-posts
- https://github.com/xdevplatform/docs/blob/main/x-api/posts/manage-tweets/introduction.mdx

## Пример локальной сборки

Java 25 несовместима с используемым Kotlin/Gradle. Выберите совместимую Java в настройках Gradle JDK; текущая конфигурация daemon указывает JetBrains JDK 21.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Проверено при создании

`assembleDebug` и `testDebugUnitTest` прошли: 4 теста PKCE/state/срока callback, 0 ошибок. APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Запуск интерфейса не подтверждён: локальный эмулятор API 35 не завершил загрузку, установка не завершилась. Вход и удаление в настоящем X не проверены: требуется настройка Client ID и авторизация владельца аккаунта.

## Репозитории

- Монорепозиторий: [daneegar/PostCleaner](https://github.com/daneegar/PostCleaner).
- Android отдельно: [daneegar/PostCleaner-Android](https://github.com/daneegar/PostCleaner-Android).
- `android/` — обычная папка с исходниками; `ios/` — место под будущую реализацию.

Для Website URL в настройках X используйте `https://github.com/daneegar/PostCleaner`.

Android-репозиторий экспортируется из монорепозитория через `git subtree split --prefix=android`. Автоматической синхронизации пока нет; основные изменения вносите в монорепозиторий.
