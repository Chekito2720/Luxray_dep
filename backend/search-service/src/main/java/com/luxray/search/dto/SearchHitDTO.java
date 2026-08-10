package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchHitDTO<T> {

    private String id;
    private float score;
    private T source;
    private HighlightDTO highlight;

    public static <T> SearchHitDTO<T> of(String id, float score, T source, HighlightDTO highlight) {
        SearchHitDTO<T> dto = new SearchHitDTO<>();
        dto.id = id;
        dto.score = score;
        dto.source = source;
        dto.highlight = highlight;
        return dto;
    }
}