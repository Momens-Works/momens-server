package works.momens.server.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화.
 *
 * <p>{@code @CreatedDate}/{@code @LastModifiedDate}({@link BaseEntity})를 채웁니다. app 컴포넌트 스캔으로 자동
 * 적용되며, JPA 슬라이스 테스트에서는 직접 {@code @Import} 합니다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
