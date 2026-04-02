package io.github.umisetokikaze.foundation.cache;

public enum CacheModuleId {
    RESOURCE_INDEX("resource_index"),
    NEGATIVE_LOOKUP("negative_lookup"),
    MODEL_JSON_PARSE("model_json_parse"),
    MODEL_PARENT_GRAPH("model_parent_graph"),
    BLOCKSTATE_EXPANSION("blockstate_expansion"),
    ATLAS_PLAN("atlas_plan");

    private final String id;

    CacheModuleId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
