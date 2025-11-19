# How Orka TeamCity Plugin Works - Internal Mechanics

## VM Startup Triggers

### When does TeamCity start a new VM?

TeamCity **самостоятельно** принимает решение о запуске VM через Cloud Plugin API. Плагин **не контролирует** это решение, а только реагирует на запросы от TeamCity.

### Основные триггеры запуска VM

#### 1. **Build в очереди (основной триггер)**

```
Билд добавлен в очередь
    ↓
TeamCity ищет доступный агент в нужном пуле
    ↓
Нет свободных агентов?
    ↓
TeamCity вызывает canStartNewInstance()
    ↓
Если true → вызывает startNewInstance()
    ↓
VM создаётся
```

**Условия:**

- Есть билд в очереди
- Билд требует агента из определённого пула
- В этом пуле нет свободных агентов
- Лимит инстансов не достигнут (`instanceLimit`)

#### 2. **Тестирование профиля после создания**

При создании/сохранении Cloud Profile, TeamCity может:

- Проверить connectivity к Orka endpoint
- **Попытаться создать тестовую VM** для валидации конфигурации
- Проверить доступность API

**Это объясняет, почему VM может стартовать сразу после создания профиля без нагрузки!**

#### 3. **Агент переподключается (reconnect)**

Если агент с Orka VM уже существует (например, VM создана вручную в Orka) и подключается к TeamCity, плагин может зарегистрировать его через метод `findInstanceByAgent()`.

#### 4. **Периодические проверки TeamCity**

TeamCity может периодически вызывать методы плагина для:

- Обновления статуса инстансов
- Проверки доступности cloud provider
- Сбора метрик

### Кодовый путь создания VM

```java
// 1. TeamCity вызывает
OrkaCloudClient.canStartNewInstance(image)
    ↓
// 2. Проверяется лимит
OrkaCloudImage.canStartNewInstance()
    return instanceLimit > currentInstances.size()
    ↓
// 3. Если true, TeamCity вызывает
OrkaCloudClient.startNewInstance(image, userData)
    ↓
// 4. Создаётся instance object
OrkaCloudInstance instance = cloudImage.startNewInstance(instanceId)
    ↓
// 5. Асинхронно запускается setup
scheduledExecutorService.submit(() -> setUpVM(...))
    ↓
// 6. Deployment в Orka
orkaClient.deployVM(vmName, namespace, metadata)
    ↓
// 7. Проверка и ожидание VM
orkaClient.getVM(instanceId, namespace)
sshUtil.waitForSSH(host, port)
    ↓
// 8. Конфигурация агента
updateBuildAgentPropertiesOnce(...)
remoteAgent.startAgent(...)
    ↓
// 9. Instance статус → RUNNING
```

## Почему VM запускается без нагрузки?

### Возможные причины

1. **TeamCity проверяет профиль после сохранения**
   - Это нормальное поведение TeamCity
   - VM создаётся для валидации конфигурации
   - После проверки может быть удалена или оставлена idle

2. **Настройки "Test connection" включены**
   - При сохранении профиля TeamCity может делать тестовый запуск
   - Проверяется весь путь: Orka API → VM deploy → SSH → Agent start

3. **Agent Pool требует minimum agents**
   - В некоторых версиях TeamCity можно настроить minimum количество агентов в пуле
   - TeamCity будет поддерживать это минимальное количество

4. **Existing VM в Orka**
   - Если в Orka уже есть VM с тем же именем
   - Плагин может "подобрать" её через `findInstanceByAgent()`

## Как контролировать запуск VM?

### 1. **Instance Limit (основной контроль)**

```
Maximum instances count = 0  → VM не будут создаваться вообще
Maximum instances count = 1  → Максимум 1 VM одновременно
Maximum instances count = 10 → До 10 VM параллельно
```

Проверка в коде:

```java
public synchronized boolean canStartNewInstance() {
    return this.instanceLimit == OrkaConstants.UNLIMITED_INSTANCES 
        || this.instanceLimit > this.instances.size();
}
```

### 2. **Agent Pool assignment**

- VM создаются только для билдов, требующих агентов из **конкретного пула**
- Если билды не требуют этот пул → VM не создаются

### 3. **Pause/Disable профиля**

- В TeamCity можно поставить Cloud Profile на паузу
- Профиль не будет создавать новые VM
- Существующие VM продолжат работать

### 4. **Build configuration requirements**

- Настройте требования к агентам в build configuration
- VM будут создаваться только если требования совпадают

## Автоматическая очистка VM

### Когда VM удаляется?

1. **Build завершён + idle timeout истёк**
   - После завершения билда агент становится idle
   - TeamCity ждёт настроенный idle timeout
   - Затем вызывает `terminateInstance()`

2. **VM failed во время setup**
   - Ошибка deployment в Orka
   - SSH недоступен
   - Agent не запустился
   - → VM автоматически удаляется

3. **Периодическая очистка failed instances**

   ```java
   RemoveFailedInstancesTask - запускается каждые 5 минут
   ```

   Удаляет VM в статусе ERROR или с проблемами

4. **Manual termination**
   - Через TeamCity UI: Agents → Cloud → Stop
   - Вызывает `terminateInstance()`

## Диагностика

### Логи для отладки триггеров

1. **TeamCity Server logs:**

   ```
   <TeamCity_data_directory>/logs/teamcity-clouds.log
   ```

   Показывает все вызовы Cloud Plugin API

2. **Orka Plugin logs:**
   Ищите в `teamcity-clouds.log`:

   ```
   [Orka Cloud] startNewInstance with temp id: <uuid>
   [Orka Cloud] Setting up VM for image '<vm_name>'
   [Orka Cloud] VM deployed: <instanceId>
   ```

3. **Проверка canStartNewInstance:**

   ```
   [Orka Cloud] Quota exceeded. Number of instances: X and limit: Y
   ```

   Показывает когда лимит достигнут

### Полезные команды для проверки

```bash
# Проверить существующие VM в Orka
curl -X GET "https://<orka-endpoint>/resources/vm/list" \
  -H "Authorization: Bearer <token>"

# Проверить количество агентов в пуле через TeamCity API
curl "http://<teamcity>/app/rest/agentPools/id:<poolId>/agents" \
  -u "username:password"
```

## Best Practices

### Избежать неожиданных VM

1. **Начните с малого лимита**

   ```
   Maximum instances count = 1
   ```

   После тестирования увеличьте

2. **Мониторьте Orka dashboard**
   - Проверяйте количество активных VM
   - Настройте алерты на превышение лимитов

3. **Используйте отдельный Agent Pool**
   - Создайте dedicated пул только для Orka агентов
   - Не используйте Default pool

4. **Настройте build requirements**
   - Явно указывайте требования к агентам
   - VM будут создаваться только для matching builds

5. **Test на staging окружении**
   - Сначала протестируйте профиль на test Orka environment
   - Проверьте поведение создания/удаления VM

6. **Регулярно проверяйте логи**

   ```bash
   tail -f <TeamCity_data_directory>/logs/teamcity-clouds.log | grep "Orka"
   ```

## FAQ

**Q: Почему VM создалась сразу после создания профиля?**
A: TeamCity тестирует профиль после сохранения. Это нормально.

**Q: Как запретить автоматическое создание VM?**
A: Установите `Maximum instances count = 0` или поставьте профиль на паузу.

**Q: VM создаётся, но билды не запускаются на ней**
A: Проверьте:

- Agent Pool assignment
- Build requirements
- Agent authorization в TeamCity

**Q: VM не удаляется после билда**
A: Проверьте idle timeout settings в Cloud Profile.

**Q: Как создать VM заранее (pre-warming)?**
A: TeamCity не поддерживает explicit pre-warming в Cloud Plugin API. VM создаются только on-demand. Но вы можете:

- Создать dummy build который периодически запускается
- Установить выше instance limit чтобы VM оставались idle

## Summary

**Ключевые моменты:**

1. ✅ **TeamCity** контролирует когда создавать VM, не плагин
2. ✅ Основной триггер: **билд в очереди + нет свободных агентов**
3. ✅ VM может создаться при **тестировании профиля**
4. ✅ Контроль через **Instance Limit** и **Agent Pool**
5. ✅ Автоматическая очистка failed instances каждые 5 минут
