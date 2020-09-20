## Сборка

```sh
sbt "clean;docker"
```

## Запуск

Перед запуском удостоверьтесь конфигурация и шаблоны заявлений на месте.

Предположим, рабочая директория сервиса на сервере - это папка `/usr/share/barbarissa` (далее исходим из этого). 

Тогда сервис можно запустить следующим образом:
```sh
docker run --name barbarissa -m 400M -d -p 10203:10203 \
-v /usr/share/barbarissa:/var/lib/barbarissa \
vsuharnikov/barbarissa-backend:latest
```

### Шаблоны

Это файлы в формате `docx` внутри папки `/usr/share/barbarissa/templates` вида `<ID компании>-<Тип заявления>.docx`, где:
* **ID компании** - идентификатор компании (см. "Как обновить информацию о всех сотрудниках");
* **Тип заявления** - тип заявления:
** _with-compensation_ - с компенсацией (например, ежегодный отпуск);
** _without-compensation_ - без компенсации (например, отгул).

В шаблоне допустимо использование следующих переменных:
* `{{sinGenPosition}}` - должность в родительном падеже (например: `программиста`);
* `{{sinGenFullName}}` - ФИО в родительном падеже (например: `Вячеславова Вячеслава Вячеславовича`);
* `{{sinGenFromDate}}` - начало отсутствия в родительном падеже (например: `3 октября 2020`);
* `{{plurDaysQuantity}}` - количество дней (например: `5 календарных дней`);
* `{{reportDate}}` - дата написания заявления (например: `10 сентября 2020`).

### Конфигурация
 
`/usr/share/barbarissa/main.conf`

Пример:
```hocon
include "application"

barbarissa.backend {
  http-api {
    api-key-hash = "sha256 hash of your api token" # Хеш секретного токена для работы по API
  }

  jira {
    rest-api = "https://jira.yourdomain.com/rest/api"
    credentials {
      username = "jirabot"
      password = "jirabotpassword"
    }
  }

  ms-exchange {
    api-target.fixed.url = "https://outlook.yourserver.com/ews/exchange.asmx"
    credentials {
      username = "bot@yourserver.com"
      password = "botpassword"
    }
  }

  # Описания причин отсутствия из JIRA
  absence-reasons = [
    {
      id = "10298"
      name = "Больничный"
      need-appointment = true
    },
    {
      id = "10301"
      name = "Отпуск ежегодный"
      need-claim = "with_compensation"
      need-appointment = true
    },
    {
      id = "10822"
      name = "Больничный без больничного листа (3 дня в полгода)"
    },
    {
      id = "10297"
      name = "Удаленная работа (3 дня в квартал)"
    },
    {
      id = "10817"
      name = "Отгул за свой счет"
      need-claim = "without_compensation"
    },
    {
      id = "10300"
      name = "Внешняя встреча/конференция (без командировки)"
    },
    {
      id = "10816"
      name = "Командировка"
    }
  ]

  processing {
    auto-start = true # Периодическая обработка
    process-after-absence-id = "HR-1000" # Пропустить все задачи в JIRA включая HR-1000 
  }
}
```

## API

Доступен Swagger UI: http://127.0.0.1:10203/docs

## Как это работает

Сервис интегрируется с:
* JIRA, чтобы автоматизированно получать заявки.
* Microsoft Exchange, чтобы создавать запись в календаре.

Периодически запускается 2 задачи:
1. Проверка новых заявок в JIRA и добавление их в очередь.
2. Обработка незавершенных заявок из очереди.

### Обработка заявок

Чтобы заявка считалась завершенной, необходимо:
1. Успешно сформировать и отправить заявление.
2. Успешно отметить отсутствие в календаре.

Эти две подзадачи выполняются независимо. 

При обработке заявки выполняется только незавершенная часть: если удалось отметить отсутствие в календаре, 
но не удалось отправить заявление, то второй раз отсутствие отмечаться не будет. 

Для каждой заявки есть ограниченное количество попыток обработки, по достижению которого повторные попытки прекращаются.

## FAQ
 
### Как обновить информацию о всех сотрудниках

```sh
curl -H 'X-Auth-Token: <paste-token-here>' -X PATCH -F 'data=@/path/to/employees.csv' http://127.0.0.1:10203/api/v0/employee
```

Файл `employees.csv` должен быть в формате CSV ([RFC-4180](https://tools.ietf.org/html/rfc4180)) без заголовка:
* Колонки разделены запятой (`,`);
* Значения при необходимости могут быть обернуты в двойные кавычки (`"`).

Колонки:
1. **ФИО**. Используется в заявлениях;
2. **Должность**. Используется в заявлениях;
3. **Почта**. Сформированные заявления отправляются на этот ящик;
4. **Пол**: МУЖ или ЖЕН. Используется для более точного склонения ФИО;
5. **ID компании**. Используется для выбора шаблона заявления. 

Пример:

```csv
Вячеславов Вячеслав Вячеславович,Разработчик,vyacheslav@example.com,МУЖ,company1
Вячеславова Вячеслава Вячеславовна,Тестировщик,vyacheslava@example.com,ЖЕН,company2
```
