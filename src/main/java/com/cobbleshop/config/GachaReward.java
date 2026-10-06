package com.cobbleshop.config;

/**
 * 뽑기 보상 하나의 정보. 아이템 보상과 명령어 보상 중 하나(또는 둘 다)를 가진다.
 *
 * @param name      채팅과 확률표에 표시되는 이름
 * @param itemId    지급할 아이템 ID. 없으면 빈 문자열
 * @param count     지급할 아이템 개수
 * @param command   서버가 실행할 명령어. {player}는 플레이어 이름으로 바뀜. 없으면 빈 문자열
 * @param iconId    확률표/결과 칸에 보여줄 아이콘 아이템 ID
 * @param weight    가중치. 클수록 잘 나온다
 * @param broadcast true면 당첨 시 서버 전체에 공지
 */
public record GachaReward(String name, String itemId, int count, String command,
                          String iconId, int weight, boolean broadcast) {
}
