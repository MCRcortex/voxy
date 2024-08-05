package me.cortex.voxy.client.core.rendering.hierachical2;

//Request of the leaf node to expand
class LeafExpansionRequest {
    //Child states contain micrometadata in the top bits
    // such as isEmpty, and isEmptyButEventuallyHasNonEmptyChild
    private final int[] childStates = new int[8];
}
