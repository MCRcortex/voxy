#define SENTINAL_OUT_OF_BOUNDS uint(-1)

layout(location = NODE_QUEUE_INDEX_BINDING) uniform uint queueIdx;

layout(binding = NODE_QUEUE_META_BINDING, std430) restrict buffer NodeQueueMeta {
    uvec4 nodeQueueMetadata[MAX_ITERATIONS];
};

layout(binding = NODE_QUEUE_SOURCE_BINDING, std430) restrict readonly buffer NodeQueueSource {
    uint[] nodeQueueSource;
};

layout(binding = NODE_QUEUE_SINK_BINDING, std430) restrict writeonly buffer NodeQueueSink {
    uint[] nodeQueueSink;
};

uint getCurrentNode() {
    if (nodeQueueMetadata[queueIdx].w <= gl_GlobalInvocationID.x) {
        return SENTINAL_OUT_OF_BOUNDS;
    }
    return nodeQueueSource[gl_GlobalInvocationID.x];
}


//TODO: limit the size/writing out of bounds
uint nodePushIndex = -1;
void pushNodesInit(uint nodeCount) {
    //Debug
    #ifdef DEBUG
    if (queueIdx >= (MAX_ITERATIONS-1)) {
        printf("LOG: Traversal tried inserting a node into next iteration, which is outside max iteration bounds. GID: %d, count: %d", gl_GlobalInvocationID.x, nodeCount);
        nodePushIndex = -1;
        return;
    }
    #endif

    uint index = atomicAdd(nodeQueueMetadata[queueIdx+1].w, nodeCount);
    //Increment first metadata value if it changes threash hold
    uint inc = ((index+LOCAL_SIZE)>>LOCAL_SIZE_BITS)-(index>>LOCAL_SIZE_BITS);
    atomicAdd(nodeQueueMetadata[queueIdx+1].x, inc);//TODO: see if making this conditional on inc != 0 is faster
    nodePushIndex = index;
}

void pushNode(uint nodeId) {
    #ifdef DEBUG
    if (nodePushIndex == -1) {
        printf("LOG: Tried pushing node when push node wasnt successful. GID: %d, pushing: %d", gl_GlobalInvocationID.x, nodeId);
        return;
    }
    #endif
    nodeQueueSink[nodePushIndex++] = nodeId;
}

#define SIMPLE_QUEUE(type, name, bindingIndex) layout(binding = bindingIndex, std430) restrict buffer name##Struct { \
    type name##Index; \
    type##[] name; \
};