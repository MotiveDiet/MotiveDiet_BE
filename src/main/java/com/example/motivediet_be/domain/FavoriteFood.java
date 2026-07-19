package com.example.motivediet_be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 즐겨찾기 음식 슬롯(0~4). 원탭 로깅 버튼 하나에 대응한다.
 */
@Entity
@Table(name = "favorite_food", uniqueConstraints =
        @UniqueConstraint(name = "UK_FAVORITE_FOOD_SLOT", columnNames = {"user_id", "slot_order"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FavoriteFood {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "food_category_id", nullable = false)
    private Long foodCategoryId;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    public void changeCategory(Long foodCategoryId) {
        this.foodCategoryId = foodCategoryId;
    }
}
