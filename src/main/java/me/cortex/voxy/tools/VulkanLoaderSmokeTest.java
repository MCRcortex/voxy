package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.vulkan.VulkanLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.nio.IntBuffer;

/**
 * M4 Stage A: verify {@link VulkanLoader} can dispatch into MoltenVK,
 * create a VkInstance, and enumerate the system's physical devices.
 * Run with {@code ./gradlew testVulkanLoader}.
 */
public final class VulkanLoaderSmokeTest {

    private VulkanLoaderSmokeTest() {}

    public static void main(String[] args) {
        if (!VulkanLoader.load()) {
            System.err.println("MoltenVK failed to load");
            System.exit(2);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("voxy-vulkan-loader-smoke-test"))
                    .applicationVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8("voxy"))
                    .engineVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(VK10.VK_MAKE_VERSION(1, 2, 0));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            // LWJGL's lwjgl-vulkan natives ship a bundled MoltenVK that's loaded
            // directly without the Vulkan loader, so portability_enumeration
            // (a loader-only extension) is not needed.

            PointerBuffer instanceHandle = stack.callocPointer(1);
            int rc = VK10.vkCreateInstance(createInfo, null, instanceHandle);
            if (rc != VK10.VK_SUCCESS) {
                throw new RuntimeException("vkCreateInstance failed: rc=" + rc);
            }
            VkInstance instance = new VkInstance(instanceHandle.get(0), createInfo);
            System.out.println("VkInstance created (apiVersion=Vulkan 1.2 with portability)");

            try {
                IntBuffer count = stack.callocInt(1);
                rc = VK10.vkEnumeratePhysicalDevices(instance, count, null);
                if (rc != VK10.VK_SUCCESS) throw new RuntimeException("vkEnumeratePhysicalDevices count: rc=" + rc);
                int deviceCount = count.get(0);
                System.out.println("Physical device count: " + deviceCount);
                if (deviceCount == 0) throw new RuntimeException("No Vulkan devices found");

                PointerBuffer devices = stack.callocPointer(deviceCount);
                rc = VK10.vkEnumeratePhysicalDevices(instance, count, devices);
                if (rc != VK10.VK_SUCCESS) throw new RuntimeException("vkEnumeratePhysicalDevices fetch: rc=" + rc);

                for (int i = 0; i < deviceCount; i++) {
                    VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);
                    VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                    VK10.vkGetPhysicalDeviceProperties(device, props);
                    String name = props.deviceNameString();
                    int apiVersion = props.apiVersion();
                    System.out.printf("  [%d] %s (api %d.%d.%d)%n", i, name,
                            (apiVersion >> 22) & 0x7F,
                            (apiVersion >> 12) & 0x3FF,
                            apiVersion & 0xFFF);
                }

                System.out.println();
                System.out.println("M4 Stage A OK — MoltenVK loads, VkInstance + VkPhysicalDevice work");
            } finally {
                VK10.vkDestroyInstance(instance, null);
            }
        }
    }
}
