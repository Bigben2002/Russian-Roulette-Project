# Russian-Roulette-Project
한성대학교 3학년 2학기 네트워크프로그래밍 프로젝트  
팀원  
- **이경석 – 2171073** (Server Architecture & Core Logic)  
- **김준현 – 2171062** (Client GUI & Item Features)

---

# 🎯 Russian Roulette — 2인 네트워크 게임 (Final Phase)

---

## 🧱 프로젝트 개요

```yaml
Project:
  Name: "러시안 룰렛 2인 네트워크 게임"
  Environment:
    - Java 17 ~ 21
    - Eclipse IDE
    - IntelliJ IDEA
  Technologies:
    - Swing GUI
    - TCP Socket (ServerSocket / Socket)
    - UTF-8 Stream
  Network Structure:
    Server: 1
    Clients: 2
    Connection: LAN 기반 (동일 Wi-Fi 환경)
Note:
  - 같은 Wi-Fi 환경에서 서버 IP/포트를 입력하면 2명이 접속하여 1:1 러시안 룰렛 전투 진행
🧩 프로젝트 목표 및 현황
🎉 Final Phase (최종 완료)
yaml
코드 복사
Completed Features:
  - 2인 매칭 / 코인토스 / 턴 전환
  - 6칸 탄창 랜덤 생성 (실탄·공탄)
  - 조준(AIM): SELF | ENEMY
  - 발사(FIRE): HP 감소 및 턴 교대
  - 아이템 시스템:
      - Heal
      - Search
      - Bomb
  - 리볼버 회전 HUD 및 라이프 UI
  - 서버 기준 HP/턴/탄창/아이템 동기화
Cancelled Features:
  - Sound Effects (범위 조정으로 제외)
📁 시스템 구조
전체 구조 (Styled Directory Tree)
text
코드 복사
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
✨ 핵심 기능 정리
🎮 전투 규칙
yaml
코드 복사
Combat Rules:
  SELF + BLANK: "아무 일 없음", Turn 유지
  SELF + BULLET: "HP -1 또는 -2(폭탄)", Turn 교대
  ENEMY + BLANK: "아무 일 없음", Turn 교대
  ENEMY + BULLET: "적 HP -1 또는 -2", Turn 교대
HP:
  Start: 5
  Death: HP <= 0
Chamber:
  Size: 6
  Reload: 모든 발 사용 시 자동 재장전
🎁 아이템 시스템
yaml
코드 복사
Items:
  Heal (H):
    Effect: HP +1 (최대 5)
    Note: HP가 이미 5면 사용 불가
  Search (S):
    Effect: 다음 탄이 BULLET/BLANK인지 확인
    Send-To: 사용자에게만 알려줌
  Bomb (B):
    Effect: 다음 실탄 명중 시 2배 피해
    One-Time: 발동 후 효과 사라짐
Slots:
  Max: 6
  Value: ["H", "S", "B", "-"] 형태로 유지
🖥️ 실행 방법
서버 실행
yaml
코드 복사
Run Server:
  1: 실행 → server.ServerGuiMain
  2: 포트 입력 (ex: 7777)
  3: Start 클릭
Log Example:
  - "[Server] Listening on 7777"
클라이언트 실행
yaml
코드 복사
Run Client:
  - client.ClientMain 실행 (2번)
Connect Info:
  Host: 서버 IP (동일 PC는 127.0.0.1)
  Port: 7777
  Name: 사용자가 입력
Flow:
  StartFrame → RoomFrame(대기방) → GameRoomFrame(게임 시작)
🕹 조작 키
yaml
코드 복사
Controls:
  Up/Down: AIM (조준 변경)
  Space: FIRE (내 턴일 때만)
  1~6: 아이템 사용
  Chat: 채팅창 열기
  X: EXIT_ROOM 후 종료
📡 통신 흐름 (요약)
yaml
코드 복사
NetworkFlow:
  HELLO:
    Server -> Client: "HELLO"
    Client -> Server: PlayerName
  READY:
    Client -> Server: READY
    Server -> All: READY_STATE
  GAME_START:
    Server -> All: HP/Bullet/Item/Turn 초기 상태
  AIM:
    Client -> Server: AIM SELF | ENEMY
    Server -> All: AIM_UPDATE
  FIRE:
    Client -> Server: FIRE
    Server:
      - 탄 판정
      - HP 계산
      - 턴 결정
    Server -> All: FIRE_RESOLVE
  ITEM:
    Client -> Server: USE_ITEM SLOT=n
    Server -> All: ITEM_UPDATE or PEEK_RESULT
  EXIT:
    Client -> Server: EXIT_ROOM
    Server -> Other: 상대방 퇴장 안내
✅ 구현 현황 체크리스트
yaml
코드 복사
Server/Client:
  - TCP 통신 안정화
  - HELLO/READY/GAME_START 동기화
Game:
  - 턴 시스템 완성
  - HP/탄창/조준 상태 동기화
  - 발사 판정 완성
Items:
  - Heal/Search/Bomb 완전 구현
UI:
  - 라이프 이미지
  - 아이템 HUD
  - 총 회전 방향 반영
Misc:
  - 채팅 완성
  - 퇴장 처리(EXI_ROOM)
👨‍💻 개발자 코멘트
yaml
코드 복사
Comment:
  Summary: >
    본 프로젝트는 Socket 네트워크 통신 + Swing GUI + 턴제 게임 구조를
    동시에 학습하기 위한 목적으로 제작되었습니다.
    서버(Room)가 모든 상태를 관리하는 Single Source of Truth 구조를 채택하여
    클라이언트는 UI만 처리하고, 모든 판정은 서버에서 이루어지는 안정적인 구조입니다.
  Future:
    - 멀티룸 확장
    - 리플레이/전투 로그 기능
    - UX 개선
