package com.cobbleshop.config;

import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * 뽑기 기계 하나의 정보. gacha.json의 항목 하나와 같다.
 *
 * @param title   창 제목
 * @param cost    1회 뽑기 가격
 * @param rewards 보상 목록
 */
public record GachaDefinition(String title, long cost, List<GachaReward> rewards) {

    /** 모든 보상의 가중치 합. */
    public int totalWeight() {
        int total = 0;
        for (GachaReward reward : rewards) {
            total += reward.weight();
        }
        return total;
    }

    /** 보상의 당첨 확률(%)을 계산한다. */
    public double chanceOf(GachaReward reward) {
        return reward.weight() * 100.0 / totalWeight();
    }

    /** 가중치 랜덤으로 보상 하나를 고른다. */
    public GachaReward pick(RandomSource random) {
        // 0 ~ (가중치 합 - 1) 사이 숫자를 뽑고, 앞에서부터 가중치를 빼 나가다 0 아래로 떨어지는 보상이 당첨
        int roll = random.nextInt(totalWeight());
        for (GachaReward reward : rewards) {
            roll -= reward.weight();
            if (roll < 0) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }
}
