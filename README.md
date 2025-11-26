# 📘 Russian-Roulette-Project
한성대학교 3학년 2학기 네트워크프로그래밍 프로젝트

팀원  
- **이경석 – 2171073** (Server Architecture & Core Logic)  
- **김준현 – 2171062** (Client GUI & Item Features)

---

# 📍 1. 프로젝트 개요

본 프로젝트는 **Java(Swing GUI)** 와 **TCP 소켓 통신(ServerSocket/Socket)** 기반으로 제작한  
**2인 네트워크 러시안 룰렛 게임**입니다.

LAN/Wi-Fi 환경에서 두 명의 플레이어가 서버에 접속하여  
턴 기반 러시안 룰렛 전투를 실시간으로 진행하도록 구현되었습니다.

### 🔧 개발환경
- Java 17 ~ Java 21  
- Eclipse IDE / IntelliJ IDEA  
- Windows / macOS 지원

### 🧪 사용 기술
- Swing GUI 기반 HUD 인터페이스
- TCP Socket + UTF-8 스트림
- 멀티스레드 서버 구조(ClientHandler)
- 텍스트 기반 커스텀 통신 프로토콜
- 이미지 렌더링 기반 HP/Life/Item 표시

### 🌐 네트워크 구조
- 서버 1대 + 클라이언트 2대  
- 동일 LAN 환경에서 IP·포트 입력 후 접속  
- **Server Authoritative** 구조로 모든 판정을 서버가 담당

---

# 📍 2. 목표와 현황  
🎯 Final Phase 목표: **전투 시스템 + 아이템 전략 + GUI 연동 완성**

### ✔ 구현 완료 내역
- 2인 매칭 및 닉네임 교환
- READY 동기화
- 코인토스 기반 **초기 턴 배정**
- 6칸 탄창(BULLET/BLANK 랜덤)
- 조준(AIM) SELF / ENEMY
- 발사(FIRE) → HP 감소 + 턴 교대
- SELF + BLANK → 턴 유지
- HP UI / 라이프 이미지 표시
- 총 이미지 회전 애니메이션(상·하·좌·우)
- **아이템 3종 (Heal, Search, Bomb) 구현**
- 채팅 기능
- EXIT_ROOM 처리

### ✖ 제외된 기능
- 사운드 효과  
- 카드 시스템 → 아이템 시스템으로 대체

---

# 📍 3. 게임 규칙

## 3-1. 기본 사격 규칙

| 조준 대상 | 탄 종류 | 결과 | 턴 변화 |
|----------|---------|-------|---------|
| SELF     | BLANK   | 생존(Risk Success) | 유지 |
| SELF     | BULLET  | HP -1 | 교대 |
| ENEMY    | BLANK   | 아무 일 없음 | 교대 |
| ENEMY    | BULLET  | 상대 HP -1 | 교대 |

- HP 초기값: **5**
- HP가 0 이하 → 즉시 패배
- 탄창 6칸 모두 소모 → **서버가 자동으로 재장전**

---

## 3-2. 아이템 시스템

| 코드 | 아이템명 | 효과 |
|------|----------|--------|
| H | Heal | HP +1 회복 (최대 5) |
| S | Search | 다음 탄환이 BULLET/BLANK인지 확인 |
| B | Bomb | 다음 실탄 명중 시 피해 2배 |

### 아이템 특징
- HUD 슬롯 최대 6개  
- 사용 키: **1~6번**  
- Search 결과는 **본인에게만 비공개 메시지로 전달**  
- Bomb 효과는 **다음 발사(FIRE) 1회에만 적용됨**

---
# 📍 4. 시스템 구조

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

# 📍 5. 🔄 시스템 상세 로직 및 데이터 흐름 (Data Flow)

본 게임은 **Event-Driven 아키텍처**를 기반으로 하며,

> 클라이언트 입력(Input) → `NetworkClient` 전송(Command) →  
> 서버 `ClientHandler` 수신 → `Room`에서 로직 처리(Logic) →  
> 결과를 모든 클라이언트에 브로드캐스트(Broadcast)  

라는 흐름으로 동작합니다.

---

## 5-1. 접속 및 매칭 (Handshake Phase)

### ① 서버 시작

- `ServerGuiMain` → `ServerFrame` 에서 포트 입력 후 Start 클릭  
- `ServerCore` 가 `ServerSocket` 을 열고 `acceptLoop()` 로 클라이언트 2명을 기다림  
- 클라이언트가 접속하면 각각에 대해 `ClientHandler` 스레드를 생성

### ② 클라이언트 접속

- `ClientMain` 실행 → `StartFrame` 에서 Host / Port / Name 입력  
- 내부적으로 `NetworkClient` 가 서버와 소켓 연결  
- 연결 직후 서버에서 `HELLO` 를 보내고, 클라이언트는 닉네임 문자열을 전송  
- `ClientHandler` 가 이 닉네임을 읽어 `Room` 에 등록

### ③ Room 생성 및 대기실 입장

- 서버에서 두 명이 모두 접속하면 `Room` 객체를 생성  
- `Room` 이 각 클라이언트에게 `ENTER_ROOM`, `ROOM_STATUS` 등을 보내  
  둘 다 `RoomFrame` (대기실 화면) 으로 진입  
- 이 시점부터 각 `ClientHandler` 는
  - 클라이언트 → 서버: 텍스트 한 줄씩 읽기
  - 서버 → 클라이언트: `Room.broadcast()` / `sendTo()` 로 전송  
  구조로 고정됨

---

## 5-2. 게임 준비 및 시작 (Ready Phase)

### ① Ready 입력

- 사용자가 `RoomFrame` 의 READY 버튼 클릭  
- `RoomFrame` → `NetworkClient.sendLine()` 을 통해  
  - Client → Server : `READY` 문자열 전송

### ② 서버 Ready 동기화

- `ClientHandler` 가 `READY` 를 수신  
- `Room` 의 `onReady()` (또는 이에 해당하는 메서드) 에 전달  
- `Room` 은 내부에 `isReadyP1`, `isReadyP2` 같은 플래그를 갱신  
- 각 상태 변경 시
  - Server → All : `READY_STATE ...` 를 전송하여  
    두 클라이언트의 대기실 화면에 “준비 완료” 상태를 표시

### ③ GAME_START

- 두 명 모두 Ready 상태가 되면 `Room.startGame()` 실행
  - 6칸 탄창 랜덤 생성 (실탄/공탄 배열)
  - 초기 HP, 턴, 조준 방향, 아이템 목록 등 초기 상태 셋업
- 이후 아래 순서로 메시지들을 브로드캐스트
  - `GAME_START` : `RoomFrame` → `GameRoomFrame` 으로 화면 전환
  - `RELOAD` : 새 탄창 정보 동기화
  - `AIM_UPDATE` : 기본 조준(SELF) 설정
  - `TURN` : 첫 턴 플레이어 지정
  - `ITEM_UPDATE` : 처음 지급된 아이템 목록 전송

---

## 5-3. 인게임 데이터 흐름 (Game Loop)

> **핵심 원칙: 모든 판정은 서버 `Room` 이 담당한다.**  
> 클라이언트는 “입력 + 화면 표시”에만 집중한다.

인게임 동안 주요 이벤트는 **조준(AIM)**, **아이템 사용(USE_ITEM)**, **발사(FIRE)** 세 가지입니다.

---

### 5-3-1. 조준 (AIM)

#### 입력 발생 (클라이언트)

- `GameRoomFrame` 에서 `KeyListener` 가 방향키 입력을 감지  
- `↑ / ↓` 입력에 따라 현재 조준 대상을 SELF / ENEMY 로 바꾸고  
  - Client → Server : `AIM SELF` 또는 `AIM ENEMY` 를 전송  
  - 이 전송은 항상 `NetworkClient.sendLine()` 으로 이루어짐

#### 서버 처리

- `ClientHandler` 가 수신한 문자열이 `Protocol.AIM` 으로 시작하는지 확인  
- `Room.onAim(player, target)` 과 같은 메서드에 위임
  - 내부에서 해당 플레이어의 조준 상태를 변경
  - 턴 검사(자기 턴이 아닌 경우 무시 등)가 이 안에서 이루어짐

#### 브로드캐스트 및 화면 반영

- 처리 결과를 바탕으로
  - Server → All : `AIM_UPDATE WHO=P1 TARGET=SELF` 형식으로 전송  
- 각 클라이언트의 `NetworkClient` 가 이를 수신하여
  - `GameRoomFrame.applyAimUpdate()` 같은 메서드를 호출
  - 총 이미지 각도/방향을 갱신하여 **회전 애니메이션** 과 HUD 를 일치시키게 됨

---

### 5-3-2. 아이템 사용 (USE_ITEM)

#### 입력 발생 (클라이언트)

- 플레이어가 `1~6` 숫자 키를 누르면  
  - `GameRoomFrame` 의 키 이벤트 → 선택된 아이템 슬롯 인덱스를 계산  
  - Client → Server : `USE_ITEM SLOT=n` 문자열 전송

#### 서버 처리 (`Room`)

- `ClientHandler` 가 `USE_ITEM` 메시지를 읽어 `Room.onUseItem(player, slot)` 호출
- `Room` 내부 로직:
  - 해당 슬롯에 아이템이 없으면 무시
  - `Heal(H)` 인 경우 → HP +1 (최대 5), 변경된 HP를 저장
  - `Search(S)` 인 경우 → 현재/다음 탄 정보를 확인
  - `Bomb(B)` 인 경우 → “다음 실탄 명중 시 2배 피해” 플래그 설정
- 공통적으로, 아이템은 사용 후 슬롯에서 제거 처리

#### 브로드캐스트 및 비공개 정보 처리

- HP/아이템 상태 변화:
  - Server → All : `ITEM_UPDATE WHO=P2 ITEMS=H---B-` 같은 형식으로 전체 HUD 를 갱신
- Search 처럼 **비공개 정보**는:
  - Server → 해당 플레이어에게만 : `PEEK_RESULT TYPE=BULLET`  
  - 상대 플레이어에게는 전송하지 않음

---

### 5-3-3. 발사 및 판정 (FIRE)

#### 입력 발생 (클라이언트)

- `GameRoomFrame` 의 `KeyListener` 가 `Spacebar` 입력 감지  
- 자기 턴일 때만
  - Client → Server : `FIRE` 전송

#### 서버 처리 (`Room.onFire`)

1. **턴 검증**  
   - 현재 턴이 아닌 플레이어가 쏘면 무시 또는 에러 처리
2. **탄창 판정**  
   - `currentIndex` 위치의 탄이 `BULLET` 인지 `BLANK` 인지 확인  
   - Bomb 플래그가 켜져 있으면 데미지 2배 적용
3. **HP/게임 상태 변경**  
   - SELF / ENEMY 조준에 따라 피해 대상 결정  
   - HP 감소 후 `hp <= 0` 이면 승패 판정
4. **턴/인덱스 갱신**  
   - ENEMY + BLANK 인 경우에도 턴 교대  
   - SELF + BLANK 인 경우에는 턴 유지  
   - 사용한 탄을 기준으로 `currentIndex++`
   - 6발 모두 사용 시 새 탄창을 만들고 `RELOAD` 브로드캐스트
5. **결과 패킷 생성**  
   - `FIRE_RESOLVE RESULT=BULLET TARGET=ENEMY HP1=4 HP2=3 INDEX=2` 형태로 결과 문자열 구성

#### 브로드캐스트 및 화면 반영

- Server → All : 위 `FIRE_RESOLVE` 메시지 전송  
- 각 클라이언트에서
  - `NetworkClient` 가 메시지를 읽고  
  - `GameRoomFrame.applyFireResult()` 에 전달  
  - HP 바 / 라이프 이미지 / 탄창 인덱스 / 로그 텍스트 등을 갱신

---

## 5-4. 아이템 / 사망 / 퇴장과 같은 기타 흐름

### ① Search 결과 처리

- 서버에서 Search 판정을 한 뒤
  - Server → 해당 플레이어 : `PEEK_RESULT TYPE=BULLET`  
- `GameRoomFrame` 은 이 패킷을 받아
  - 화면 하단 로그 또는 팝업으로  
    “다음 탄: 실탄 / 공탄” 을 표시하지만  
  - 반대편 클라이언트는 아무 정보도 받지 못함

### ② 게임 종료 (HP 0 이하)

- `Room.onFire()` 안에서 HP가 0 이하가 되는 순간
  - 승자/패자를 결정하고  
  - Server → All : `GAME_OVER WINNER=P1` 형식의 패킷을 전송  
- 클라이언트는
  - 팝업 또는 HUD 메시지로 “P1 승리” 등을 표시하고  
  - 입력을 막은 뒤, 종료 또는 다시 접속을 유도하도록 처리

### ③ 퇴장(EXIT_ROOM)

- 사용자가 게임 도중 창을 닫거나 나가기 버튼을 누르면  
  - Client → Server : `EXIT_ROOM` 전송  
- 서버 `Room.onExit()` 에서
  - 해당 플레이어를 제거하고  
  - 남은 플레이어에게
    - Server → Other : `EXIT_ROOM` 알림 전송  
- 남은 클라이언트는
  - 팝업 “상대방이 퇴장했습니다.” 표시 후  
  - 메인 화면 또는 종료 상태로 돌아감

---

> 요약하면,  
> **`GameRoomFrame` 에서 발생한 모든 입력 → `NetworkClient` → `ClientHandler` → `Room` → 다시 `NetworkClient` → `GameRoomFrame`**  
> 으로 순환하는 구조이며,  
> **실제 판정과 수치는 항상 `Room` 에서만 계산**되도록 설계되어  
> 동기화 문제와 치트 가능성을 줄이는 구조입니다.


---
# 📍 6. 실행 방법

## 서버 실행
1. `ServerGuiMain` 실행  
2. 포트 입력 (예: 7777)  
3. Start 클릭  
4. 로그에 아래 표시되면 준비 완료  

[Server] Listening on 7777

---

## 클라이언트 실행
1. `ClientMain` 두 번 실행  
2. Host / Port / Name 입력  
3. Connect 클릭  
4. 두 명 READY → **GAME_START**

---

# 📍 7. 조작 키

| 키 | 설명 |
|----|------|
| ↑ / ↓ | 조준 방향 변경 (SELF / ENEMY) |
| SPACE | 발사 |
| 1~6 | 아이템 사용 |
| Chat 버튼 | 채팅 열기 |
| 창 닫기(X) | EXIT_ROOM 후 종료 |

---

# 📍 8. 기술 요약

| 구성 | 설명 |
|------|-------|
| **통신 방식** | TCP 기반, UTF-8 텍스트 프로토콜 |
| **서버 구조** | ClientHandler 스레드 + Room에서 모든 로직 관리 |
| **클라이언트** | 입력 처리 / 이미지 렌더링 / HUD 담당 |
| **게임 로직** | 턴, HP, 탄창, AIM/FIRE, 아이템 모두 서버에서 판정 |
| **확장성** | 멀티룸, 로그, 관전자 모드 등 확장 가능 |

---

# 📍 9. 최종본 요약 설명

본 프로젝트는 **네트워크 통신 + 실시간 게임 로직 + GUI 렌더링 + 아이템 전략 시스템**을  
하나의 구조로 통합한 실전형 턴제 네트워크 게임입니다.

### 서버(Room)는:
- HP / 턴 / 탄창 / 아이템 등 **모든 상태를 단일하게 관리**
- 클라이언트는 그 결과만 반영
- 동기화 문제 없이 안정적

### 클라이언트는:
- 입력 처리  
- HUD 렌더링  
- 이미지 로딩  
- 애니메이션  

에 집중하도록 설계되었습니다.

**학습 목적 + 실전 구조**를 모두 갖춘 완성도 높은 네트워크 게임 프로젝트입니다.

---

