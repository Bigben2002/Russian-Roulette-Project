title: "Java Network Russian Roulette Game (Final Version)"
description: >
  Java Socket과 Swing을 활용하여 개발한 1:1 네트워크 대전 러시안 룰렛 게임입니다.
  서버-클라이언트 구조, 커스텀 프로토콜, 스윙 GUI 커스텀 렌더링(회전 애니메이션),
  전략적 아이템 시스템이 모두 구현된 최종 프로젝트 명세서입니다.

sections:
  - id: "1-project-overview"
    title: "1. 프로젝트 개요 (Project Overview)"
    content: |-
      ### 🔫 프로젝트 소개
      단순한 텍스트 통신을 넘어, **실시간 그래픽 인터페이스(GUI)**와 **전략적 아이템 시스템**을 도입하여
      완성도 높은 1:1 턴제 네트워크 게임을 구현했습니다.

      - **개발 언어**: Java JDK 21
      - **핵심 기술**:
        - `java.net.Socket` (TCP/IP 통신)
        - `javax.swing` (GUI & Custom Painting)
        - `Multi-threading` (클라이언트 요청 병렬 처리)
      - **개발 인원**: 1인 (Full Stack)

  - id: "2-game-rules"
    title: "2. 게임 규칙 및 시스템 (Game Rules)"
    content: |-
      ### 🎲 기본 룰
      1. **1:1 대전**: 두 명의 플레이어(P1, P2)가 접속하여 게임을 진행합니다.
      2. **승리 조건**: 상대방의 **HP(5칸)**를 먼저 0으로 만들면 승리합니다.
      3. **실린더**: 6발의 슬롯 중 무작위로 **실탄(Live)**과 **공탄(Blank)**이 배치됩니다.

      ### ⚔️ 턴(Turn) 전략
      자신의 턴에 다음 행동 중 하나를 수행합니다.
      - **조준(Aim)**: 방향키(↑, ↓)로 `SELF`(자신) 또는 `ENEMY`(상대)를 조준합니다.
      - **아이템(Item)**: 숫자키(1~6)로 아이템을 사용하여 변수를 창출합니다.
      - **발사(Fire)**: `Space Bar`로 격발합니다.

      > **💡 핵심 전략 (Turn Retention)**
      > - **실탄 발사**: 데미지를 입히고 턴 종료.
      > - **공탄 + 상대 조준**: 데미지 없이 턴 종료.
      > - **공탄 + 자신 조준**: 데미지 없이 **턴 유지 (한 번 더 행동 가능)**.

      ### 🎒 아이템 (Items)
      - **💊 Heal (H)**: HP를 1 회복합니다. (Max 5)
      - **🔍 Search (S)**: 현재 발사될 탄환이 실탄인지 공탄인지 몰래 확인합니다.
      - **💣 Bomb (B)**: 이번 턴에 실탄 발사 시 **데미지 2배(2칸)**를 입힙니다.

  - id: "3-project-structure"
    title: "3. 프로젝트 전체 구조 (Project Structure)"
    content: |-
      ### 📂 파일 구조도

      ```text
      src/
       ├─ 📂 server/             # [Server] 게임 로직의 심장
       │   ├─ ServerGuiMain.java   # 서버 프로그램 진입점 (GUI)
       │   ├─ ServerFrame.java     # 서버 제어 및 로그창
       │   ├─ ServerCore.java      # 클라이언트 연결 수락 및 스레드 관리
       │   ├─ Room.java            # ★ 게임 로직 (턴, 확률, 아이템, 승패)
       │   ├─ ClientHandler.java   # 클라이언트별 통신 담당
       │   └─ Protocol.java        # 통신 규약 상수 정의
       │
       └─ 📂 client/             # [Client] 유저 인터페이스
           ├─ ClientMain.java      # 클라이언트 진입점
           ├─ StartFrame.java      # 접속 정보 입력 (IP/Port/Name)
           ├─ RoomFrame.java       # 대기실 (Lobby & Ready)
           ├─ GameRoomFrame.java   # ★ 메인 게임 화면 (입력 및 렌더링)
           ├─ RoomCanvas.java      # (Inner) 그래픽 드로잉 & 애니메이션
           ├─ NetworkClient.java   # 서버 송수신 전담
           └─ ImageLoader.java     # 리소스 로딩 유틸
      resources/
       └─ images/                # 배경, 플레이어, 아이템 PNG 파일들
      ```

  - id: "4-execution-flow"
    title: "4. 게임 실행 흐름 (Execution Flow)"
    content: |-
      ### 🚀 실행 시나리오

      1. **서버 구동 (`ServerGuiMain`)**
         - 포트(7777)를 열고 `AcceptLoop` 스레드가 대기합니다.

      2. **접속 및 매칭 (`ClientMain`)**
         - 클라이언트 1, 2가 접속하면 핸드셰이크(HELLO) 후 `Waiting` 상태가 됩니다.
         - 2명이 모이면 서버가 `Room`을 생성하고 `ENTER_ROOM`을 방송합니다.

      3. **게임 시작 (`GameRoomFrame`)**
         - 양쪽이 `READY`를 누르면 서버가 실탄을 섞고(`Random`) `GAME_START` 패킷을 보냅니다.

      4. **인게임 루프 (Sync)**
         - **Server**: `TURN`, `RELOAD`, `HP` 정보를 계속 방송합니다.
         - **Client**: 사용자의 키 입력을 받아 `AIM`, `USE_ITEM`, `FIRE` 패킷을 보냅니다.
         - **Server (`Room`)**: 요청을 검증하고 결과를 계산(`onFire`)한 뒤 `FIRE_RESOLVE`로 결과를 뿌립니다.

      5. **게임 종료**
         - HP가 0이 되면 `GAME_OVER` 패킷과 함께 승자를 알리고 게임이 끝납니다.

  - id: "5-key-code-details"
    title: "5. 주요 코드 상세 (Key Implementation)"
    content: |-
      ### 🛠 Server: `Room.java`
      - **역할**: 게임의 룰을 관장하는 심판.
      - **주요 로직**:
        - `randomizeCylinder()`: 6발 중 실탄/공탄 랜덤 배치.
        - `onFire()`: 발사 요청 시 실탄 여부 확인, 데미지 계산, **폭탄 아이템 적용 여부**, **턴 넘김 규칙**을 처리합니다.
        - `broadcast()`: 접속한 두 클라이언트에게 동시에 상태를 전송합니다.

      ### 🎨 Client: `GameRoomFrame.java`
      - **역할**: 사용자의 입력을 받고 화면을 그립니다.
      - **주요 로직**:
        - `RoomCanvas.paintComponent()`: 배경, 캐릭터, 아이템 슬롯을 그립니다.
        - `Animation`: `Graphics2D.rotate()`를 사용하여 현재 턴인 플레이어의 총구가 부드럽게 돌아가는 애니메이션을 구현했습니다.
        - `Socket Listener`: 서버에서 오는 메시지를 비동기로 받아 UI를 즉시 갱신합니다.

  - id: "6-checklist"
    title: "6. 구현 기능 체크리스트 (Checklist)"
    content: |-
      - [x] **기본 네트워크**: TCP Socket 연결, Multi-threading
      - [x] **프로토콜**: 명령어 기반 문자열 프로토콜 설계 및 파싱
      - [x] **로비 시스템**: 대기방, 닉네임 표시, 레디(Ready) 기능
      - [x] **게임 로직**: 실탄/공탄 확률, 체력, 데미지 구현
      - [x] **전략 요소**: 조준 변경(Self/Enemy), 턴 유지 규칙
      - [x] **아이템**: Heal, Search, Bomb 3종 구현 완료
      - [x] **그래픽(GUI)**: Swing Custom Painting, 이미지 렌더링
      - [x] **UX**: 총기 회전 애니메이션, 채팅 시스템, 사운드(예정)
      - [ ] 
