package works.momens.server.web.source.dto.response;

import java.util.List;
import works.momens.server.source.SourceConnectionDetail;

public record SourceConnectionsResponse(List<SourceConnectionResponse> sourceConnections) {

  public static SourceConnectionsResponse from(List<SourceConnectionDetail> details) {
    return new SourceConnectionsResponse(
        details.stream().map(SourceConnectionResponse::from).toList());
  }
}
