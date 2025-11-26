# 🎲 Russian Roulette – 2인 네트워크 게임  
Java · TCP Socket · Swing GUI

---

## 📌 프로젝트 개요

이 프로젝트는 **2명의 플레이어가 TCP 서버에 접속해**  
서로에게 총을 쏘며 상대방 HP를 0으로 만들면 승리하는  
**온라인 러시안 룰렛 게임**입니다.

- 서버 = 게임의 **두뇌 (HP/턴/탄창 관리)**
- 클라이언트 = **눈과 손 (UI, 입력, 채팅)**
- 6발 랜덤 실린더 + SELF 공탄 시 턴 유지 → **전략적 심리전**

---

# 🎮 게임 규칙

## 🩸 HP  
- 시작 HP: **5**
- 상대 HP를 **0**으로 만들면 승리

## 🔫 실린더 규칙  
- 6칸 배열  
- `1 = 실탄`, `0 = 공탄`  
- 6발 소진 시 자동 재장전 (서버에서 새 실린더 생성)

## 🔁 턴(Turn) & 심리전 규칙

| 조준 대상 | 탄환 결과 | 효과 | 턴 전환 |
|----------|-----------|------|---------|
| Enemy    | Bullet    | 상대 HP -1 | 턴 교대 |
| Enemy    | Blank     | 변화 없음 | 턴 교대 |
| Self     | Bullet    | 내 HP -1 | 턴 교대 |
| Self     | Blank     | 변화 없음 | **턴 유지 (AGAIN)** |

> ⭐ SELF + BLANK = **턴 유지**  
> 턴을 유지하기 위해 자기 자신에게 공탄을 기대하고 쏘는 **심리전이 핵심**

---

# 📁 폴더 구조

```text
src/
 ├─ server/                  # 서버: 게임의 '두뇌'
 │   ├─ ServerGuiMain.java   # 서버 진입점 (GUI 실행)
 │   ├─ ServerFrame.java     # 서버 제어 GUI (Start/Stop/로그)
 │   ├─ ServerCore.java      # ServerSocket + acceptLoop
 │   ├─ ClientHandler.java   # 클라이언트 1명당 통신 스레드
 │   ├─ Room.java            # ★ 핵심: 방/턴/HP/탄창/승패 로직
 │   └─ Protocol.java        # 문자열 기반 통신 명령어
 │
 └─ client/                  # 클라이언트: 게임의 '눈과 손'
     ├─ ClientMain.java      # 클라이언트 진입점
     ├─ StartFrame.java      # IP/Port/Name 입력 화면
     ├─ RoomFrame.java       # 대기실(Lobby)
     ├─ GameRoomFrame.java   # ★ 게임방 UI + 채팅 + 입력 처리
     ├─ NetworkClient.java   # 서버와의 송수신 담당
     └─ ImageLoader.java     # 이미지 로딩 유틸
🏗 서버 구조 (server/)
🔹 ServerGuiMain.java
서버 프로그램의 메인 진입점

Swing 기반 ServerFrame GUI를 실행

🔹 ServerFrame.java
서버 UI 화면

포트 입력 필드

Start / Stop 버튼

서버 로그 출력(TextArea)

서버 종료/예외 처리 GUI 지원

🔹 ServerCore.java
서버의 중심 엔진

지정 포트에 대해 ServerSocket 열기

별도 스레드에서 acceptLoop() 실행

첫 번째 접속 → P1

두 번째 접속 → P2

두 명이 모이면:

Room 생성

ClientHandler 2개 생성 후 스레드 실행

ROOM_CREATED, ENTER_ROOM 방송

🔹 ClientHandler.java
클라이언트 1명당 1 스레드

기능:

클라이언트에서 오는 문자열 메시지를 readLine()으로 수신

메시지가 CHAT, READY, 이후 확장될 AIM, FIRE인지 판별

해당 메시지를 Room 으로 전달

Room이 보내는 메시지를 클라이언트에게 write

🔹 Room.java
방(2인)을 관리하는 핵심 로직 클래스

P1, P2 핸들러 관리

브로드캐스트 기능 (ROOM_STATUS, ROOM_CREATED, CHAT 등)

추후 확장:

currentTurn

HP 관리

탄창 배열(6칸)

실탄/공탄 판정

승패 판정

FIRE_RESULT, TURN, UPDATE 등 추가 프로토콜 처리

🔹 Protocol.java
문자열 기반 프로토콜 정의
예:

HELLO

ROOM_STATUS

ROOM_CREATED

ENTER_ROOM

CHAT

(추가 예정)

AIM

FIRE

RESULT

TURN

WINNER

🖥 클라이언트 구조 (client/)
🔹 ClientMain.java
클라이언트 실행 진입점

StartFrame 실행

🔹 StartFrame.java
IP, Port, Name 입력 필드

“Connect / Start” 버튼

입력 완료 → 서버 연결 → RoomFrame 으로 즉시 전환

🔹 RoomFrame.java (대기실)
서버로부터 수신된 상태 표시:

ROOM_STATUS

ROOM_CREATED

두 명이 모두 접속하면:

서버 → ENTER_ROOM 전송

클라이언트 → 자동으로 GameRoomFrame 이동

🔹 GameRoomFrame.java
게임 플레이가 진행되는 메인 화면
구성 요소:

RoomCanvas (배경 + 캐릭터 + 이름)

Chat 버튼

조작키 안내 버튼

이후 확장: HUD(HP/턴/탄창)

현재 구현:

채팅 송수신

서버에서 오는 메시지 실시간 반영

🔹 NetworkClient.java
서버와 TCP 연결 담당

writeLine() → 메시지 서버로 전송

readLine() 스레드 → UI 콜백으로 전달

Swing Thread-safe 처리(SwingUtilities.invokeLater)

🔹 ImageLoader.java
/images/... 폴더의 리소스를
BufferedImage 로 로딩해 UI에서 사용

🔄 전체 동작 흐름
🚀 1) 서버 실행
ServerGuiMain 실행

포트 입력 → Start

로그 창에 “Listening…” 출력

서버가 클라이언트 접속 대기

🎮 2) 클라이언트 접속
ClientMain 실행

Host / Port / Name 입력

서버 연결 성공 → ROOM_STATUS 수신

RoomFrame 으로 이동하여 상대방 대기

🧍‍♂️🧍‍♀️ 3) 매칭(대기실)
첫 번째 플레이어 → WAITING 1/2

두 번째 플레이어 → READY 2/2

서버:

ROOM_CREATED P1=<name> P2=<name>

ENTER_ROOM

클라이언트: GameRoomFrame으로 전환

💬 4) 채팅 기능 흐름
클라이언트 → 서버
nginx
코드 복사
CHAT 메시지내용
서버(ClientHandler) → Room
scss
코드 복사
Room.broadcastChat(sender, msg)
Room → 모든 클라이언트
yaml
코드 복사
CHAT sender: msg
클라이언트(UI)
채팅창에 즉시 append

너무 길면 오래된 메시지 자동 제거

🔧 향후 확장(러시안 룰렛 핵심 구현 예정)
서버(Room)에 추가할 상태
currentTurn

hpP1 / hpP2

cylinder[6]

fireIndex(현재 탄창 위치)

추가 프로토콜 예시
nginx
코드 복사
AIM SELF
AIM ENEMY
FIRE
FIRE_RESULT SELF BLANK KEEP_TURN
TURN P2
HP P1=5 P2=4
WINNER P1
▶ 실행 방법
서버 실행
ServerGuiMain 실행

포트 입력 → Start

로그에서 “Listening”이 뜨면 준비됨

클라이언트 실행
ClientMain 실행

IP / Port / Name 입력

Connect

RoomFrame → GameRoomFrame 자동 전환

👥 팀 정보
이경석 (2171073)

김준현 (2171062)

📌 요약
이 프로젝트는:

Java TCP Socket 기반 2인 네트워크 게임

서버/클라이언트 구조 완전 분리

이미 구현됨:
✔ 매칭 로직
✔ 로비 → 게임방 이동
✔ 채팅
✔ 이미지 기반 UI

앞으로 추가될 핵심 기능:
🔥 턴 관리
🔥 HP / 탄창(6칸) / 실탄·공탄 랜덤
🔥 FIRE / AIM / RESULT 프로토콜
🔥 승패 처리
