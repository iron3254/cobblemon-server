# Cobblemon Server

코블몬(Cobblemon) 모드팩 서버용 **상자 GUI 상점 · 뽑기 · 플레이어 거래** 모드입니다.

- Minecraft **1.21.1** / NeoForge **21.1.x**
- 서버 쪽에서 모든 판정을 처리합니다. 화면은 바닐라 상자 창을 그대로 쓰기 때문에 **클라이언트용 그림 파일이 필요 없습니다.**

## 기능

| 기능 | 명령어 | 설명 |
|---|---|---|
| 잔액 | `/money` | 내 코인 확인 |
| 송금 | `/money pay <플레이어> <금액>` | 다른 플레이어에게 코인 보내기 |
| 상점 | `/shop <상점ID>` | 좌클릭 구매 · 우클릭 판매 · Shift = 묶음 |
| 뽑기 | `/gacha <뽑기ID>` | 좌클릭 1회 · Shift+좌클릭 10회, 확률표 표시 |
| 거래 | `/trade <플레이어>` | 상대가 채팅의 [수락]을 누르면 거래 창이 열림 |

**관리자 명령어** (권한 레벨 2 이상)

- `/money give|take|set <플레이어> <금액>`: 코인 지급, 회수, 설정
- `/shop <상점ID> <플레이어>`, `/gacha <뽑기ID> <플레이어>`: 다른 플레이어에게 창 열어주기 (NPC나 커맨드 블록에 연결할 때 사용)
- `/cobbleshop reload`: 설정 파일 다시 불러오기

### 거래 규칙
- 왼쪽 4칸 줄은 요청한 사람, 오른쪽 4칸 줄은 수락한 사람이 물건을 올리는 자리입니다.
- 둘 다 수락 버튼(양털)을 누르면 물건이 교환됩니다.
- **물건이 바뀌면 수락이 자동으로 풀립니다.** 수락한 뒤에 물건을 바꿔치기하는 사기를 막기 위해서예요.
- 창을 닫거나, 접속을 끊거나, 쓰러지면 거래가 취소되고 물건은 원래 주인에게 돌아갑니다.

## 설정 파일

서버를 처음 켜면 `config/cobbleshop/` 폴더에 기본 설정이 만들어집니다. 수정한 뒤 `/cobbleshop reload`를 실행하면 바로 적용됩니다.

### shops.json
```json
{
  "pokemart": {
    "title": "포켓마트",
    "items": [
      { "item": "cobblemon:poke_ball", "count": 1, "buy": 100, "sell": 25 },
      { "item": "cobblemon:potion", "count": 1, "buy": 150 }
    ]
  }
}
```
- `buy`나 `sell`을 빼면 해당 거래(구매/판매)가 불가능해집니다.
- 설치되지 않은 모드의 아이템은 자동으로 건너뛰고 서버 로그에 경고를 남깁니다.
- 상품이 45개를 넘으면 페이지가 나뉩니다.

### gacha.json
```json
{
  "basic": {
    "title": "기본 뽑기",
    "cost": 500,
    "rewards": [
      { "name": "몬스터볼 x5", "item": "cobblemon:poke_ball", "count": 5, "weight": 50 },
      { "name": "★ 피카츄 ★", "command": "pokegiveother {player} pikachu",
        "icon": "cobblemon:master_ball", "weight": 2, "broadcast": true }
    ]
  }
}
```
- `weight`(가중치)가 클수록 잘 나옵니다. 확률은 `내 weight ÷ 전체 weight 합`입니다.
- `command`는 서버 콘솔 권한으로 실행되고, `{player}`는 뽑은 사람 이름으로 바뀝니다. 포켓몬 지급 명령어는 사용하는 코블몬 버전에 맞게 확인하세요.
- `broadcast: true`인 보상은 서버 전체에 공지됩니다.

> ⚠️ 마인크래프트 이용 정책(EULA)에 따라 **현금으로 뽑기권이나 코인을 판매하면 안 됩니다.** 게임 안에서 번 코인으로만 운영하세요.

## 빌드하는 방법

1. [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/)를 설치합니다.
2. IntelliJ에서 이 폴더를 엽니다. (`File → Open`) Gradle 프로젝트로 자동 인식됩니다.
3. JDK 21이 없다고 나오면 IntelliJ 안내에 따라 **Temurin 21**을 다운로드합니다.
4. 오른쪽 Gradle 탭에서 `Tasks → build → build`를 실행합니다.
5. `build/libs/cobblemon-server-1.0.0.jar`가 완성된 모드입니다. 서버의 `mods` 폴더에 넣으세요.

GitHub에 올리면 **Actions 탭에서 자동으로 빌드**되고, 완성된 jar를 Artifacts에서 내려받을 수 있습니다.

## 폴더 구조

```
src/main/java/com/cobbleshop/
├── CobbleShop.java        모드 시작점, 이벤트 연결
├── command/ModCommands    명령어
├── config/                JSON 설정 읽기 (상점, 뽑기)
├── economy/               코인 저장(어태치먼트)과 계산
├── gui/ChestUiMenu        상자 창을 버튼식 GUI로 쓰는 부모 클래스
├── shop/ShopMenu          상점 창
├── gacha/GachaMenu        뽑기 창
└── trade/                 거래 요청 관리, 거래 창
```
