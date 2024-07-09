#version 460 core
//TODO: make this better than a single thread
layout(local_size_x=1, local_size_y=1) in;

//The queue contains 3 atomics
// end (the current processing pointer)
// head (the current point that is ok to read from)
// top (An atomic that is only used for writing to)
//The way it works when enqueuing
// top is incremented by x,
//   write the data getting enqueued at the starting point specified by the `top` incrmenet
// then increment head strictly _AFTER_ writing to the queue, this ensures that the data is always written and avaible in the queue

layout(binding = 0, std140) uniform SceneUniform {

};

layout(binding = 1, std430) restrict buffer NodeData {//Needs to be read and writeable for marking data,
    //(could do an evil violation, make this readonly, then have a writeonly varient, which means that writing might not be visible but will show up by the next frame)
    //Nodes are 16 bytes big (or 32 cant decide, 16 might _just_ be enough)
    ivec4[] nodes;
};


layout(binding = 2, std430) restrict buffer Atomics {
    uint requestQueueIndex;
    uint requestQueueMaxSize;

    uint renderQueueIndex;
    uint renderQueueMaxSize;
} atomics;

layout(binding = 3, std430) restrict writeonly buffer RequestQueue {
    uint[] requestQueue;
};

layout(binding = 4, std430) restrict writeonly buffer RenderQueue {
    uint[] renderQueue;
};

/*
layout(binding = 2, std430) restrict buffer QueueData {
    uint tail;
    uint head;
    uint top;
    uint[] queue;
} queue;
*/

#import <voxy:lod/hierarchial/transform.glsl>

#import <voxy:lod/hierarchial/node.glsl>

layout(binding = 0) uniform sampler2DShadow hizDepthSampler;


void aqcuireNewBatch() {

}

//Contains all the screenspace computation
#import <voxy:lod/hierarchial/screenspace.glsl>





//If a request is successfully added to the RequestQueue, must update NodeData to mark that the node has been put into the request queue
// to prevent it from being requested every frame and blocking the queue


//Once a suitable render section is found, it is put into the RenderQueue, or if its not availbe its put into the RequestQueue
// and its children are rendered instead if it has them avalible

//NOTE: EXPERIMENT: INSTEAD OF PERSISTENT THREADS
//TODO: since we know the tree depth is worst case 5, we can just do an indirect dispatch 5 times one for each layer
// issues with this approach, barriers and waiting for one to finish before the otehr can be executed
// advantages, MUCH SIMPLER, no shader barriers needed really , issue is need a flipflip queue but thats ok,
// also ensures the gpu is full of work capacity
// this might be what i do to start with since its much easier to do
// not sure


void addRequest() {
    if (!hasRequested()) {
        //TODO: request this node (cpu side can figure out what it wants/needs)

        //Mark node as having a request submitted to prevent duplicate submissions
    }
}

void enqueueSelfForRender() {
    //TODO: Draw mesh and stop with this node (good path)
}

void enqueueChildren() {

}

//TODO: need to add an empty mesh, as a parent node might not have anything to render but the children do??
void main() {
    uint id = 0;

    //Setup/unpack the node
    unpackNode(id);
    //TODO: check the node is OK first??? maybe?

    //Compute screenspace
    setupScreenspace();

    if (isCulledByHiz()) {
        //We are done here, dont do any more, the issue is the shader barriers maybe
        // its culled, maybe just mark it as culled?
    } else {
        //It is visible, TODO: maybe do a more detailed hiz test? (or make it so that )

        if (shouldDecend()) {
            if (hasChildren()) {
                enqueueChildren();
            } else {
                addRequest();
                //TODO: use self mesh (is error state if it doesnt have one since all leaf nodes should have a mesh)
                // Basicly guarenteed to have a mesh, if it doesnt it is very very bad and incorect since its a violation of the graph properties
                // that all leaf nodes must contain a mesh
                enqueueSelfForRender();
            }
        } else {
            if (hasMesh()) {
                enqueueSelfForRender();
            } else {
                //!! not ideal, we want to render this mesh but dont have it. If we havent sent a request
                // then send a request for a mesh for this node.
                addRequest();

                //TODO: Decend into children? maybe add a bitflag saying is bad if the immediate children dont have meshes
                enqueueChildren();
            }
        }
    }
}


/*
//Thread 0 grabs a batch when empty
void main() {
    while (true) {
        //Each thread processes an entry on the queue and pushes all children to the queue if it is determined the children need to be added
    }
}
*/