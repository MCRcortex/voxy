package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.vulkan.VulkanLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
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
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * M4 Stages B-D: stand up a minimal Vulkan render path on MoltenVK and
 * confirm a clear-color render pass writes the expected pixels.
 *
 *   Stage B: pick a physical device, create VkDevice + graphics queue with
 *            the dynamicRendering feature enabled.
 *   Stage C: allocate a 256x256 RGBA8 VkImage in DEVICE_LOCAL memory + view,
 *            plus a HOST_VISIBLE VkBuffer for CPU readback.
 *   Stage D: record one command buffer that transitions the image, runs
 *            vkCmdBeginRendering with loadOp=CLEAR, transitions for transfer,
 *            and copies into the readback buffer; submit, fence-wait, verify.
 *
 * Verbose by Vulkan standards but linear top-to-bottom — each stage's
 * boilerplate is contained in its own helper. Once this passes we know
 * the LWJGL / MoltenVK / Apple GPU stack is good for vkCmdDraw work in
 * M6 and beyond, and we can wrap it in a {@code VulkanRenderBackend
 * implements RenderBackend} when M9 needs it.
 *
 * Run with {@code ./gradlew testVulkanClear}.
 */
public final class VulkanClearSmokeTest {

    private static final int W = 256;
    private static final int H = 256;
    private static final int FORMAT = VK10.VK_FORMAT_R8G8B8A8_UNORM;
    private static final float CLEAR_R = 0.2f;
    private static final float CLEAR_G = 0.6f;
    private static final float CLEAR_B = 0.9f;
    private static final float CLEAR_A = 1.0f;

    private VulkanClearSmokeTest() {}

    public static void main(String[] args) {
        if (!VulkanLoader.load()) {
            System.err.println("MoltenVK failed to load");
            System.exit(2);
        }

        try (MemoryStack outer = MemoryStack.stackPush()) {
            VkInstance instance = createInstance(outer);
            try {
                VkPhysicalDevice physical = pickPhysicalDevice(outer, instance);
                int queueFamily = findGraphicsQueueFamily(outer, physical);
                System.out.println("Picked queue family index: " + queueFamily);

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
                    try {
                        long[] imgPair = createImage(outer, device, physical);
                        image = imgPair[0];
                        imageMemory = imgPair[1];
                        imageView = createImageView(outer, device, image);

                        long[] bufPair = createReadbackBuffer(outer, device, physical, (long) W * H * 4);
                        readbackBuffer = bufPair[0];
                        readbackMemory = bufPair[1];

                        cmdPool = createCommandPool(outer, device, queueFamily);
                        cmdBuf = allocateCommandBuffer(outer, device, cmdPool);
                        fence = createFence(outer, device);

                        recordCommands(outer, cmdBuf, image, imageView, readbackBuffer);
                        submitAndWait(outer, queue, cmdBuf, fence, device);

                        verifyPixels(device, readbackMemory);

                        System.out.println();
                        System.out.println("M4 Stages B-D OK — Vulkan clear-color pass via dynamic_rendering on MoltenVK");
                    } finally {
                        if (fence != 0) VK10.vkDestroyFence(device, fence, null);
                        if (cmdBuf != null && cmdPool != 0) {
                            VK10.vkFreeCommandBuffers(device, cmdPool, cmdBuf);
                        }
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
                .pApplicationName(stack.UTF8("voxy-vulkan-clear-smoke-test"))
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
        if (count.get(0) == 0) throw new RuntimeException("No Vulkan physical devices");
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
        throw new RuntimeException("No graphics-capable queue family");
    }

    private static VkDevice createDevice(MemoryStack stack, VkPhysicalDevice physical, int queueFamily) {
        VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamily)
                .pQueuePriorities(stack.floats(1.0f));

        VkPhysicalDeviceDynamicRenderingFeatures dynRender = VkPhysicalDeviceDynamicRenderingFeatures.calloc(stack)
                .sType(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES)
                .dynamicRendering(true);

        // dynamic_rendering moved into core 1.3 but MoltenVK exposes it through the
        // KHR extension as well. Enable both to maximize compatibility — drivers
        // that only support the extension will see it in ppEnabledExtensionNames,
        // drivers on 1.3+ will see the feature struct chained via pNext.
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
                .mipLevels(1)
                .arrayLayers(1)
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

        int memTypeIndex = findMemoryType(stack, physical, memReq.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

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
                .image(image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(FORMAT)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1));
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateImageView", VK10.vkCreateImageView(device, info, null, handle));
        return handle.get(0);
    }

    private static long[] createReadbackBuffer(MemoryStack stack, VkDevice device,
                                               VkPhysicalDevice physical, long size) {
        VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
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
                .allocationSize(memReq.size())
                .memoryTypeIndex(memTypeIndex);
        LongBuffer memoryHandle = stack.callocLong(1);
        check("vkAllocateMemory(buffer)", VK10.vkAllocateMemory(device, allocInfo, null, memoryHandle));
        long memory = memoryHandle.get(0);
        check("vkBindBufferMemory", VK10.vkBindBufferMemory(device, buffer, memory, 0));
        return new long[]{buffer, memory};
    }

    private static int findMemoryType(MemoryStack stack, VkPhysicalDevice device,
                                       int typeBits, int requiredProperties) {
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
                .commandPool(pool)
                .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
        PointerBuffer handle = stack.callocPointer(1);
        check("vkAllocateCommandBuffers", VK10.vkAllocateCommandBuffers(device, info, handle));
        return new VkCommandBuffer(handle.get(0), device);
    }

    private static long createFence(MemoryStack stack, VkDevice device) {
        VkFenceCreateInfo info = VkFenceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        LongBuffer handle = stack.callocLong(1);
        check("vkCreateFence", VK10.vkCreateFence(device, info, null, handle));
        return handle.get(0);
    }

    private static void recordCommands(MemoryStack stack, VkCommandBuffer cmdBuf,
                                        long image, long imageView, long readbackBuffer) {
        VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        check("vkBeginCommandBuffer", VK10.vkBeginCommandBuffer(cmdBuf, begin));

        // Transition image: UNDEFINED -> COLOR_ATTACHMENT_OPTIMAL for the render pass.
        imageBarrier(stack, cmdBuf, image,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                0, VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

        // Begin dynamic rendering with one color attachment + load action CLEAR.
        VkClearValue clearValue = VkClearValue.calloc(stack);
        VkClearColorValue clearColor = clearValue.color();
        clearColor.float32(0, CLEAR_R);
        clearColor.float32(1, CLEAR_G);
        clearColor.float32(2, CLEAR_B);
        clearColor.float32(3, CLEAR_A);

        VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack)
                .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                .imageView(imageView)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .resolveMode(VK10.VK_SAMPLE_COUNT_1_BIT)
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
        KHRDynamicRendering.vkCmdEndRenderingKHR(cmdBuf);

        // Transition: COLOR_ATTACHMENT_OPTIMAL -> TRANSFER_SRC_OPTIMAL for the readback copy.
        imageBarrier(stack, cmdBuf, image,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(VkImageSubresourceLayers.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(0)
                        .layerCount(1))
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
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1));
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

    private static void verifyPixels(VkDevice device, long readbackMemory) {
        long size = (long) W * H * 4;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer mapped = stack.callocPointer(1);
            check("vkMapMemory", VK10.vkMapMemory(device, readbackMemory, 0, size, 0, mapped));
            try {
                ByteBuffer bytes = MemoryUtil.memByteBuffer(mapped.get(0), (int) size).order(ByteOrder.LITTLE_ENDIAN);

                int expectedR = clamp8(CLEAR_R), expectedG = clamp8(CLEAR_G);
                int expectedB = clamp8(CLEAR_B), expectedA = clamp8(CLEAR_A);

                int[] sampleOffsets = {
                        0,
                        ((H - 1) * W + (W - 1)) * 4,
                        (H / 2 * W + W / 2) * 4
                };
                for (int off : sampleOffsets) {
                    int r = bytes.get(off) & 0xFF;
                    int g = bytes.get(off + 1) & 0xFF;
                    int b = bytes.get(off + 2) & 0xFF;
                    int a = bytes.get(off + 3) & 0xFF;
                    System.out.printf("  pixel @ %5d -> (%d, %d, %d, %d)%n", off / 4, r, g, b, a);
                    if (Math.abs(r - expectedR) > 1 || Math.abs(g - expectedG) > 1
                            || Math.abs(b - expectedB) > 1 || Math.abs(a - expectedA) > 1) {
                        throw new RuntimeException("Pixel mismatch at offset " + off
                                + ": got (" + r + "," + g + "," + b + "," + a
                                + "), expected (" + expectedR + "," + expectedG
                                + "," + expectedB + "," + expectedA + ")");
                    }
                }
            } finally {
                VK10.vkUnmapMemory(device, readbackMemory);
            }
        }
    }

    private static int clamp8(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private static void check(String op, int rc) {
        if (rc != VK10.VK_SUCCESS) throw new RuntimeException(op + " failed: rc=" + rc);
    }
}
