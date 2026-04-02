package io.github.umisetokikaze.foundation.cache;

public record ModelPipelineBundle(
        ModelJsonParseSnapshot modelJsonParse,
        ModelParentGraphSnapshot modelParentGraph,
        BlockstateExpansionSnapshot blockstateExpansion,
        AtlasPlanSnapshot atlasPlan) {
}
