package app.freerouting.autoroute.accelerator.opencl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Direct Panama FFM downcall bindings for OpenCL 1.2 C APIs (JEP 454). Requires zero third-party
 * JARs or JNI runtime extractions.
 */
public final class PanamaOpenClBindings {

  public static final long CL_SUCCESS = 0L;
  public static final long CL_DEVICE_TYPE_GPU = (1L << 2);
  public static final long CL_DEVICE_TYPE_ALL = 0xFFFFFFFFL;
  public static final int CL_DEVICE_NAME = 0x102B;
  public static final int CL_DEVICE_VENDOR = 0x102C;
  public static final int CL_DRIVER_VERSION = 0x102D;
  public static final int CL_DEVICE_MAX_COMPUTE_UNITS = 0x1002;
  public static final int CL_DEVICE_GLOBAL_MEM_SIZE = 0x101F;
  public static final int CL_DEVICE_HOST_UNIFIED_MEMORY = 0x1035;
  public static final int CL_PROGRAM_BUILD_LOG = 0x1183;

  public static final long CL_MEM_READ_WRITE = (1L << 0);
  public static final long CL_MEM_WRITE_ONLY = (1L << 1);
  public static final long CL_MEM_READ_ONLY = (1L << 2);
  public static final long CL_MEM_USE_HOST_PTR = (1L << 3);
  public static final long CL_MEM_ALLOC_HOST_PTR = (1L << 4);
  public static final long CL_MEM_COPY_HOST_PTR = (1L << 5);

  public static final int CL_TRUE = 1;
  public static final int CL_FALSE = 0;

  public static final int CL_COMPLETE = 0x0;
  public static final int CL_EVENT_COMMAND_EXECUTION_STATUS = 0x11D3;

  private final MethodHandle clGetPlatformIDsHandle;
  private final MethodHandle clGetDeviceIDsHandle;
  private final MethodHandle clGetDeviceInfoHandle;
  private final MethodHandle clCreateContextHandle;
  private final MethodHandle clCreateCommandQueueHandle;
  private final MethodHandle clCreateProgramWithSourceHandle;
  private final MethodHandle clBuildProgramHandle;
  private final MethodHandle clGetProgramBuildInfoHandle;
  private final MethodHandle clCreateKernelHandle;
  private final MethodHandle clSetKernelArgHandle;
  private final MethodHandle clCreateBufferHandle;
  private final MethodHandle clEnqueueWriteBufferHandle;
  private final MethodHandle clEnqueueReadBufferHandle;
  private final MethodHandle clEnqueueNDRangeKernelHandle;
  private final MethodHandle clWaitForEventsHandle;
  private final MethodHandle clGetEventInfoHandle;
  private final MethodHandle clReleaseMemObjectHandle;
  private final MethodHandle clReleaseKernelHandle;
  private final MethodHandle clReleaseProgramHandle;
  private final MethodHandle clReleaseCommandQueueHandle;
  private final MethodHandle clReleaseContextHandle;

  private final boolean loaded;

  public PanamaOpenClBindings() {
    SymbolLookup lookup = null;
    String os = System.getProperty("os.name", "").toLowerCase();
    Arena arena = Arena.ofAuto();
    try {
      if (os.contains("win")) {
        lookup = SymbolLookup.libraryLookup("OpenCL.dll", arena);
      } else if (os.contains("linux")) {
        lookup = SymbolLookup.libraryLookup("libOpenCL.so.1", arena);
      } else if (os.contains("mac")) {
        lookup =
            SymbolLookup.libraryLookup(
                Path.of("/System/Library/Frameworks/OpenCL.framework/OpenCL"), arena);
      }
    } catch (Throwable t) {
      // Library not found or inaccessible
    }

    if (lookup == null) {
      this.loaded = false;
      this.clGetPlatformIDsHandle = null;
      this.clGetDeviceIDsHandle = null;
      this.clGetDeviceInfoHandle = null;
      this.clCreateContextHandle = null;
      this.clCreateCommandQueueHandle = null;
      this.clCreateProgramWithSourceHandle = null;
      this.clBuildProgramHandle = null;
      this.clGetProgramBuildInfoHandle = null;
      this.clCreateKernelHandle = null;
      this.clSetKernelArgHandle = null;
      this.clCreateBufferHandle = null;
      this.clEnqueueWriteBufferHandle = null;
      this.clEnqueueReadBufferHandle = null;
      this.clEnqueueNDRangeKernelHandle = null;
      this.clWaitForEventsHandle = null;
      this.clGetEventInfoHandle = null;
      this.clReleaseMemObjectHandle = null;
      this.clReleaseKernelHandle = null;
      this.clReleaseProgramHandle = null;
      this.clReleaseCommandQueueHandle = null;
      this.clReleaseContextHandle = null;
      return;
    }

    Linker linker = Linker.nativeLinker();
    this.clGetPlatformIDsHandle =
        findHandle(
            linker,
            lookup,
            "clGetPlatformIDs",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clGetDeviceIDsHandle =
        findHandle(
            linker,
            lookup,
            "clGetDeviceIDs",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clGetDeviceInfoHandle =
        findHandle(
            linker,
            lookup,
            "clGetDeviceInfo",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clCreateContextHandle =
        findHandle(
            linker,
            lookup,
            "clCreateContext",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clCreateCommandQueueHandle =
        findHandle(
            linker,
            lookup,
            "clCreateCommandQueue",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS));
    this.clCreateProgramWithSourceHandle =
        findHandle(
            linker,
            lookup,
            "clCreateProgramWithSource",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clBuildProgramHandle =
        findHandle(
            linker,
            lookup,
            "clBuildProgram",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clGetProgramBuildInfoHandle =
        findHandle(
            linker,
            lookup,
            "clGetProgramBuildInfo",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clCreateKernelHandle =
        findHandle(
            linker,
            lookup,
            "clCreateKernel",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clSetKernelArgHandle =
        findHandle(
            linker,
            lookup,
            "clSetKernelArg",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS));
    this.clCreateBufferHandle =
        findHandle(
            linker,
            lookup,
            "clCreateBuffer",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clEnqueueWriteBufferHandle =
        findHandle(
            linker,
            lookup,
            "clEnqueueWriteBuffer",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clEnqueueReadBufferHandle =
        findHandle(
            linker,
            lookup,
            "clEnqueueReadBuffer",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clEnqueueNDRangeKernelHandle =
        findHandle(
            linker,
            lookup,
            "clEnqueueNDRangeKernel",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clWaitForEventsHandle =
        findHandle(
            linker,
            lookup,
            "clWaitForEvents",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    this.clGetEventInfoHandle =
        findHandle(
            linker,
            lookup,
            "clGetEventInfo",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    this.clReleaseMemObjectHandle =
        findHandle(
            linker,
            lookup,
            "clReleaseMemObject",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    this.clReleaseKernelHandle =
        findHandle(
            linker,
            lookup,
            "clReleaseKernel",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    this.clReleaseProgramHandle =
        findHandle(
            linker,
            lookup,
            "clReleaseProgram",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    this.clReleaseCommandQueueHandle =
        findHandle(
            linker,
            lookup,
            "clReleaseCommandQueue",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    this.clReleaseContextHandle =
        findHandle(
            linker,
            lookup,
            "clReleaseContext",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    this.loaded = (clGetPlatformIDsHandle != null && clGetDeviceIDsHandle != null);
  }

  private static MethodHandle findHandle(
      Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
    Optional<MemorySegment> symbol = lookup.find(name);
    return symbol.map(segment -> linker.downcallHandle(segment, descriptor)).orElse(null);
  }

  public boolean isLoaded() {
    return this.loaded;
  }

  public int clGetPlatformIDs(int numEntries, MemorySegment platforms, MemorySegment numPlatforms)
      throws Throwable {
    return (int) clGetPlatformIDsHandle.invokeExact(numEntries, platforms, numPlatforms);
  }

  public int clGetDeviceIDs(
      MemorySegment platform,
      long deviceType,
      int numEntries,
      MemorySegment devices,
      MemorySegment numDevices)
      throws Throwable {
    return (int)
        clGetDeviceIDsHandle.invokeExact(platform, deviceType, numEntries, devices, numDevices);
  }

  public int clGetDeviceInfo(
      MemorySegment device,
      int paramName,
      long paramValueSize,
      MemorySegment paramValue,
      MemorySegment paramValueSizeRet)
      throws Throwable {
    return (int)
        clGetDeviceInfoHandle.invokeExact(
            device, paramName, paramValueSize, paramValue, paramValueSizeRet);
  }

  public MemorySegment clCreateContext(
      MemorySegment properties,
      int numDevices,
      MemorySegment devices,
      MemorySegment pfnNotify,
      MemorySegment userData,
      MemorySegment errcodeRet)
      throws Throwable {
    return (MemorySegment)
        clCreateContextHandle.invokeExact(
            properties, numDevices, devices, pfnNotify, userData, errcodeRet);
  }

  public MemorySegment clCreateCommandQueue(
      MemorySegment context, MemorySegment device, long properties, MemorySegment errcodeRet)
      throws Throwable {
    return (MemorySegment)
        clCreateCommandQueueHandle.invokeExact(context, device, properties, errcodeRet);
  }

  public MemorySegment clCreateProgramWithSource(
      MemorySegment context,
      int count,
      MemorySegment strings,
      MemorySegment lengths,
      MemorySegment errcodeRet)
      throws Throwable {
    return (MemorySegment)
        clCreateProgramWithSourceHandle.invokeExact(context, count, strings, lengths, errcodeRet);
  }

  public int clBuildProgram(
      MemorySegment program,
      int numDevices,
      MemorySegment deviceList,
      MemorySegment options,
      MemorySegment pfnNotify,
      MemorySegment userData)
      throws Throwable {
    return (int)
        clBuildProgramHandle.invokeExact(
            program, numDevices, deviceList, options, pfnNotify, userData);
  }

  public MemorySegment clCreateKernel(
      MemorySegment program, MemorySegment kernelName, MemorySegment errcodeRet) throws Throwable {
    return (MemorySegment) clCreateKernelHandle.invokeExact(program, kernelName, errcodeRet);
  }

  public int clSetKernelArg(
      MemorySegment kernel, int argIndex, long argSize, MemorySegment argValue) throws Throwable {
    return (int) clSetKernelArgHandle.invokeExact(kernel, argIndex, argSize, argValue);
  }

  public MemorySegment clCreateBuffer(
      MemorySegment context, long flags, long size, MemorySegment hostPtr, MemorySegment errcodeRet)
      throws Throwable {
    return (MemorySegment)
        clCreateBufferHandle.invokeExact(context, flags, size, hostPtr, errcodeRet);
  }

  public int clEnqueueWriteBuffer(
      MemorySegment commandQueue,
      MemorySegment buffer,
      int blockingWrite,
      long offset,
      long cb,
      MemorySegment ptr,
      int numEventsInWaitList,
      MemorySegment eventWaitList,
      MemorySegment event)
      throws Throwable {
    return (int)
        clEnqueueWriteBufferHandle.invokeExact(
            commandQueue,
            buffer,
            blockingWrite,
            offset,
            cb,
            ptr,
            numEventsInWaitList,
            eventWaitList,
            event);
  }

  public int clEnqueueReadBuffer(
      MemorySegment commandQueue,
      MemorySegment buffer,
      int blockingRead,
      long offset,
      long cb,
      MemorySegment ptr,
      int numEventsInWaitList,
      MemorySegment eventWaitList,
      MemorySegment event)
      throws Throwable {
    return (int)
        clEnqueueReadBufferHandle.invokeExact(
            commandQueue,
            buffer,
            blockingRead,
            offset,
            cb,
            ptr,
            numEventsInWaitList,
            eventWaitList,
            event);
  }

  public int clEnqueueNDRangeKernel(
      MemorySegment commandQueue,
      MemorySegment kernel,
      int workDim,
      MemorySegment globalWorkOffset,
      MemorySegment globalWorkSize,
      MemorySegment localWorkSize,
      int numEventsInWaitList,
      MemorySegment eventWaitList,
      MemorySegment event)
      throws Throwable {
    return (int)
        clEnqueueNDRangeKernelHandle.invokeExact(
            commandQueue,
            kernel,
            workDim,
            globalWorkOffset,
            globalWorkSize,
            localWorkSize,
            numEventsInWaitList,
            eventWaitList,
            event);
  }

  public int clGetEventInfo(
      MemorySegment event,
      int paramName,
      long paramValueSize,
      MemorySegment paramValue,
      MemorySegment paramValueSizeRet)
      throws Throwable {
    return (int)
        clGetEventInfoHandle.invokeExact(
            event, paramName, paramValueSize, paramValue, paramValueSizeRet);
  }

  public int clWaitForEvents(int numEvents, MemorySegment eventList) throws Throwable {
    return (int) clWaitForEventsHandle.invokeExact(numEvents, eventList);
  }

  public int clGetProgramBuildInfo(
      MemorySegment program,
      MemorySegment device,
      int paramName,
      long paramValueSize,
      MemorySegment paramValue,
      MemorySegment paramValueSizeRet)
      throws Throwable {
    return (int)
        clGetProgramBuildInfoHandle.invokeExact(
            program, device, paramName, paramValueSize, paramValue, paramValueSizeRet);
  }

  public int clReleaseMemObject(MemorySegment memobj) throws Throwable {
    return (int) clReleaseMemObjectHandle.invokeExact(memobj);
  }

  public int clReleaseKernel(MemorySegment kernel) throws Throwable {
    return (int) clReleaseKernelHandle.invokeExact(kernel);
  }

  public int clReleaseProgram(MemorySegment program) throws Throwable {
    return (int) clReleaseProgramHandle.invokeExact(program);
  }

  public int clReleaseCommandQueue(MemorySegment commandQueue) throws Throwable {
    return (int) clReleaseCommandQueueHandle.invokeExact(commandQueue);
  }

  public int clReleaseContext(MemorySegment context) throws Throwable {
    return (int) clReleaseContextHandle.invokeExact(context);
  }
}
