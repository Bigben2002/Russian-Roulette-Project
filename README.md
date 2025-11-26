
# 🎲 Russian Roulette – 2인 네트워크 게임 (Java / Socket / Swing)

> **과목**: 네트워크 프로그래밍  
> **주제**: 2인용 온라인 러시안 룰렛 게임 (TCP 소켓 + Swing GUI)  
> **언어 / 환경**: Java 17~21, Eclipse, TCP Socket, Swing
팀원 <br>
이경석 - 2171073 <br>
김준현 - 2171062
 

---

## 1. 게임 개요

두 명의 플레이어가 **같은 서버에 접속**해,  
서버가 관리하는 러시안 룰렛 규칙에 따라 서로에게 총을 쏘며 **상대 HP를 0**으로 만들면 승리하는 네트워크 게임입니다.

- 서버는 **게임의 두뇌** 역할 (방/턴/HP/탄창 관리)
- 클라이언트는 **눈과 손** 역할 (화면 표시 + 입력 전달)
- 같은 Wi-Fi / LAN 환경에서 IP + Port를 입력해 서로 접속

---

## 2. 기본 규칙

### 2-1. HP & 승리 조건

- 각 플레이어는 **HP 5**로 시작
- 상대방 HP를 **0으로 만들면 승리**
- HP는 실탄 발사에만 영향을 받음

### 2-2. 탄창(실린더) 규칙

- 6칸짜리 실린더에 **실탄(Bullet)** 과 **공탄(Blank)** 이 랜덤 배치
- 예: `[B, 0, B, 0, 0, B]` (서버에서 랜덤 생성)
- 6발을 모두 사용하면 다시 6발을 랜덤으로 재장전

### 2-3. 턴(Turn) 및 심리전 규칙

| 조준 대상 (AIM) | 탄환 결과 (Result) | 효과 (Effect)        | 턴 전환 (Turn)   |
|-----------------|--------------------|----------------------|------------------|
| 상대 (Enemy)    | 실탄 (Bullet)      | 상대 HP -1           | 턴 교대          |
| 상대 (Enemy)    | 공탄 (Blank)       | 아무 일도 없음      | 턴 교대          |
| 자신 (Self)     | 실탄 (Bullet)      | 내 HP -1 (**최악**) | 턴 교대          |
| 자신 (Self)     | 공탄 (Blank)       | 아무 일도 없음      | **턴 유지 (AGAIN)** |

> **핵심 포인트**  
> `Self + Blank → 턴 유지` 규칙 때문에,  
> 플레이어는 “공탄일 것”을 믿고 자신에게 쏴서 턴을 유지할지,  
> 그대로 상대에게 쏴서 마무리할지를 계속 고민해야 합니다.

---

## 3. 폴더 구조

```text
src/
 ├─ server/                 # 서버: 게임의 '두뇌'
 │   ├─ ServerGuiMain.java  # 서버 진입점 (main) - GUI 실행
 │   ├─ ServerFrame.java    # 서버 제어 GUI (포트 입력, Start/Stop, 로그 출력)
 │   ├─ ServerCore.java     # ServerSocket listen + 클라이언트 accept + Room 생성
 │   ├─ ClientHandler.java  # 클라이언트 1명당 1개씩 생성되는 통신 스레드
 │   ├─ Room.java           # ★ 핵심: 방, 플레이어 2명 관리, (확장: 턴/HP/탄창 로직)
 │   └─ Protocol.java       # 통신 명령어 상수 정의 (텍스트 프로토콜)
 │
 └─ client/                 # 클라이언트: 게임의 '눈과 손'
     ├─ ClientMain.java     # 클라이언트 진입점 (main) - StartFrame 실행
     ├─ StartFrame.java     # IP/Port/Name 입력 창
     ├─ RoomFrame.java      # 대기실(Lobby), 상대 접속/READY 상태 표시
     ├─ GameRoomFrame.java  # ★ 메인 게임 화면 (그래픽 + 입력 처리 + 채팅)
     ├─ NetworkClient.java  # 서버 송수신 전담 (소켓 + 리더 스레드)
     └─ ImageLoader.java    # 리소스 이미지 로딩 유틸리티
4. 각 클래스 역할 정리
4-1. server 패키지
ServerGuiMain.java
public static void main(String[] args) 를 가진 서버 시작점

내부에서 ServerFrame 을 생성하여 GUI 서버 창을 띄움

ServerFrame.java
서버를 컨트롤하는 Swing GUI

주요 기능

포트 번호 입력 (예: 7777)

Start/Stop 버튼으로 서버 시작 / 종료

서버 로그 출력 (접속, 에러, 방 생성 등)

ServerCore.java
실제 ServerSocket 을 열어 클라이언트를 받는 엔진

주요 역할

start(port):

지정 Port로 ServerSocket 생성

별도 스레드에서 acceptLoop() 실행

acceptLoop():

클라이언트 연결을 기다리며, 두 명이 모이면 Room 을 생성

각 소켓마다 ClientHandler 생성 → 스레드로 실행

Room 에 두 클라이언트를 등록하고, 방 생성 / 입장 메시지 브로드캐스트

ClientHandler.java
클라이언트 1명당 1개씩 생성되는 스레드

역할

소켓의 입력 스트림으로부터 한 줄씩 읽기

프로토콜에 따라:

CHAT ... → Room 에 전달

(향후) FIRE, AIM, READY 등도 처리

Room 이 보내는 메시지를 자신의 클라이언트에게 전송

Room.java
두 클라이언트를 하나의 게임 방으로 묶는 핵심 클래스

현재 역할

방에 속한 두 명의 핸들러 관리

ROOM_CREATED, ROOM_STATUS, ENTER_ROOM, CHAT 등 브로드캐스트

확장 예정

턴(누가 쏠 차례인지) 관리

HP, 탄창 배열(6칸), 실탄/공탄 판정

승리/패배 시 게임 종료 브로드캐스트

Protocol.java
서버와 클라이언트 사이에 오가는 문자열 상수를 모아둔 클래스

예시

HELLO

ROOM_STATUS

ROOM_CREATED

ENTER_ROOM

CHAT

(추가 예정) TURN, AIM, FIRE, RESULT, RELOAD 등

4-2. client 패키지
ClientMain.java
클라이언트 프로그램의 시작점

StartFrame 을 띄워 접속 정보 입력 UI 를 보여줌

StartFrame.java
접속 정보 입력 화면

Host/IP

Port

Name (게임 내 닉네임)

Connect / Start 버튼 클릭 시:

NetworkClient 를 통해 서버에 접속

성공 시 RoomFrame 으로 화면 전환

RoomFrame.java (로비 / 대기실)
두 플레이어가 모일 때까지 상태를 보여주는 화면

표시 내용

현재 대기 인원 (WAITING 1/2, WAITING 2/2 등)

P1 / P2 이름

서버에서 수신하는 메시지

ROOM_STATUS ... → 상태 라벨 갱신

ROOM_CREATED ... → P1/P2 이름 세팅

ENTER_ROOM ... → GameRoomFrame 으로 전환

GameRoomFrame.java (메인 게임 화면)
실제 플레이가 진행되는 화면

구성 요소

배경 / 캐릭터 이미지 (위: 상대, 아래: 나) – RoomCanvas 에서 그림

내 이름, 상대 이름, (확장) HP/턴/탄창 HUD

채팅 버튼 (누르면 채팅창 오픈)

현재 구현 중심

채팅 송수신

확장 예정

키보드 입력 (조준 방향, 발사 키)

서버와의 턴/HP/탄창 동기화

NetworkClient.java
소켓 통신을 담당하는 클라이언트 네트워크 모듈

역할

서버와 TCP 연결 생성

서버에서 오는 텍스트 라인을 별도 스레드에서 계속 읽기

읽은 라인을 콜백(Consumer<String>) 으로 UI에 전달

클라이언트가 입력한 명령/채팅을 서버에 전송

ImageLoader.java
/images/... 같은 리소스 경로에서 이미지를 읽어오는 유틸

Swing ImageIcon / BufferedImage 로 변환해 UI에서 사용

5. 실행 흐름 (호출 순서)
5-1. 서버 실행 플로우
ServerGuiMain.main()

ServerFrame 생성 및 표시

사용자가 포트 입력 후 Start 버튼 클릭

ServerFrame → ServerCore.start(port) 호출

ServerCore.start(port)

ServerSocket 생성

acceptLoop() 를 새 스레드로 실행

acceptLoop()

첫 번째 클라이언트 접속: P1으로 등록

두 번째 클라이언트 접속: P2로 등록

Room + ClientHandler 2개 생성

Room 에서 ROOM_CREATED, ENTER_ROOM 브로드캐스트

5-2. 클라이언트 실행 플로우
ClientMain.main()

StartFrame 생성 및 표시

사용자가 IP / Port / Name 입력 → Connect

StartFrame → NetworkClient 생성 + 서버 접속

성공 시 RoomFrame 으로 전환

RoomFrame

서버에서 오는:

ROOM_STATUS → 상태 텍스트 업데이트

ROOM_CREATED → P1 / P2 이름 표시

ENTER_ROOM → GameRoomFrame 생성 후 화면 전환

GameRoomFrame

서버에서 오는 다양한 메시지 처리

채팅 버튼 / (추가) 키보드 입력 처리

6. 채팅 기능 흐름
클라이언트 → 서버

사용자가 채팅창에 메시지를 입력 후 전송

NetworkClient.send("CHAT " + message) 호출

서버 (ClientHandler)

한 줄 읽기 → "CHAT " 으로 시작하는지 확인

Room.broadcastChat(senderName, message) 호출

서버 (Room)

두 플레이어 모두에게
CHAT <이름>: <내용> 형식으로 브로드캐스트

클라이언트 수신

NetworkClient 리더 스레드 → UI 콜백

GameRoomFrame / 채팅 창에서 메시지 추가

7. 러시안 룰렛 게임 로직 설계 (확장 포인트)
현재 코드 상태

네트워크 연결, 2인 매칭, 로비 → 게임방 전환, 채팅 기능 구현 완료

HP / 탄창 / 턴 / 승패 로직은 Room 중심으로 추가 예정

7-1. 상태 관리 주체
Room (서버) 에서 다음 상태를 관리:

currentTurn (현재 턴인 플레이어)

hpP1, hpP2

cylinder[] (길이 6, 0: 공탄, 1: 실탄)

currentIndex (다음에 발사될 칸)

클라이언트는 “표시만” 담당:

서버에서 보낸 STATUS/UPDATE 메시지를 받아 HP, 턴, 탄 수 등을 화면에 반영

7-2. 예상 프로토콜 확장 예시
text
코드 복사
# 클라이언트 -> 서버
AIM SELF
AIM ENEMY
FIRE

# 서버 -> 클라이언트
TURN P1
TURN P2
HP P1=4 P2=5
FIRE_RESULT SELF BLANK KEEP_TURN
FIRE_RESULT ENEMY BULLET SWITCH_TURN
WINNER P1
LOSER P2
8. 실행 방법
8-1. 준비 사항
JDK 17 이상 (JDK 21 사용 가능)

Eclipse 또는 IntelliJ / VS Code 등 Java IDE

같은 네트워크(LAN / Wi-Fi)에서 서버 IP를 알고 있을 것

8-2. 서버 실행
IDE에서 ServerGuiMain 을 Java Application 으로 실행

서버 GUI 창에서:

Port: 예) 7777

Start 버튼 클릭

로그 창에 Listening on 7777 같은 메시지가 보이면 준비 완료

8-3. 클라이언트 실행
두 대(또는 한 대에서 2번) ClientMain 을 Java Application 으로 실행

각 클라이언트의 StartFrame 에서:

Host: 서버 IP (같은 PC면 127.0.0.1)

Port: 서버 포트 (예: 7777)

Name: 원하는 닉네임

Connect / Start 버튼 클릭 → RoomFrame 이동

두 명이 모두 접속하면:

ROOM_STATUS, ROOM_CREATED, ENTER_ROOM 메시지를 따라

자동으로 GameRoomFrame 으로 전환

9. 팀 정보
👤 이경석 – 2171073

👤 김준현 – 2171062

10. 요약
이 프로젝트는 TCP 소켓 + Swing GUI 를 이용한 2인용 러시안 룰렛 게임입니다.

현재:

서버/클라이언트 구조

2인 매칭 로비

게임방 화면

채팅 기능
이 동작하도록 구현되어 있습니다.

앞으로:

턴/HP/탄창/승패 를 Room 중심으로 추가하면,

실제 러시안 룰렛 룰(SELF+BLANK 턴 유지)이 완성됩니다.
 
