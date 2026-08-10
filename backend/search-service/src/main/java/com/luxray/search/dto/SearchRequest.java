package com.luxray.search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SearchRequest {

    private String q;
    private int page;
    private int size;
    private List<String> indices;
    private List<String> filters;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<String> getIndices() { return indices; }
    public void setIndices(List<String> indices) { this.indices = indices; }
    public List<String> getFilters() { return filters; }
    public void setFilters(List<String> filters) { this.filters = filters; }
}