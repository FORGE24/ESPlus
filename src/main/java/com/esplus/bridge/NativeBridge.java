package com.esplus.bridge;

import java.io.File;
import java.util.UUID;

public final class NativeBridge {

    private static final String[] LIBS = {
        "esplus-cbridge",
        "esplus-cppbridge",
        "esplus-asmlib"
    };

    private static boolean loaded = false;
    private static boolean loadingFailed = false;

    private NativeBridge() {}

    public static synchronized boolean isAvailable() {
        if (loaded) return true;
        if (loadingFailed) return false;
        return tryLoad();
    }

    private static boolean tryLoad() {
        for (String lib : LIBS) {
            try {
                System.loadLibrary(lib);
            } catch (UnsatisfiedLinkError e) {
                try {
                    File dir = new File(System.getProperty("java.library.path", "."));
                    File f = new File(dir, System.mapLibraryName(lib));
                    if (!f.exists()) {
                        loadingFailed = true;
                        return false;
                    }
                    System.load(f.getAbsolutePath());
                } catch (Throwable t) {
                    loadingFailed = true;
                    return false;
                }
            }
        }
        loaded = true;
        nativeInit(ProcessHandle.current().pid(), null);
        return true;
    }

    // ---------- Common C Bridge ----------
    public static native boolean nativeInit(long pid, String configPath);
    public static native String  getHwid();
    public static native int     getProcessIntegrity();
    public static native boolean checkDebugger();
    public static native boolean checkTiming();
    public static native int[]   readHwBreaks();
    public static native long    getProcessBase();

    // ---------- C++ Memory Integrity ----------
    public static native boolean cppVerifyModule(String modulePath);
    public static native int     cppScanHooks();

    // ---------- ASM Primitives ----------
    public static native long    asmRdtsc();
    public static native int[]   asmCpuId(int leaf);
    public static native long    asmMemscan(byte[] haystack, int hayLen, byte[] pattern, int patLen);

    // ---------- High-level wrappers ----------

    public static String getHardwareId() {
        return isAvailable() ? getHwid() : UUID.randomUUID().toString().replace("-", "");
    }

    public static SecuritySnapshot takeSnapshot() {
        if (!isAvailable()) return new SecuritySnapshot(false, 100, 0, 0, 0, 0);
        boolean debug = checkDebugger();
        int integrity = getProcessIntegrity();
        boolean timing = checkTiming();
        long base = getProcessBase();
        int hwHits = 0;
        try {
            int[] br = readHwBreaks();
            if (br != null) for (int v : br) if (v != 0) hwHits++;
        } catch (Throwable ignored) {}
        return new SecuritySnapshot(true, integrity, debug ? 1 : 0, hwHits, timing ? 1 : 0, base);
    }

    public static MemoryReport scanIntegrity() {
        if (!isAvailable()) return new MemoryReport(false, 100, -1);
        boolean verified = cppVerifyModule(null);
        int hooks = cppScanHooks();
        int score = 100;
        if (hooks > 0) score = Math.max(0, 100 - hooks * 15);
        return new MemoryReport(verified, score, hooks);
    }

    public static long tsc() {
        return isAvailable() ? asmRdtsc() : System.nanoTime();
    }

    public static long nsPerCycle() {
        if (!isAvailable()) return 1;
        long t1 = tsc();
        long n1 = System.nanoTime();
        long t2 = tsc();
        long n2 = System.nanoTime();
        long deltaT = t2 - t1;
        long deltaN = n2 - n1;
        if (deltaT <= 0) return 1;
        return Math.max(1, deltaN / deltaT);
    }

    public static final class SecuritySnapshot {
        public final boolean available;
        public final int integrityScore;
        public final int debuggerDetected;
        public final int hwBreaksActive;
        public final int timingSkewDetected;
        public final long moduleBase;
        public SecuritySnapshot(boolean a, int s, int d, int h, int t, long b) {
            this.available = a; this.integrityScore = s; this.debuggerDetected = d;
            this.hwBreaksActive = h; this.timingSkewDetected = t; this.moduleBase = b;
        }
        @Override public String toString() {
            return "SecuritySnapshot[avail="+available+", integrity="+integrityScore+
                ", debugger="+debuggerDetected+", hwBreaks="+hwBreaksActive+
                ", timing="+timingSkewDetected+", base=0x"+Long.toHexString(moduleBase)+"]";
        }
    }

    public static final class MemoryReport {
        public final boolean headerValid;
        public final int score;
        public final int hookedFuncs;
        public MemoryReport(boolean v, int s, int h) {
            this.headerValid = v; this.score = s; this.hookedFuncs = h;
        }
        @Override public String toString() {
            return "MemoryReport[peOk="+headerValid+", score="+score+", hooks="+hookedFuncs+"]";
        }
    }
}
