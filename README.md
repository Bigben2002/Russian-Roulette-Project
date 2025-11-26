project:
  name: "Russian Roulette - 2인 네트워크 게임"
  phase: "Phase-1 (네트워크 + 로비 + 게임방 + 채팅 / 러시안 룰렛 규칙 적용)"

overview:
  goal: >
    2명의 플레이어가 TCP 소켓으로 같은 서버에 접속해,
    러시안 룰렛 규칙(HP 5 / 6발 탄창 / SELF+BLANK 턴 유지)에 따라
    서로에게 총을 쏘며 상대 HP를 0으로 만들면 승리하는 게임을 구현한다.
  features:
    - IP/Port 입력 후 서버 GUI를 통해 방 생성 및 2인 매칭
    - 로비(대기실)와 실제 게임 방 화면 분리
    - 서버 주도 매칭 / 프로토콜 기반 메시지 통신
    - 게임방에서의 채팅 기능 (지연 최소화, 최근 N줄만 유지)
    - 배경/캐릭터 이미지를 이용한 2D 연출 (위: P1, 아래: P2)

rules:
  hp:
    start_hp: 5
    win_condition: "상대 플레이어 HP를 0으로 만들면 승리"
  cylinder:
    size: 6
    description: "실탄(BULLET)과 공탄(BLANK)이 6칸 실린더에 랜덤 배치"
  turn_system:
    description: >
      플레이어는 자신의 턴에 조준 방향(자신 / 상대)을 선택하고 발사한다.
      SELF+BLANK 상황에서 턴이 유지되는 규칙을 이용한 심리전이 핵심이다.
    table:
      - target: "Enemy"
        result: "BULLET"
        effect: "상대 HP -1"
        turn_change: "교대"
      - target: "Enemy"
        result: "BLANK"
        effect: "효과 없음"
        turn_change: "교대"
      - target: "Self"
        result: "BULLET"
        effect: "내 HP -1 (최악의 선택)"
        turn_change: "교대"
      - target: "Self"
        result: "BLANK"
        effect: "효과 없음"
        turn_change: "유지 (한 번 더 진행)"

architecture:
  server_role: "게임의 '두뇌' – 룸 구성, 플레이어 연결, (확장시) 턴/HP/탄창 로직을 서버에서 판정"
  client_role: "게임의 '눈과 손' – 서버 결과를 화면에 표시하고, 키/채팅 입력을 서버로 전달"
  communication:
    type: "TCP (ServerSocket / Socket)"
    charset: "UTF-8 (BufferedReader / BufferedWriter)"
    message_style: "한 줄 단위 텍스트 프로토콜 (줄 끝에 '\\n')"

directory_tree:
  src:
    server:
      description: "서버 프로그램 및 게임 방(룸) 관리"
      files:
        - name: "ServerGuiMain.java"
          role: "서버 프로그램 진입점. ServerFrame GUI를 띄운다."
        - name: "ServerFrame.java"
          role: "포트 입력 / Start/Stop 버튼 / 로그 영역을 가진 서버 제어 GUI."
        - name: "ServerCore.java"
          role: >
            실제 ServerSocket을 열고, 클라이언트 2명을 accept한 다음
            ClientHandler와 Room을 생성해 스레드를 시작하는 서버 엔진.
        - name: "ClientHandler.java"
          role: >
            클라이언트 1명당 1개씩 생성되는 스레드.
            클라이언트로부터 수신한 메시지(예: CHAT)를 Room에 전달하고,
            Room이 전달하는 방송 메시지를 해당 소켓으로 전송한다.
        - name: "Room.java"
          role: >
            두 명의 ClientHandler를 하나의 게임 방으로 묶는 클래스.
            현재는 ROOM_CREATED / ROOM_STATUS / ENTER_ROOM / CHAT 방송을 담당하며,
            이후 턴/HP/탄창/승패 로직을 확장할 핵심 위치.
        - name: "Protocol.java"
          role: "HELLO, ROOM_STATUS, ROOM_CREATED, ENTER_ROOM, CHAT 등 텍스트 프로토콜 상수 정의."
    client:
      description: "클라이언트 프로그램 및 GUI"
      files:
        - name: "ClientMain.java"
          role: "클라이언트 진입점. StartFrame을 띄운다."
        - name: "StartFrame.java"
          role: "Host/IP, Port, Name을 입력받고 RoomFrame으로 넘어가는 시작 화면."
        - name: "RoomFrame.java"
          role: >
            로비(대기실) 화면. 서버에서 오는 ROOM_STATUS, ROOM_CREATED, ENTER_ROOM
            메시지를 받아 2인 매칭 상태를 보여주고, 게임방으로 전환한다.
        - name: "GameRoomFrame.java"
          role: >
            실제 게임 화면. 배경/캐릭터 그림(RoomCanvas)과 상단 조작 안내, Chat 버튼을 제공하고,
            서버에서 오는 CHAT 메시지를 받아 채팅창에 출력한다.
        - name: "RoomCanvas (내부 클래스)"
          role: >
            JPanel 상속. room_bg.png, player1.png, player2.png 이미지를 로딩해
            위쪽(P1) / 아래쪽(P2) 위치에 캐릭터와 이름 라벨을 그린다.
        - name: "NetworkClient.java"
          role: >
            서버와의 연결을 담당. HELLO 핸드셰이크 후 이름을 전송하고,
            별도 스레드에서 서버의 모든 텍스트 라인을 읽어
            UI 스레드(SwingUtilities.invokeLater)로 콜백을 전달한다.
        - name: "ImageLoader.java"
          role: "리소스(/images/...)에 있는 PNG 이미지를 Classpath에서 읽어 BufferedImage로 반환."

code_flow:
  high_level:
    - "1) 서버 GUI 실행 → 포트 입력 후 서버 Start"
    - "2) 클라이언트 2대가 IP/Port/Name을 입력하고 접속"
    - "3) 서버는 두 소켓을 Room으로 묶고, ROOM_CREATED / ENTER_ROOM을 브로드캐스트"
    - "4) 클라이언트는 로비(RoomFrame)에서 게임방(GameRoomFrame)으로 전환"
    - "5) 게임방에서 Chat 버튼을 누르면 채팅 다이얼로그를 통해 메시지 송수신"
    - "6) 이후 Room 확장을 통해 턴/HP/탄창/조준/발사 로직을 추가할 예정 (서버 주도 판정)"

  server_startup:
    steps:
      - step: "ServerGuiMain.main()"
        detail: "SwingUtilities.invokeLater를 사용해 ServerFrame을 생성하고 화면에 표시."
      - step: "ServerFrame.onStart()"
        detail: "사용자가 포트를 입력 후 '방 만들기(서버 시작)' 버튼을 누르면 ServerCore.start(port)를 호출."
      - step: "ServerCore.start(port)"
        detail: >
          ServerSocket을 생성해 지정한 포트에서 listen을 시작하고,
          별도의 스레드(acceptLoop)를 띄워 클라이언트 접속을 기다린다.
      - step: "acceptLoop() – 매칭 로직"
        detail: >
          1) 첫 번째 클라이언트 접속: HELLO 전송 → 이름 수신 → ROOM_STATUS WAITING 1/2 전송
          2) 두 번째 클라이언트 접속: 동일한 절차 후 ROOM_STATUS WAITING 2/2 전송
          3) ClientHandler p1/p2와 Room을 생성하고, 핸들러에 Room을 주입한 뒤 스레드로 실행
          4) Room.announceCreatedAndReady() 호출로 모든 클라이언트에게
             ROOM_CREATED / ROOM_STATUS READY / ENTER_ROOM 방송

  client_connection:
    steps:
      - step: "ClientMain.main()"
        detail: "SwingUtilities.invokeLater로 StartFrame을 생성하고 표시."
      - step: "StartFrame.connect()"
        detail: >
          Host, Port, Name을 검증하고, 정상일 경우 RoomFrame(host,port,name)을 생성하여
          StartFrame은 dispose()로 닫는다.
      - step: "RoomFrame 생성자"
        detail: >
          1) 라벨 세팅(status, P1, P2)
          2) NetworkClient를 생성하면서 onServerLine 콜백을 등록
          3) NetworkClient.connect(host, port, name)을 호출해 서버에 연결
      - step: "NetworkClient.connect(...)"
        detail: >
          1) 소켓 생성 및 서버 연결
          2) 서버로부터 HELLO 라인을 수신 (핸드셰이크)
          3) 자신의 name을 한 줄로 전송
          4) 별도의 reader 스레드를 띄워 서버의 모든 라인을 읽고
             onLine.accept(line)을 EDT(Swing)에서 호출하도록 래핑

  lobby_to_game:
    steps:
      - step: "ROOM_STATUS 수신"
        detail: "RoomFrame.onServerLine()에서 상태 라벨을 'WAITING 1/2', 'READY 2/2' 등으로 표시."
      - step: "ROOM_CREATED 수신"
        detail: "P1=<name>, P2=<name> 값을 파싱해 p1Label, p2Label에 반영."
      - step: "ENTER_ROOM 수신"
        detail: >
          1) p1Name, p2Name이 비어있다면 다시 한 번 채움
          2) GameRoomFrame(p1Name, p2Name, myName, net)을 생성
          3) NetworkClient.setOnLine(gf.getLineConsumer())로 콜백을 게임방으로 교체
          4) GameRoomFrame을 setVisible(true)로 띄우고, RoomFrame은 dispose()로 닫는다.

  chat_flow:
    client_side:
      - "GameRoomFrame에서 Chat 버튼 클릭 → ChatDialog 생성/표시"
      - "사용자가 입력 후 Enter 또는 Send 클릭 → doSend() 호출"
      - "doSend(): 자신의 화면에 즉시 '[ME] 이름: 메시지'를 append 후, net.send('CHAT 메시지') 전송"
    server_side:
      - "ClientHandler.run(): in.readLine()으로 한 줄씩 읽기"
      - "라인이 'CHAT '으로 시작하면 메시지 부분만 추출해 Room.broadcastChat(sender, msg) 호출"
      - "Room.broadcastChat(): 두 클라이언트에게 'CHAT sender: message' 형식으로 방송"
    back_to_client:
      - "NetworkClient reader 스레드가 'CHAT ...' 수신 → onLine 콜백 호출"
      - "GameRoomFrame.getLineConsumer(): sender/role(P1/P2)를 파악해 '[P1][ME] 이름: 내용' 형식 문자열 생성"
      - "ChatDialog.appendLine()으로 채팅창에 추가하고, 너무 오래된 줄은 상단부터 삭제해 MAX 줄만 유지"

protocol:
  description: "서버와 클라이언트 사이에 오가는 텍스트 기반 명령어 집합"
  messages:
    handshake:
      - "HELLO: 서버가 클라이언트에게 최초로 보내는 인사. 클라이언트는 이름을 한 줄로 응답."
    lobby:
      - "ROOM_STATUS ... : 현재 매칭 상태 표시 (예: WAITING 1/2, READY 2/2)"
      - "ROOM_CREATED P1=<name> P2=<name> : 방이 구성되었음을 알리고 플레이어 이름 전달"
      - "ENTER_ROOM P1=<name> P2=<name> : 실제 게임방으로 입장하라는 신호"
    chat:
      - "클라이언트 → 서버: 'CHAT <텍스트>'"
      - "서버 → 클라이언트: 'CHAT <sender>: <message>'"

ui:
  start_frame:
    purpose: "접속 정보 입력 및 RoomFrame으로 진입"
    fields:
      - "Host: 서버 IP (localhost 환경이면 127.0.0.1)"
      - "Port: 서버 포트 (기본 7777)"
      - "Name: 게임 내 표시될 플레이어 이름"
  room_frame:
    purpose: "2인 매칭 상태 확인 (대기실 / 로비)"
    labels:
      - "STATUS: 현재 대기 인원 수 / READY 여부"
      - "P1: 첫 번째 접속 플레이어 이름"
      - "P2: 두 번째 접속 플레이어 이름"
    auto_transition: "ENTER_ROOM 수신 시 게임방(GameRoomFrame)으로 자동 전환"
  game_room_frame:
    purpose: "실제 플레이가 일어나는 메인 게임 화면"
    components:
      - "RoomCanvas: 상단(P1) / 하단(P2) 캐릭터 및 이름 렌더링"
      - "You Label: 현재 내가 P1인지 P2인지, 그리고 내 이름 표시"
      - "조작키 버튼: 조작 설명을 띄우는 '조작키' 버튼"
      - "Chat 버튼: 채팅 다이얼로그를 열어 대화를 주고받을 수 있음"
    future_extension:
      - "HUD(HP/탄창/턴 표시) 추가"
      - "←/→/F 키 이벤트 처리 및 서버로 AIM/FIRE 전송"
      - "탄창 소진 후 재장전, SELF+BLANK 재격발 로직 반영"

run_guide:
  requirements:
    jdk: "JDK 17 이상 (21 권장)"
    ide: "Eclipse / IntelliJ / VS Code 등 자유"
  run_server:
    - "프로젝트를 빌드 후 server 패키지의 ServerGuiMain.main()을 실행한다."
    - "ServerFrame이 뜨면 포트를 확인(기본 7777)하고 '방 만들기(서버 시작)' 버튼을 누른다."
    - "로그 영역에 '[Server] Listening on 7777'과 같은 메시지가 출력되면 대기 상태 완료."
  run_client:
    - "client 패키지의 ClientMain.main()을 실행해 StartFrame을 띄운다."
    - "Host에 서버 IP(같은 PC면 127.0.0.1), Port에 서버 포트, Name에 닉네임을 입력한다."
    - "'Start / Connect' 버튼을 누르면 RoomFrame으로 이동한다."
    - "2명이 모두 접속해 READY 상태가 되면 자동으로 GameRoomFrame으로 전환된다."

test_checklist:
  basic_connection:
    - "서버를 켜지 않은 상태에서 클라이언트를 연결하면 적절한 오류 메시지가 나오는지"
    - "서버를 켜고 클라이언트 2대를 접속했을 때 서버 로그에 P1/P2 접속 정보가 찍히는지"
    - "RoomFrame에 STATUS / P1 / P2 정보가 예상대로 표시되는지"
  room_transition:
    - "두 번째 클라이언트가 접속하면 STATUS가 READY 2/2로 변경되는지"
    - "ENTER_ROOM 수신 후 두 클라이언트 모두 GameRoomFrame으로 전환되는지"
  chat:
    - "GameRoomFrame에서 Chat 버튼을 눌러 채팅창이 정상적으로 열리는지"
    - "한쪽 클라이언트에서 메시지를 보내면, 내 화면과 상대 화면 모두에 채팅이 출력되는지"
    - "오래된 메시지가 500줄 이상 쌓이면 위쪽부터 잘려 성능 문제가 없는지"
  future_game_logic:
    - "탄창 랜덤 배치 후 6발 모두 사용하면 재장전이 되는지"
    - "Enemy/Self 조준에 따라 HP 감소 및 턴 교대 규칙이 올바르게 동작하는지"
    - "Self+Blank 상황에서 턴이 유지되는지 (전략적 재격발이 가능한지)"

notes:
  phase_status:
    description: >
      현재 업로드된 코드 기준으로는 네트워크 연결, 2인 매칭, 로비/게임방 전환,
      채팅 기능과 기본 UI가 구현되어 있다.
      HP/탄창/턴(러시안 룰렛 규칙)은 Room을 중심으로 확장 개발할 예정이며,
      이 README는 최종 규칙(HP 5, 6발 실린더, SELF+BLANK 턴 유지)을 기준으로 설계를 정리한다.
  extension_guideline:
    - "턴/HP/탄창 상태는 반드시 서버(Room)에서만 수정하고, 클라이언트는 결과만 표시하도록 설계할 것."
    - "새로운 프로토콜(예: TURN, AIM, FIRE, FIRE_RESOLVE, HEART 등)을 Protocol.java에 추가하고, 서버/클라이언트 모두에서 동일하게 해석할 것."
    - "GUI 변경 시에도 NetworkClient와 프로토콜 형식은 최대한 유지해, 네트워크 레이어와 뷰 레이어를 분리할 것."
