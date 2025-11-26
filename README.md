title: "Java Network Russian Roulette - Final Version"
description: >
  Java Socket과 Swing Custom Painting을 활용하여 개발한 1:1 네트워크 전략 대전 게임.
  텍스트 기반 프로토타입에서 시작하여, 실시간 애니메이션과 아이템 전략 시스템이 완벽하게 구현된
  최종 프로젝트의 구조 및 실행 흐름 명세서입니다.

sections:
  - id: "1-project-overview"
    title: "1. 프로젝트 개요 & 팀원 (Overview)"
    content: |-
      ### 🎯 프로젝트 소개
      단순히 텍스트만 주고받는 소켓 통신을 넘어, **`Graphics2D`를 활용한 회전 애니메이션**과 **서버 권한(Server-Authoritative) 기반의 상태 동기화**를 구현한 실시간 네트워크 게임입니다.

      - **개발 환경**: Java JDK 21, Eclipse IDE
      - **핵심 기술**: TCP Socket, Multi-threading, Swing GUI (Custom Component)

      ### 👥 개발 팀원
      | 이름 | 학번 | 담당 역할 |
      |:---:|:---:|:---|
      | **이경석** | 2171073 | **Server Logic** (스레드 관리, 룰 판정), **Client GUI** (애니메이션, 렌더링) |
      | **김준현** | 2171062 | **Network Protocol** (규약 설계), **Item Logic** (아이템 기능), **QA** |

  - id: "2-game-mechanics"
    title: "2. 게임 규칙 및 전략 (Game Rules)"
    content: |-
      ### 🎲 기본 승리 조건
      - 플레이어는 **HP 5**를 가지고 시작하며, 상대방의 HP를 **0**으로 만들면 승리합니다.
      - 6발의 실린더에 실탄(Bullet)과 공탄(Blank)이 랜덤하게 배치됩니다.

      ### ⚔️ 전략적 턴(Turn) 시스템
      "공탄을 자신에게 쏘면 턴이 유지된다"는 규칙을 이용한 심리전이 핵심입니다.

      | 조준(AIM) | 탄환(Result) | 결과(Effect) | 턴 전환 |
      |:---:|:---:|:---:|:---:|
      | **상대 (Enemy)** | **실탄** | 상대 HP -1 | 교대 |
      | **상대 (Enemy)** | **공탄** | (효과 없음) | 교대 |
      | **자신 (Self)** | **실탄** | 내 HP -1 | 교대 (최악의 수) |
      | **자신 (Self)** | **공탄** | (효과 없음) | **유지 (AGAIN)** |

      ### 🎒 아이템 시스템
      - **💊 Heal**: HP +1 회복.
      - **🔍 Search**: 현재 탄환이 실탄인지 공탄인지 몰래 확인.
      - **💣 Bomb**: 이번 턴에 실탄 발사 시 데미지 2배(2칸).

  - id: "3-project-structure"
    title: "3. 프로젝트 전체 구조 (Architecture)"
    content: |-
      ### 📁 폴더 구조 및 역할 정의

      #### 🖥 server/ (게임의 '두뇌')
      - **ServerGuiMain**: 서버 프로그램의 시작점. `ServerFrame` GUI를 띄웁니다.
      - **ServerFrame**: 포트 설정 및 서버 Start/Stop 제어, 로그 출력을 담당하는 UI입니다.
      - **ServerCore**: `ServerSocket`을 열고 클라이언트 연결을 수락(Accept)하여 스레드를 할당하는 엔진입니다.
      - **ClientHandler**: 접속한 클라이언트 1명당 1개씩 생성되는 스레드. 메시지를 수신하여 `Room`으로 전달합니다.
      - **Room**: **★핵심 로직**. 두 클라이언트를 묶어 게임을 진행시킵니다. (턴 관리, 데미지 계산, 아이템 처리, 승패 판정)
      - **Protocol**: `GAME_START`, `FIRE`, `AIM` 등 통신에 사용하는 명령어 상수를 모아둔 클래스입니다.

      #### 💻 client/ (게임의 '눈과 손')
      - **ClientMain**: 클라이언트 프로그램 시작점.
      - **StartFrame**: 접속 정보(IP, Port, Name)를 입력받는 로그인 창.
      - **RoomFrame**: 대기실(Lobby). 상대방 접속 여부와 Ready 상태를 보여줍니다.
      - **GameRoomFrame**: **★메인 게임 화면**. 키보드 입력을 처리하고, 서버 데이터를 받아 화면을 갱신합니다.
      - **RoomCanvas**: `JPanel`을 상속받아 배경, 캐릭터, **총기 회전 애니메이션**을 직접 그리는 캔버스입니다.
      - **NetworkClient**: 소켓 송수신을 전담하는 클래스. 서버 메시지를 받아 UI 스레드로 넘겨줍니다.
      - **ImageLoader**: 리소스 폴더의 이미지를 로딩하는 유틸리티.

  - id: "4-execution-flow"
    title: "4. 실행 흐름 시나리오 (Execution Flow)"
    content: |-
      ### 1️⃣ 서버 구동 및 대기
      1. `ServerGuiMain` 실행 → GUI에서 포트(7777) 입력 후 Start.
      2. `ServerCore`가 `acceptLoop()` 스레드를 돌며 접속을 기다림.

      ### 2️⃣ 접속 및 매칭 (Lobby)
      1. 클라이언트 1, 2 접속 → `StartFrame`에서 IP/Name 입력.
      2. 서버는 2명이 모이면 `Room` 객체를 생성하고 `ENTER_ROOM` 패킷 전송.
      3. 클라이언트들은 `RoomFrame`(대기실)에서 `GameRoomFrame`(게임방)으로 화면 전환.

      ### 3️⃣ 게임 시작 및 초기화
      1. 양쪽이 `READY` 버튼 클릭 → 서버가 `GAME_START` 방송.
      2. 서버: 실린더 랜덤 배치(`randomizeCylinder`), 아이템 지급.
      3. 클라이언트: 초기 HUD(체력, 총알 수) 설정 및 대기.

      ### 4️⃣ 인게임 루프 (동기화 과정)
      1. **[Server]** `TURN P1` 전송.
      2. **[Client P1]** 내 차례임을 인지, 총 이미지가 활성화됨.
         - 키보드 `↑/↓`: `AIM ENEMY` / `AIM SELF` 패킷 전송 → 총구 회전 애니메이션.
         - 키보드 `1~6`: `USE_ITEM` 패킷 전송.
         - 스페이스바: `FIRE` 패킷 전송.
      3. **[Server]** `FIRE` 수신 시 `Room.onFire()` 실행.
         - 실탄 여부 확인, 데미지 계산, 턴 교체 여부 판단.
         - `FIRE_RESOLVE` 패킷(결과, 남은 체력 등)을 브로드캐스트.
      4. **[Client]** 결과 수신.
         - `GameRoomFrame`이 격발 애니메이션 재생, HP 깎임, 탄환 수 감소 반영.

      ### 5️⃣ 게임 종료
      - 한 쪽 HP가 0이 되면 서버가 `GAME_OVER` 전송. 클라이언트는 승리/패배 배너 출력.

  - id: "5-code-deep-dive"
    title: "5. 주요 코드 상세 설명 (Deep Dive)"
    content: |-
      ### ServerGuiMain (서버 진입점)
      ```java
      SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
      ```
      - Swing의 스레드 안전성(Thread Safety)을 위해 **Event Dispatch Thread(EDT)**에서 GUI를 생성합니다.

      ### Room (게임의 심장)
      - 게임의 모든 **State(상태)**를 관리합니다. 클라이언트는 로직을 계산하지 않고 보여주기만 합니다.
      - **`onFire()` 메서드**: 확률 게임의 핵심입니다.
        - 공탄 + 자가 조준(Self Aim)일 경우 `turnSwaps = false`로 설정하여 턴을 유지시키는 로직이 들어있습니다.

      ### GameRoomFrame & RoomCanvas (화면 그리기)
      - **`RoomCanvas`**: `paintComponent(Graphics g)`를 오버라이드하여 구현했습니다.
      - **애니메이션 원리**:
        ```java
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(currentAngle, centerX, centerY); // 총 회전
        g2.drawImage(gunImg, ...);
        ```
        - 서버 상태에 따라 목표 각도(`targetAngle`)를 설정하고, `Timer`를 이용해 부드럽게 회전시킵니다.

      ### NetworkClient (통신)
      - 수신 스레드(`Reader`)를 별도로 분리하여 UI가 멈추지 않게 합니다.
      - 메시지가 오면 `SwingUtilities.invokeLater`를 통해 UI 스레드에게 화면 갱신을 요청합니다.

  - id: "6-checklist"
    title: "6. 개발 완료 기능 (Checklist)"
    content: |-
      - [x] **Phase-1 (Basic)**: 소켓 연결, 채팅, 러시안 룰렛 확률 로직, HP 동기화.
      - [x] **Phase-2 (Advanced)**:
        - [x] **GUI**: Swing Custom Painting으로 배경, 캐릭터, 총기 렌더링.
        - [x] **Animation**: 조준 방향 변경 시 총기 회전 연출.
        - [x] **Items**: Heal, Search, Bomb 아이템 구현 및 서버 검증 로직.
        - [x] **UX**: 턴 알림, 결과 배너, 사운드(구조 마련).
