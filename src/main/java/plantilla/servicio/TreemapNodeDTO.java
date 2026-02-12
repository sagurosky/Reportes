package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for treemap visualization - hierarchical data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreemapNodeDTO {
    private String label;
    private Long value;
    private List<TreemapNodeDTO> children;

    public TreemapNodeDTO(String label, Long value) {
        this.label = label;
        this.value = value;
        this.children = new ArrayList<>();
    }
}
