# 📘 Russian-Roulette-Project
한성대학교 3학년 2학기 네트워크프로그래밍 프로젝트

팀원  
- **이경석 – 2171073** (Server Architecture & Core Logic)  
- **김준현 – 2171062** (Client GUI & Item Features)

---

# 🎯 Russian Roulette — 2인 네트워크 게임 (Final Phase)

본 프로젝트는 **Java + Swing GUI + TCP 소켓 통신**을 기반으로 구현한  
**2인 온라인 러시안 룰렛 게임**입니다.

LAN 환경에서 두 명의 클라이언트가 서버에 접속하여  
실시간 턴제 전투를 진행하도록 설계되었습니다.

본 게임의 철학은 다음과 같습니다:

- 서버가 모든 게임 상태(HP, Turn, Chamber, Items)를 관리하는 **Single Source of Truth**
- 클라이언트는 입력 처리 및 UI 렌더링만 담당
- 텍스트 기반 프로토콜로 명확한 통신 구조 유지
- 멀티스레드 환경에서 안정적인 동기화

---

# 🧱 프로젝트 개요

- **프로젝트명:** 러시안 룰렛 2인 네트워크 게임  
- **개발환경:** Java 17~21 / Eclipse IDE / IntelliJ IDEA  
- **사용 기술:** Swing GUI, TCP Socket 통신  
- **네트워크 구조:** 서버 1대 + 클라이언트 2대(LAN)

> 같은 Wi-Fi 환경에서 서버 IP·포트를 입력하면  
> 두 클라이언트가 자동으로 매칭됩니다.

---

# 🧩 구현 목표 및 달성 현황

## ✔ Final Phase (완료)

- [x] 2인 매칭, 닉네임 교환, READY 동기화
- [x] 6칸 랜덤 탄창 (실탄·공탄)
- [x] 조준(AIM) SELF / ENEMY
- [x] 발사(FIRE), HP 감소, 턴 교대
- [x] SELF+BLANK → 턴 유지
- [x] HP UI, Life 이미지, 탄창 인덱스 표시
- [x] **아이템 3종 (Heal, Search, Bomb) 완전 구현**
- [x] 서버 중심 동기화 시스템
- [x] 채팅 기능
- [x] EXIT_ROOM 처리
- [ ] *(제외)* 사운드 기능
- [ ] *(제외)* 카드 시스템 → 아이템으로 대체

---

# 📁 디렉터리 구조
```
src/
 ├─ server/
 │   ├─ ServerGuiMain.java     # 서버 GUI 진입점
 │   ├─ ServerFrame.java       # 서버 로그 및 제어 창
 │   ├─ Room.java              # ★ 핵심 로직 (턴, 판정, 동기화)
 │   ├─ ClientHandler.java     # 클라이언트별 통신 스레드
 │   ├─ Protocol.java          # 통신 명령어 정의
 │   └─ ServerCore.java        # 소켓 Accept 관리
 └─ client/
     ├─ ClientMain.java        # 클라이언트 진입점
     ├─ StartFrame.java        # 접속 UI
     ├─ RoomFrame.java         # 대기실 UI
     ├─ GameRoomFrame.java     # ★ 인게임 UI & 키 입력 처리
     ├─ ImageLoader.java       # 리소스 로딩 유틸리티
     └─ NetworkClient.java     # 서버 송수신 스레드
resources/
 └─ images/                    # 배경/플레이어/총/라이프/아이템 이미지 리소스
```
---

# 🔫 게임 규칙

## 피해 판정

| 조준 대상 | 탄 종류 | 결과 | 턴 변화 |
|----------|---------|-------|---------|
| SELF     | BLANK   | 아무 일 없음 | 유지 |
| SELF     | BULLET  | HP -1 (Bomb 적용 시 -2) | 교대 |
| ENEMY    | BLANK   | 아무 일 없음 | 교대 |
| ENEMY    | BULLET  | 상대 HP -1 (Bomb 시 -2) | 교대 |

- HP 초기값: **5**
- HP가 0 또는 이하이면 즉시 GAME_OVER
- 탄창 6칸 모두 소모 → 자동 재장전 + 아이템 일부 재지급

---

# 🎁 아이템 시스템

| 코드 | 이름 | 효과 |
|------|------|-------|
| H | Heal | HP +1 (최대 5) |
| S | Search | 다음 탄환이 BULLET/BLANK인지 본인에게만 전달 |
| B | Bomb | 다음 실탄 명중 시 피해 2배 |

- 슬롯 최대 6개
- 사용: 키보드 **1~6**

---

# 🖥️ 실행 방법

## 서버 실행

1. `ServerGuiMain` 실행
2. 포트 입력 (예: 7777)
3. Start 클릭  
4. 아래 메시지 확인
[Server] Listening on 7777

yaml
코드 복사

---

## 클라이언트 실행

1. `ClientMain`을 두 번 실행
2. Host/Port/Name 입력
3. Connect → RoomFrame 이동
4. 두 명 READY → GAME_START

---

# 🎮 조작 키

| 키 | 기능 |
|----|---------|
| ↑↓ | 조준 방향 변경 (SELF/ENEMY) |
| SPACE | 발사 |
| 1~6 | 아이템 사용 |
| Chat | 채팅창 |
| X | EXIT_ROOM 후 종료 |

---

# 📡 서버 ↔ 클라이언트 통신 흐름

## (1) 접속 과정

Server → Client  
HELLO

arduino
코드 복사

Client → Server  
닉네임 문자열

yaml
코드 복사

이후 Room 생성 → ROOM_CREATED 브로드캐스트

---

## (2) READY 상태

Client → Server  
READY

pgsql
코드 복사

Server → All  
READY_STATE

yaml
코드 복사

---

## (3) 게임 시작

Server → All  
GAME_START
RELOAD
TURN
AIM_UPDATE
ITEM_UPDATE

yaml
코드 복사

---

## (4) 조준

Client → Server  
AIM SELF
AIM ENEMY

yaml
코드 복사

---

## (5) 발사

Client → Server  
FIRE

pgsql
코드 복사

Server → All  
FIRE_RESOLVE RESULT=... TARGET=... HP1=.. HP2=.. B_LEFT=.. K_LEFT=..

yaml
코드 복사

---

## (6) 아이템

Client → Server  
USE_ITEM SLOT=n

pgsql
코드 복사

Server → All  
ITEM_UPDATE WHO=P1 ITEMS=...

sql
코드 복사

Search 결과는  
PEEK_RESULT TYPE=BULLET

yaml
코드 복사
처럼 **본인에게만 전송**

---

## (7) 퇴장

Client → Server  
EXIT_ROOM

arduino
코드 복사

Server → Other  
EXIT_ROOM

yaml
코드 복사

---

# 👨‍💻 개발자 코멘트

본 프로젝트는 **네트워크 프로그래밍 + GUI + 실시간 게임 로직**을 학습하기 위해 제작되었습니다.  
서버(Room)가 모든 로직을 단일하게 관리하기 때문에  
클라이언트 간 데이터 충돌 없이 안정적으로 게임을 진행할 수 있습니다.

구조가 단순하지 않으면서도 확장성이 높아  
멀티룸, 관전자 모드, 로그/리플레이 기능을 추가하는 데도 적합합니다.

---

# 📅 개발 진행 로그

- Phase-1: 기본 전투 시스템 구축  
- Phase-2: GUI 개선 + 아이템 시스템 구축  
- Final Phase: 전체 기능 안정화 및 프로젝트 완성  
- 사운드/카드 기능은 범위 조정으로 제외

---
