package pl.aiwcorpo.mcp.servicedesk.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.servicedesk.core.domain.ServicedeskRecord;
import pl.aiwcorpo.mcp.servicedesk.core.domain.RecordNotFoundException;

class ServicedeskServiceTest {

    private final Map<String, ServicedeskRecord> backend = Map.of("ABC-1", new ServicedeskRecord("ABC-1", "ABC", "First", "open"));
    private final ServicedeskService service = new ServicedeskService(id -> Optional.ofNullable(backend.get(id)));

    @Test
    void returnsTheRecordTheBackendShows() {
        assertThat(service.getRecord("ABC-1").title()).isEqualTo("First");
    }

    @Test
    void aRecordTheBackendDoesNotShowIsNotFoundOrDenied() {
        assertThatThrownBy(() -> service.getRecord("ABC-2")).isInstanceOf(RecordNotFoundException.class);
    }
}
