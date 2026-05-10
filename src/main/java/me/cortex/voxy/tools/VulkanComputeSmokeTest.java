package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.vulkan.VulkanLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * M8: Vulkan equivalent of M7 — dispatch the increment.comp compute shader,
 * bind a HOST_VISIBLE SSBO via descriptor sets, and confirm every value
 * received its per-invocation expected output.
 *
 * Run with {@code ./gradlew testVulkanCompute}.
 */
public final class VulkanComputeSmokeTest {

    private static final int N = 64;

    private VulkanComputeSmokeTest() {}

    public static void main(String[] args) throws Exception {
        if (!VulkanLoader.load()) {
            System.err.println("MoltenVK failed to load");
            System.exit(2);
        }

        Path shadersRoot = Path.of("src/main/resources/assets/voxy/shaders/tools").toAbsolutePath();
        String compGlsl = Files.readString(shadersRoot.resolve("increment.comp"), StandardCharsets.UTF_8);
        byte[] compSpirv = RuntimeShaderCompiler.compile(compGlsl,
                RuntimeShaderCompiler.Stage.COMPUTE, Map.of(),
                RuntimeShaderCompiler.Target.VULKAN_SPIRV).spirv();
        System.out.println("Compiled increment.comp -> " + compSpirv.length + " B SPIRV");

        try (MemoryStack outer = MemoryStack.stackPush()) {
            VkInstance instance = createInstance(outer);
            try {
                VkPhysicalDevice physical = pickPhysicalDevice(outer, instance);
                int queueFamily = findComputeQueueFamily(outer, physical);
                VkDevice device = createDevice(outer, physical, queueFamily);
                try {
                    PointerBuffer queueHandle = outer.callocPointer(1);
                    VK10.vkGetDeviceQueue(device, queueFamily, 0, queueHandle);
                    VkQueue queue = new VkQueue(queueHandle.get(0), device);

                    long ssboBuffer = 0, ssboMemory = 0;
                    long descriptorSetLayout = 0, descriptorPool = 0;
                    long pipelineLayout = 0, pipeline = 0;
                    long compModule = 0;
                    long cmdPool = 0;
                    VkCommandBuffer cmdBuf = null;
                    long fence = 0;
                    try {
                        long bufferSize = (long) N * Integer.BYTES;
                        long[] bufPair = createSsboBuffer(outer, device, physical, bufferSize);
                        ssboBuffer = bufPair[0];
                        ssboMemory = bufPair[1];

                        descriptorSetLayout = createDescriptorSetLayout(outer, device);
                        descriptorPool = createDescriptorPool(outer, device);
                        long descriptorSet = allocateDescriptorSet(outer, device, descriptorPool, descriptorSetLayout);
                        updateDescriptorSet(outer, device, descriptorSet, ssboBuffer, bufferSize);

                        compModule = createShaderModule(outer, device, compSpirv);

                        long[] pipelinePair = createComputePipeline(outer, device, compModule, descriptorSetLayout);
                        pipelineLayout = pipelinePair[0];
                        pipeline = pipelinePair[1];

                        cmdPool = createCommandPool(outer, device, queueFamily);
                        cmdBuf = allocateCommandBuffer(outer, device, cmdPool);
                        fence = createFence(outer, device);

                        recordCommands(outer, cmdBuf, pipeline, pipelineLayout, descriptorSet);
                        submitAndWait(outer, queue, cmdBuf, fence, device);

                        verifyValues(device, ssboMemory);

                        System.out.println();
                        System.out.println("M8 SMOKE OK — Vulkan compute dispatch wrote correct values on MoltenVK");
                    } finally {
                        if (fence != 0) VK10.vkDestroyFence(device, fence, null);
                        if (cmdBuf != null && cmdPool != 0) VK10.vkFreeCommandBuffers(device, cmdPool, cmdBuf);
                        if (cmdPool != 0) VK10.vkDestroyCommandPool(device, cmdPool, null);
                        if (pipeline != 0) VK10.vkDestroyPipeline(device, pipeline, null);
                        if (pipelineLayout != 0) VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
                        if (compModule != 0) VK10.vkDestroyShaderModule(device, compModule, null);
                        if (descriptorPool != 0) VK10.vkDestroyDescriptorPool(device, descriptorPool, null);
                        if (descriptorSetLayout != 0) VK10.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
                        if (ssboMemory != 0) VK10.vkFreeMemory(device, ssboMemory, null);
                        if (ssboBuffer != 0) VK10.vkDestroyBuffer(device, ssboBuffer, null);
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
                .pApplicationName(stack.UTF8("voxy-vulkan-compute"))
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

    private static int findComputeQueueFamily(MemoryStack stack, VkPhysicalDevice device) {
        IntBuffer count = stack.callocInt(1);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
        VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);
        for (int i = 0; i < families.capacity(); i++) {
            if ((families.get(i).queueFlags() & VK10.VK_QUEUE_COMPUTE_BIT) != 0) return i;
        }
        throw new RuntimeException("No compute queue family");
    }

    private static VkDevice createDevice(MemoryStack stack, VkPhysicalDevice physical, int queueFamily) {
        VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamily)
                .pQueuePriorities(stack.floats(1.0f));
        VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queues);
        PointerBuffer handle = stack.callocPointer(1);
        check("vkCreateDevice", VK10.vkCreateDevice(physical, info, null, handle));
        return new VkDevice(handle.get(0), physical, info);
    }

    private static long[] createSsboBuffer(MemoryStack stack, VkDevice device, VkPhysicalDevice physical, long size) {
        VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
        LongBuffer bufferHandle = stack.callocLong(1);
        check("vkCreateBuffer", VK10.vkCreateBuffer(device, info, null, bufferHandle));
        long buffer = bufferHandle.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        VK10.vkGetBufferMemoryRequirements(device, buffer, memReq);
        int memType = findMemoryType(stack, physical, memReq.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(memType);
        LongBuffer memoryHandle = stack.callocLong(1);
        check("vkAllocateMemory(ssbo)", VK10.vkAllocateMemory(device, allocInfo, null, memoryHandle));
        long memory = memoryHandle.get(0);
        check("vkBindBufferMemory", VK10.vkBindBufferMemory(device, buffer, memory, 0));
        return new long[]{buffer, memory};
    }

    private static int findMemoryType(MemoryStack stack, VkPhysicalDevice device, int typeBits, int properties) {
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceMemoryProperties(device, memProps);
        for (int i = 0; i < memProps.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0
                    && (memProps.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }
        throw new RuntimeException("No memory type matches typeBits=" + typeBits + " properties=" + properties);
    }

    private static long createDescriptorSetLayout(MemoryStack stack, VkDevice device) {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack)
                .binding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
        VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateDescriptorSetLayout", VK10.vkCreateDescriptorSetLayout(device, info, null, handle));
        return handle.get(0);
    }

    private static long createDescriptorPool(MemoryStack stack, VkDevice device) {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1);
        VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pPoolSizes(poolSizes)
                .maxSets(1);
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateDescriptorPool", VK10.vkCreateDescriptorPool(device, info, null, handle));
        return handle.get(0);
    }

    private static long allocateDescriptorSet(MemoryStack stack, VkDevice device, long pool, long layout) {
        VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(pool)
                .pSetLayouts(stack.longs(layout));
        LongBuffer handle = stack.callocLong(1);
        check("vkAllocateDescriptorSets", VK10.vkAllocateDescriptorSets(device, info, handle));
        return handle.get(0);
    }

    private static void updateDescriptorSet(MemoryStack stack, VkDevice device,
                                             long descriptorSet, long buffer, long size) {
        VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(buffer).offset(0).range(size);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorCount(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .pBufferInfo(bufferInfo);
        VK10.vkUpdateDescriptorSets(device, writes, null);
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

    private static long[] createComputePipeline(MemoryStack stack, VkDevice device,
                                                 long compModule, long descriptorSetLayout) {
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer layoutHandle = stack.callocLong(1);
        check("vkCreatePipelineLayout", VK10.vkCreatePipelineLayout(device, layoutInfo, null, layoutHandle));
        long pipelineLayout = layoutHandle.get(0);

        VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                .module(compModule)
                .pName(stack.UTF8("main"));

        VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                .stage(stage)
                .layout(pipelineLayout);
        LongBuffer pipelineHandle = stack.callocLong(1);
        check("vkCreateComputePipelines",
                VK10.vkCreateComputePipelines(device, VK10.VK_NULL_HANDLE, pipelineInfo, null, pipelineHandle));
        return new long[]{pipelineLayout, pipelineHandle.get(0)};
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
                .commandPool(pool)
                .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
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

    private static void recordCommands(MemoryStack stack, VkCommandBuffer cmdBuf,
                                        long pipeline, long pipelineLayout, long descriptorSet) {
        VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        check("vkBeginCommandBuffer", VK10.vkBeginCommandBuffer(cmdBuf, begin));

        VK10.vkCmdBindPipeline(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        VK10.vkCmdBindDescriptorSets(cmdBuf, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout,
                0, stack.longs(descriptorSet), null);
        VK10.vkCmdDispatch(cmdBuf, 1, 1, 1);

        check("vkEndCommandBuffer", VK10.vkEndCommandBuffer(cmdBuf));
    }

    private static void submitAndWait(MemoryStack stack, VkQueue queue, VkCommandBuffer cmdBuf,
                                       long fence, VkDevice device) {
        VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(cmdBuf));
        check("vkQueueSubmit", VK10.vkQueueSubmit(queue, submit, fence));
        check("vkWaitForFences", VK10.vkWaitForFences(device, fence, true, Long.MAX_VALUE));
    }

    private static void verifyValues(VkDevice device, long ssboMemory) {
        long size = (long) N * Integer.BYTES;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer mapped = stack.callocPointer(1);
            check("vkMapMemory", VK10.vkMapMemory(device, ssboMemory, 0, size, 0, mapped));
            try {
                ByteBuffer bytes = MemoryUtil.memByteBuffer(mapped.get(0), (int) size).order(ByteOrder.LITTLE_ENDIAN);
                IntBuffer values = bytes.asIntBuffer();
                int passed = 0, mismatched = 0;
                for (int i = 0; i < N; i++) {
                    int expected = i * 2 + 1;
                    int actual = values.get(i);
                    if (actual == expected) passed++;
                    else {
                        if (mismatched < 4) {
                            System.out.printf("  [%d] expected=%d actual=%d%n", i, expected, actual);
                        }
                        mismatched++;
                    }
                }
                System.out.printf("Compute results: %d/%d match%n", passed, N);
                if (passed != N) throw new RuntimeException("M8 FAILED: " + mismatched + " mismatches");
            } finally {
                VK10.vkUnmapMemory(device, ssboMemory);
            }
        }
    }

    private static void check(String op, int rc) {
        if (rc != VK10.VK_SUCCESS) throw new RuntimeException(op + " failed: rc=" + rc);
    }
}
