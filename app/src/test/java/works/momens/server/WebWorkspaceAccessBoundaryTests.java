package works.momens.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * {@code web} 모듈의 워크스페이스 권한 확인 경계를 검증합니다(MOM-0919).
 *
 * <p>{@code web} 모듈이 {@code WorkspaceAccess}를 직접 참조하면 조합 서비스마다 권한 판정과 실패 응답을 개별적으로 구현하게 됩니다. 그 결과
 * {@code docs/spec/api-response-error-codes.md}의 「권한 details」 절을 따르지 않는 응답이 다시 발생할 수 있습니다. 이 규칙은
 * 컴파일러가 검증하지 않으므로 직접 참조가 남아 있으면 CI가 실패하도록 이 테스트에서 확인합니다.
 *
 * <p>검증 대상은 main 소스입니다. 통합 테스트에서 데이터를 준비하기 위해 {@code WorkspaceAccess}를 사용하는 것까지 제한할 필요는 없으므로 {@code
 * DO_NOT_INCLUDE_TESTS}로 테스트 클래스를 제외합니다.
 *
 * <p>이 규칙은 {@code web} 모듈에만 적용합니다. {@code mobile}, {@code signal}, {@code memory} 모듈은 아직 동일한 응답 형식을
 * 사용하지 않으며, 해당 모듈의 정리는 별도 작업에서 다룹니다.
 */
class WebWorkspaceAccessBoundaryTests {

  private static final String WEB_PACKAGE = "works.momens.server.web";

  private final JavaClasses webClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(WEB_PACKAGE);

  @Test
  void webDoesNotDependOnWorkspaceAccess() {
    noClasses()
        .that()
        .resideInAPackage(WEB_PACKAGE + "..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(WorkspaceAccess.class)
        .check(webClasses);
  }
}
