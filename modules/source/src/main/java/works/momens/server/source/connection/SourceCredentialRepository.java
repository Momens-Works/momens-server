package works.momens.server.source.connection;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code source_credentials} 테이블의 조회와 저장을 담당합니다.
 *
 * <p>연결 식별자가 기본 키이므로 별도의 조회 메서드를 선언하지 않습니다.
 */
public interface SourceCredentialRepository extends JpaRepository<SourceCredential, UUID> {}
