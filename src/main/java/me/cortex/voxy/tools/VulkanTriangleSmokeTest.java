package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.vulkan.VulkanLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * M6: Vulkan equivalent of M5 — compile the triangle shaders to SPIRV via
 * {@link RuntimeShaderCompiler}, build a Vulkan graphics pipeline against
 * dynamic_rendering, draw the 3-vertex triangle into a 256x256 RGBA8 image,
 * read pixels back, and confirm the corners stay clear while the interior
 * was rasterized.
 *
 * Run with {@code ./gradlew testVulkanTriangle}.
 */
public final class VulkanTriangleSmokeTest {

    private static final int W = 256;
    private static final int H = 256;
    private static final int FORMAT = VK10.VK_FORMAT_R8G8B8A8_UNORM;

    private VulkanTriangleSmokeTest() {}

    public static void main(String[] args) throws Exception {
        if (!VulkanLoader.load()) {
            System.err.println("MoltenVK failed to load");
            System.exit(2);
        }

        Path shadersRoot = Path.of("src/main/resources/assets/voxy/shaders/tools").toAbsolutePath();
        String vertGlsl = Files.readString(shadersRoot.resolve("triangle.vert"), StandardCharsets.UTF_8);
        String fragGlsl = Files.readString(shadersRoot.resolve("triangle.frag"), StandardCharsets.UTF_8);

        byte[] vertSpirv = RuntimeShaderCompiler.compile(vertGlsl,
                RuntimeShaderCompiler.Stage.VERTEX, Map.of(),
                RuntimeShaderCompiler.Target.VULKAN_SPIRV).spirv();
        byte[] fragSpirv = RuntimeShaderCompiler.compile(fragGlsl,
                RuntimeShaderCompiler.Stage.FRAGMENT, Map.of(),
                RuntimeShaderCompiler.Target.VULKAN_SPIRV).spirv();
        System.out.println("Compiled SPIRV — vert=" + vertSpirv.length + "B frag=" + fragSpirv.length + "B");

        try (MemoryStack outer = MemoryStack.stackPush()) {
            VkInstance instance = createInstance(outer);
            try {
                VkPhysicalDevice physical = pickPhysicalDevice(outer, instance);
                int queueFamily = findGraphicsQueueFamily(outer, physical);
                VkDevice device = createDevice(outer, physical, queueFamily);
                try {
                    PointerBuffer queueHandle = outer.callocPointer(1);
                    VK10.vkGetDeviceQueue(device, queueFamily, 0, queueHandle);
                    VkQueue queue = new VkQueue(queueHandle.get(0), device);

                    long image = 0, imageMemory = 0, imageView = 0;
                    long readbackBuffer = 0, readbackMemory = 0;
                    long cmdPool = 0;
                    VkCommandBuffer cmdBuf = null;
                    long fence = 0;
                    long vertModule = 0, fragModule = 0;
                    long pipelineLayout = 0, pipeline = 0;
                    try {
                        long[] imgPair = createImage(outer, device, physical);
                        image = imgPair[0]; imageMemory = imgPair[1];
                        imageView = createImageView(outer, device, image);

                        long[] bufPair = createReadbackBuffer(outer, device, physical, (long) W * H * 4);
                        readbackBuffer = bufPair[0]; readbackMemory = bufPair[1];

                        cmdPool = createCommandPool(outer, device, queueFamily);
                        cmdBuf = allocateCommandBuffer(outer, device, cmdPool);
                        fence = createFence(outer, device);

                        vertModule = createShaderModule(outer, device, vertSpirv);
                        fragModule = createShaderModule(outer, device, fragSpirv);

                        long[] pipelinePair = createGraphicsPipeline(outer, device, vertModule, fragModule);
                        pipelineLayout = pipelinePair[0];
                        pipeline = pipelinePair[1];

                        recordCommands(outer, cmdBuf, image, imageView, pipeline, readbackBuffer);
                        submitAndWait(outer, queue, cmdBuf, fence, device);

                        verifyTriangle(device, readbackMemory);

                        System.out.println();
                        System.out.println("M6 SMOKE OK — Vulkan triangle rendered via dynamic_rendering on MoltenVK");
                    } finally {
                        if (pipeline != 0) VK10.vkDestroyPipeline(device, pipeline, null);
                        if (pipelineLayout != 0) VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
                        if (fragModule != 0) VK10.vkDestroyShaderModule(device, fragModule, null);
                        if (vertModule != 0) VK10.vkDestroyShaderModule(device, vertModule, null);
                        if (fence != 0) VK10.vkDestroyFence(device, fence, null);
                        if (cmdBuf != null && cmdPool != 0) VK10.vkFreeCommandBuffers(device, cmdPool, cmdBuf);
                        if (cmdPool != 0) VK10.vkDestroyCommandPool(device, cmdPool, null);
                        if (readbackMemory != 0) VK10.vkFreeMemory(device, readbackMemory, null);
                        if (readbackBuffer != 0) VK10.vkDestroyBuffer(device, readbackBuffer, null);
                        if (imageView != 0) VK10.vkDestroyImageView(device, imageView, null);
                        if (imageMemory != 0) VK10.vkFreeMemory(device, imageMemory, null);
                        if (image != 0) VK10.vkDestroyImage(device, image, null);
                    }
                } finally {
                    VK10.vkDestroyDevice(device, null);
                }
            } finally {
                VK10.vkDestroyInstance(instance, null);
            }
        }
    }

    private static VkInstance createInstance(MemoryStack stack) {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("voxy-vulkan-triangle"))
                .applicationVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                .pEngineName(stack.UTF8("voxy"))
                .engineVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                .apiVersion(VK10.VK_MAKE_VERSION(1, 2, 0));
        VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo);
        PointerBuffer handle = stack.callocPointer(1);
        check("vkCreateInstance", VK10.vkCreateInstance(info, null, handle));
        return new VkInstance(handle.get(0), info);
    }

    private static VkPhysicalDevice pickPhysicalDevice(MemoryStack stack, VkInstance instance) {
        IntBuffer count = stack.callocInt(1);
        check("vkEnumeratePhysicalDevices count", VK10.vkEnumeratePhysicalDevices(instance, count, null));
        PointerBuffer devices = stack.callocPointer(count.get(0));
        check("vkEnumeratePhysicalDevices fetch", VK10.vkEnumeratePhysicalDevices(instance, count, devices));
        VkPhysicalDevice picked = new VkPhysicalDevice(devices.get(0), instance);
        VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceProperties(picked, props);
        System.out.println("Physical device: " + props.deviceNameString());
        return picked;
    }

    private static int findGraphicsQueueFamily(MemoryStack stack, VkPhysicalDevice device) {
        IntBuffer count = stack.callocInt(1);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
        VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);
        for (int i = 0; i < families.capacity(); i++) {
            if ((families.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) return i;
        }
        throw new RuntimeException("No graphics queue family");
    }

    private static VkDevice createDevice(MemoryStack stack, VkPhysicalDevice physical, int queueFamily) {
        VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamily)
                .pQueuePriorities(stack.floats(1.0f));
        VkPhysicalDeviceDynamicRenderingFeatures dynRender = VkPhysicalDeviceDynamicRenderingFeatures.calloc(stack)
                .sType(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES)
                .dynamicRendering(true);
        PointerBuffer extensions = stack.callocPointer(1);
        extensions.put(0, stack.UTF8(KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME));
        VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queues)
                .ppEnabledExtensionNames(extensions)
                .pNext(dynRender.address());
        PointerBuffer handle = stack.callocPointer(1);
        check("vkCreateDevice", VK10.vkCreateDevice(physical, info, null, handle));
        return new VkDevice(handle.get(0), physical, info);
    }

    private static long[] createImage(MemoryStack stack, VkDevice device, VkPhysicalDevice physical) {
        VkImageCreateInfo info = VkImageCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .format(FORMAT)
                .extent(VkExtent3D.calloc(stack).set(W, H, 1))
                .mipLevels(1).arrayLayers(1)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                .usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        LongBuffer imageHandle = stack.callocLong(1);
        check("vkCreateImage", VK10.vkCreateImage(device, info, null, imageHandle));
        long image = imageHandle.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        VK10.vkGetImageMemoryRequirements(device, image, memReq);
        int memTypeIndex = findMemoryType(stack, physical, memReq.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(memTypeIndex);
        LongBuffer memoryHandle = stack.callocLong(1);
        check("vkAllocateMemory(image)", VK10.vkAllocateMemory(device, allocInfo, null, memoryHandle));
        long memory = memoryHandle.get(0);
        check("vkBindImageMemory", VK10.vkBindImageMemory(device, image, memory, 0));
        return new long[]{image, memory};
    }

    private static long createImageView(MemoryStack stack, VkDevice device, long image) {
        VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(FORMAT)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateImageView", VK10.vkCreateImageView(device, info, null, handle));
        return handle.get(0);
    }

    private static long[] createReadbackBuffer(MemoryStack stack, VkDevice device, VkPhysicalDevice physical, long size) {
        VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size).usage(VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
        LongBuffer bufferHandle = stack.callocLong(1);
        check("vkCreateBuffer", VK10.vkCreateBuffer(device, info, null, bufferHandle));
        long buffer = bufferHandle.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        VK10.vkGetBufferMemoryRequirements(device, buffer, memReq);
        int memTypeIndex = findMemoryType(stack, physical, memReq.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size()).memoryTypeIndex(memTypeIndex);
        LongBuffer memoryHandle = stack.callocLong(1);
        check("vkAllocateMemory(buffer)", VK10.vkAllocateMemory(device, allocInfo, null, memoryHandle));
        long memory = memoryHandle.get(0);
        check("vkBindBufferMemory", VK10.vkBindBufferMemory(device, buffer, memory, 0));
        return new long[]{buffer, memory};
    }

    private static int findMemoryType(MemoryStack stack, VkPhysicalDevice device, int typeBits, int requiredProperties) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceMemoryProperties(device, memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0
                    && (memProps.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties) {
                return i;
            }
        }
        throw new RuntimeException("No memory type matches typeBits=" + typeBits + " required=" + requiredProperties);
    }

    private static long createCommandPool(MemoryStack stack, VkDevice device, int queueFamily) {
        VkCommandPoolCreateInfo info = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamily);
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateCommandPool", VK10.vkCreateCommandPool(device, info, null, handle));
        return handle.get(0);
    }

    private static VkCommandBuffer allocateCommandBuffer(MemoryStack stack, VkDevice device, long pool) {
        VkCommandBufferAllocateInfo info = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(pool).level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
        PointerBuffer handle = stack.callocPointer(1);
        check("vkAllocateCommandBuffers", VK10.vkAllocateCommandBuffers(device, info, handle));
        return new VkCommandBuffer(handle.get(0), device);
    }

    private static long createFence(MemoryStack stack, VkDevice device) {
        VkFenceCreateInfo info = VkFenceCreateInfo.calloc(stack).sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateFence", VK10.vkCreateFence(device, info, null, handle));
        return handle.get(0);
    }

    private static long createShaderModule(MemoryStack stack, VkDevice device, byte[] spirv) {
        ByteBuffer codeBuffer = MemoryUtil.memAlloc(spirv.length);
        codeBuffer.put(spirv).flip();
        try {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(codeBuffer);
            LongBuffer handle = stack.callocLong(1);
            check("vkCreateShaderModule", VK10.vkCreateShaderModule(device, info, null, handle));
            return handle.get(0);
        } finally {
            MemoryUtil.memFree(codeBuffer);
        }
    }

    private static long[] createGraphicsPipeline(MemoryStack stack, VkDevice device,
                                                  long vertModule, long fragModule) {
        VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
        stages.get(0)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertModule)
                .pName(stack.UTF8("main"));
        stages.get(1)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragModule)
                .pName(stack.UTF8("main"));

        VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

        VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);

        VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1);

        VkPipelineRasterizationStateCreateInfo rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                .cullMode(VK10.VK_CULL_MODE_NONE)
                .frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
                .lineWidth(1.0f);

        VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

        VkPipelineColorBlendAttachmentState.Buffer blendAttachments = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                .blendEnable(false)
                .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                        | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);

        VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .pAttachments(blendAttachments);

        IntBuffer dynamicStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR);
        VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(dynamicStates);

        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
        LongBuffer layoutHandle = stack.callocLong(1);
        check("vkCreatePipelineLayout", VK10.vkCreatePipelineLayout(device, layoutInfo, null, layoutHandle));
        long pipelineLayout = layoutHandle.get(0);

        IntBuffer colorFormats = stack.ints(FORMAT);
        VkPipelineRenderingCreateInfo renderingInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                .sType(VK13.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
                .pColorAttachmentFormats(colorFormats);

        VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pNext(renderingInfo.address())
                .pStages(stages)
                .pVertexInputState(vertexInput)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewport)
                .pRasterizationState(rasterization)
                .pMultisampleState(multisample)
                .pColorBlendState(colorBlend)
                .pDynamicState(dynamic)
                .layout(pipelineLayout);

        LongBuffer pipelineHandle = stack.callocLong(1);
        check("vkCreateGraphicsPipelines",
                VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, pipelineInfo, null, pipelineHandle));
        return new long[]{pipelineLayout, pipelineHandle.get(0)};
    }

    private static void recordCommands(MemoryStack stack, VkCommandBuffer cmdBuf,
                                        long image, long imageView, long pipeline, long readbackBuffer) {
        VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        check("vkBeginCommandBuffer", VK10.vkBeginCommandBuffer(cmdBuf, begin));

        imageBarrier(stack, cmdBuf, image,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                0, VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

        VkClearValue clearValue = VkClearValue.calloc(stack);
        VkClearColorValue clearColor = clearValue.color();
        clearColor.float32(0, 0.1f); clearColor.float32(1, 0.1f);
        clearColor.float32(2, 0.15f); clearColor.float32(3, 1.0f);

        VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                .imageView(imageView)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);

        VkRect2D renderArea = VkRect2D.calloc(stack);
        renderArea.extent(VkExtent2D.calloc(stack).set(W, H));

        VkRenderingInfo renderInfo = VkRenderingInfo.calloc(stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR)
                .renderArea(renderArea)
                .layerCount(1)
                .pColorAttachments(colorAttachments);
        KHRDynamicRendering.vkCmdBeginRenderingKHR(cmdBuf, renderInfo);

        VkViewport.Buffer viewports = VkViewport.calloc(1, stack)
                .x(0).y(0).width((float) W).height((float) H).minDepth(0).maxDepth(1);
        VK10.vkCmdSetViewport(cmdBuf, 0, viewports);

        VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
        scissors.get(0).extent(VkExtent2D.calloc(stack).set(W, H));
        VK10.vkCmdSetScissor(cmdBuf, 0, scissors);

        VK10.vkCmdBindPipeline(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        VK10.vkCmdDraw(cmdBuf, 3, 1, 0, 0);

        KHRDynamicRendering.vkCmdEndRenderingKHR(cmdBuf);

        imageBarrier(stack, cmdBuf, image,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                .imageSubresource(VkImageSubresourceLayers.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .imageExtent(VkExtent3D.calloc(stack).set(W, H, 1));
        VK10.vkCmdCopyImageToBuffer(cmdBuf, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                readbackBuffer, region);

        check("vkEndCommandBuffer", VK10.vkEndCommandBuffer(cmdBuf));
    }

    private static void imageBarrier(MemoryStack stack, VkCommandBuffer cmdBuf, long image,
                                      int oldLayout, int newLayout,
                                      int srcAccess, int dstAccess,
                                      int srcStage, int dstStage) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(srcAccess).dstAccessMask(dstAccess)
                .oldLayout(oldLayout).newLayout(newLayout)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
        VK10.vkCmdPipelineBarrier(cmdBuf, srcStage, dstStage, 0, null, null, barrier);
    }

    private static void submitAndWait(MemoryStack stack, VkQueue queue, VkCommandBuffer cmdBuf,
                                       long fence, VkDevice device) {
        VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(cmdBuf));
        check("vkQueueSubmit", VK10.vkQueueSubmit(queue, submit, fence));
        check("vkWaitForFences", VK10.vkWaitForFences(device, fence, true, Long.MAX_VALUE));
    }

    private static void verifyTriangle(VkDevice device, long readbackMemory) {
        long size = (long) W * H * 4;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer mapped = stack.callocPointer(1);
            check("vkMapMemory", VK10.vkMapMemory(device, readbackMemory, 0, size, 0, mapped));
            try {
                ByteBuffer bytes = MemoryUtil.memByteBuffer(mapped.get(0), (int) size).order(ByteOrder.LITTLE_ENDIAN);

                int clearR = 26, clearG = 26, clearB = 38, clearA = 255;  // (0.1, 0.1, 0.15, 1.0) * 255
                int topLeft = sampleRgba(bytes, 4, 4);
                int bottomRight = sampleRgba(bytes, W - 4, H - 4);
                int interior = sampleRgba(bytes, W / 2, H / 2);

                int clearPacked = pack(clearR, clearG, clearB, clearA);
                System.out.printf("  Top-left   pixel: 0x%08X (clear=0x%08X) %s%n",
                        topLeft, clearPacked, topLeft == clearPacked ? "OK" : "MISMATCH");
                System.out.printf("  Bot-right  pixel: 0x%08X (clear=0x%08X) %s%n",
                        bottomRight, clearPacked, bottomRight == clearPacked ? "OK" : "MISMATCH");
                System.out.printf("  Interior   pixel: 0x%08X (clear=0x%08X) %s%n",
                        interior, clearPacked, interior != clearPacked ? "DIFFERENT (triangle drew)" : "STILL CLEAR");

                if (topLeft != clearPacked || bottomRight != clearPacked) {
                    throw new RuntimeException("Background corners changed");
                }
                if (interior == clearPacked) {
                    throw new RuntimeException("Triangle interior unchanged — pipeline didn't rasterize");
                }
            } finally {
                VK10.vkUnmapMemory(device, readbackMemory);
            }
        }
    }

    private static int sampleRgba(ByteBuffer bytes, int x, int y) {
        int offset = (y * W + x) * 4;
        return pack(bytes.get(offset) & 0xFF, bytes.get(offset + 1) & 0xFF,
                bytes.get(offset + 2) & 0xFF, bytes.get(offset + 3) & 0xFF);
    }

    private static int pack(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    private static void check(String op, int rc) {
        if (rc != VK10.VK_SUCCESS) throw new RuntimeException(op + " failed: rc=" + rc);
    }
}
