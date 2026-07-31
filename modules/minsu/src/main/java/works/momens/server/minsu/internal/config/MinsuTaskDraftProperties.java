package works.momens.server.minsu.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("momens.minsu.task-draft")
public record MinsuTaskDraftProperties(@DefaultValue("false") boolean enabled) {}
