# video-processing-pipeline

> 영상 업로드 이후 필요한 후처리 작업을 안정적으로 수행하는 시스템

---

## 1. 프로젝트 소개

### Background

학원용 LMS를 설계하면서 강의 영상 업로드 이후 다음과 같은 후처리 기능이 필요했습니다.

* 영상 썸네일 생성
* 영상 길이, 해상도, Codec 등의 메타데이터 추출
* 스트리밍을 위한 영상 트랜스코딩

각 후처리 작업은 처리 시간과 실패 가능성이 다르며, 하나의 영상에 대해 여러 작업이 독립적으로 수행되어야 합니다.

따라서 영상 업로드와 여러 후처리 작업을 어떤 구조로 연결하고 처리하는 것이 적절한지 직접 구현하며 검토하고자 이 프로젝트를 시작했습니다.

### Goal

다음 요구사항을 만족하는 영상 후처리 시스템을 구현하는 것을 목표로 합니다.

* 영상 업로드와 시간이 오래 걸리는 후처리 작업이 서로 불필요하게 영향을 주지 않을 것
* 각 후처리 작업을 독립적으로 수행할 수 있을 것
* 특정 후처리 작업이 실패한 경우 해당 작업만 다시 수행할 수 있을 것
* 하나의 작업 실패가 다른 후처리 작업의 재실행으로 이어지지 않을 것
* 동일한 작업이 다시 수행되더라도 결과의 일관성을 유지할 수 있을 것
* 새로운 후처리 기능을 기존 로직의 큰 변경 없이 추가할 수 있을 것

---

## 2. 핵심 기능

### 영상 업로드

* 영상 원본 저장
* 영상 처리 상태 관리

### 썸네일 생성

* 영상의 대표 이미지 생성

### 메타데이터 추출

* 영상 길이 추출
* 해상도 추출
* Codec 정보 추출

### 트랜스코딩

* 스트리밍에 적합한 영상 포맷으로 변환
* 트랜스코딩 처리 상태 관리

### 실패 작업 재처리

* 후처리 작업별 성공 / 실패 상태 관리
* 실패한 후처리 작업 재실행

---

## 3. 기술 스택

* Java 21, Spring Boot 4.1.1
* Spring Data JPA, MySQL 8.4
* Spring for Apache Kafka, Kafka 4.0 (KRaft)
* springdoc-openapi (Swagger UI)
* Docker, Docker Compose

---

## 4. 실행 방법

### 사전 준비

```bash
cp .env.example .env   # DB 계정 정보를 원하는 값으로 수정
```

### Docker Compose로 전체 실행

앱과 MySQL을 한 번에 띄웁니다.

```bash
docker compose up --build -d
```

* API 서버: http://localhost:8080
* Swagger UI: http://localhost:8080/swagger-ui/index.html

종료:

```bash
docker compose down
```

### 로컬에서 앱만 실행

MySQL, MinIO, Kafka만 Docker로 띄우고 앱은 로컬 `local` 프로파일로 실행합니다.

```bash
docker compose up -d mysql minio kafka
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬 실행(bootRun)과 테스트에는 FFmpeg가 필요합니다 (Docker 이미지에는 포함). FFmpeg 빈이 부팅 시 바이너리를 확인하므로, 없으면 앱이 뜨지 않습니다.

```bash
brew install ffmpeg
```

---

## 5. 프로파일

| 프로파일 | 용도 |
|---|---|
| `local` | 로컬 개발 (localhost MySQL) |
| `dev` | Docker Compose / 개발 서버 (환경변수로 접속 정보 주입) |
| `prod` | 운영 (환경변수로 접속 정보 주입) |