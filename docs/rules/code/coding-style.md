# 코딩 스타일

포맷은 도구로 강제하고, 도구가 못 잡는 명명·관용은 아래 규칙을 따릅니다.

## 포맷

- **Spotless + Google Java Format** (2-space, 100칸)으로 강제합니다.
- 커밋 전 `./gradlew spotlessApply`로 정렬하고, CI가 `./gradlew spotlessCheck`로 검사합니다.
- 에디터 설정은 `.editorconfig`를 따릅니다.
- GJF가 자동 처리하므로 따로 신경 쓰지 않아도 되는 것: 와일드카드 import 금지, 모든
  제어문 중괄호, 연산자 공백, 배열 Java식(`String[]`).

## 네이밍

- 클래스: `XxxController` / `XxxService` / `XxxRepository`, 엔티티 `User`,
  DTO `XxxRequest` / `XxxResponse`.
- 메서드: 조회 `getX`·`findXById`·`findXList`, 생성 `createX`·`saveX`,
  수정 `updateX`·`modifyX`, 삭제 `deleteX`·`removeX`, 검증 `validateX`·`checkXExists`.
- 변수: camelCase. boolean 은 `isActive`·`hasPermission`. 컬렉션은 복수형(`users`).
- 상수: `UPPER_SNAKE_CASE`.

## Lombok

- 허용: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`,
  `@Builder`, `@Slf4j`.
- 지양: `@Data`, 무분별한 `@Setter`.

## 기타

- `switch` 에는 `default` 를 둡니다.
- 불리언은 직접 평가합니다: `if (isActive)` (O), `if (isActive == true)` (X).
