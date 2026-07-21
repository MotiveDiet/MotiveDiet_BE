package com.example.motivediet_be.service;

import com.example.motivediet_be.domain.FavoriteFood;
import com.example.motivediet_be.domain.FoodCategory;
import com.example.motivediet_be.domain.FoodLog;
import com.example.motivediet_be.domain.MotiveSignal;
import com.example.motivediet_be.dto.FavoriteFoodResponse;
import com.example.motivediet_be.dto.HomeResponse;
import com.example.motivediet_be.repository.FavoriteFoodRepository;
import com.example.motivediet_be.repository.FoodCategoryRepository;
import com.example.motivediet_be.repository.FoodLogRepository;
import com.example.motivediet_be.repository.MotiveSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 대시보드(화면 1b)에 필요한 모든 데이터를 한 응답으로 묶는다.
 * 스트릭/주간 카운트는 별도 테이블 없이 FoodLog를 즉석 집계한다.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final MotiveSignalRepository motiveSignalRepository;
    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodLogRepository foodLogRepository;

    public HomeResponse home(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        Set<LocalDate> logDates = Set.copyOf(foodLogRepository.findLogDates(userId));
        Map<Long, Long> weeklyCountByCategory = foodLogRepository
                .findByUserIdAndLoggedAtGreaterThanEqual(userId, weekStart.atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(FoodLog::getFoodCategoryId, Collectors.counting()));

        return new HomeResponse(
                today,
                motiveSignal(userId, today),
                weekStreak(weekStart, logDates),
                currentStreak(logDates, today),
                favoriteFoods(userId, weeklyCountByCategory),
                FavoriteFoodService.SLOT_CAPACITY);
    }

    private HomeResponse.MotiveSignalResponse motiveSignal(Long userId, LocalDate today) {
        MotiveSignal signal = motiveSignalRepository.findFirstByUserIdOrderByIdDesc(userId).orElse(null);
        if (signal == null) {
            return null;
        }
        LocalDate eventDate = signal.getEventDate();
        // 날짜가 이미 지난 이벤트는 칩을 숨긴다. 날짜가 없는 동기는 카운트다운 없이 노출한다.
        if (eventDate != null && eventDate.isBefore(today)) {
            return null;
        }
        Integer daysUntil = eventDate == null ? null : (int) ChronoUnit.DAYS.between(today, eventDate);
        return new HomeResponse.MotiveSignalResponse(signal.getMotiveType().emoji(), signal.getTarget(), daysUntil);
    }

    private List<HomeResponse.DayStreak> weekStreak(LocalDate weekStart, Set<LocalDate> logDates) {
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(weekStart::plusDays)
                .map(date -> new HomeResponse.DayStreak(
                        date, date.getDayOfWeek().name().substring(0, 3), logDates.contains(date)))
                .toList();
    }

    private List<FavoriteFoodResponse> favoriteFoods(Long userId, Map<Long, Long> weeklyCountByCategory) {
        Map<Long, FoodCategory> categories = foodCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(FoodCategory::getId, Function.identity()));

        return favoriteFoodRepository.findByUserIdOrderBySlotOrder(userId).stream()
                .map(favorite -> {
                    FoodCategory category = categories.get(favorite.getFoodCategoryId());
                    return new FavoriteFoodResponse(
                            favorite.getId(),
                            favorite.getFoodCategoryId(),
                            category.getName(),
                            category.getEmoji(),
                            weeklyCountByCategory.getOrDefault(favorite.getFoodCategoryId(), 0L),
                            category.getWeeklyThreshold(),
                            favorite.getSlotOrder());
                })
                .toList();
    }

    /**
     * 오늘부터 거꾸로 세되, 오늘이 아직 비어 있어도 스트릭을 끊지 않고 어제부터 센다.
     * 순수 함수 — 테스트 대상.
     */
    static int currentStreak(Set<LocalDate> logDates, LocalDate today) {
        LocalDate cursor = logDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (logDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
