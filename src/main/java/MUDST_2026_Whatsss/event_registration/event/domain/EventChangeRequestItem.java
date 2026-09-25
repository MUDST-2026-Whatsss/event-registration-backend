package MUDST_2026_Whatsss.event_registration.event.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "event_change_request_items")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventChangeRequestItem {

    @Id
    @GeneratedValue
    @Column(name = "event_change_request_item_id", nullable = false, updatable = false)
    private UUID eventChangeRequestItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_change_request_id", nullable = false)
    private EventChangeRequest changeRequest;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private JsonNode oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private JsonNode newValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
