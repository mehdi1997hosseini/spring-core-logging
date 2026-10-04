# Spring Boot 3.5.7 + Java 21 + PostgreSQL + EFK

پروژه نمونه شامل:

- Java 21
- Spring Boot 3.5.7
- Spring Web / MVC
- Spring Data JPA
- PostgreSQL
- Lombok
- MapStruct
- Docker / Docker Compose
- Elasticsearch 8.19.8
- Fluent Bit 4.1
- Kibana 8.19.8

## معماری EFK

Docker containerهای برنامه لاگ JSON تولید می‌کنند.
Fluent Bit لاگ‌های Docker را از `/var/lib/docker/containers` می‌خواند و به Elasticsearch ارسال می‌کند.
Kibana برای مشاهده و جست‌وجوی لاگ‌ها استفاده می‌شود.

## اجرا

در ریشه پروژه:

```bash
docker compose up --build
```

سرویس‌ها:

- Application: http://localhost:8080
- PostgreSQL: localhost:5432
- Elasticsearch: http://localhost:9200
- Kibana: http://localhost:5601

## تست API

ساخت User:

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Mehdi\",\"email\":\"mehdi@example.com\"}"
```

دریافت Userها:

```bash
curl http://localhost:8080/api/users
```

## مشاهده لاگ‌ها در Kibana

1. وارد Kibana شوید.
2. به Discover بروید.
3. یک Data View با الگوی `spring-boot-efk-*` بسازید.
4. فیلد زمان را در صورت درخواست Kibana روی `@timestamp` قرار دهید.

> نکته: این Compose برای محیط توسعه/یادگیری طراحی شده است. Elasticsearch security عمداً در این نمونه خاموش شده است تا راه‌اندازی EFK ساده باشد. برای Production باید security، secrets، resource limits، persistence و lifecycle لاگ‌ها به‌صورت مناسب تنظیم شوند.
