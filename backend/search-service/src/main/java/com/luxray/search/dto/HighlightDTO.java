package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class HighlightDTO {

    private Map<String, List<String>> fields;

    public static HighlightDTO of(Map<String, List<String>> fields) {
        HighlightDTO dto = new HighlightDTO();
        dto.fields = fields;
        return dto;
    }
}