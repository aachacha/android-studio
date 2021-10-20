package android.com.java.profilertester.taskcategory;

import android.com.java.profilertester.MainActivityFragment.SleepControl;
import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MemoryTaskCategory extends TaskCategory {
    private final int PERIOD_TIME = 2;
    private final int ITERATION_COUNT = 5;
    private final int DELTA_SIZE = (1 << 22);
    private final int DELTA_OBJECT_COUNT = 10000;
    private final int TEST_OBJECT_SIZE_SMALL = 100;
    private final int TEST_OBJECT_SIZE_LARGE = 10000;
    private final int TEST_OBJECT_COUNT_MANY = 100000;
    private final int TEST_OBJECT_COUNT_FEW = 1000;

    private final List<? extends Task> mTasks;

    static {
        System.loadLibrary("native_memory");
    }

    public MemoryTaskCategory(SleepControl sleepControl) {
        mTasks =
                Arrays.asList(
                        new AllocateJavaMemoryTask(sleepControl),
                        new AllocateNativeMemoryTask(),
                        new AllocateObjectsTask(sleepControl),
                        new JniRefsTask(sleepControl),
                        new AllocateManyObjectsTask(sleepControl),
                        new AllocateFewObjectsTask(sleepControl));
    }

    public native void allocateNativeMemory();

    public native long allocateJniRef(Object o);

    public native void freeJniRef(long refValue);

    private class AllocateJavaMemoryTask extends MemoryTask {
        private SleepControl mySleepControl;

        AllocateJavaMemoryTask(SleepControl sleepControl) {
            mySleepControl = sleepControl;
        }

        @Override
        protected String memoryExecute() throws Exception {
            char[][] table = new char[ITERATION_COUNT][];
            for (int i = 0; i < ITERATION_COUNT; ++i) {
                long start = System.currentTimeMillis();
                table[i] = new char[DELTA_SIZE];
                Arrays.fill(table[i], 'x');
                mySleepControl.sleepIfAllowed(
                        TimeUnit.MILLISECONDS,
                        PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Java Memory Allocation";
        }
    }

    private class AllocateNativeMemoryTask extends MemoryTask {

        @Override
        protected String memoryExecute() throws Exception {
            MemoryTaskCategory.this.allocateNativeMemory();
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Native Memory Allocation";
        }
    }

    private class AllocationTestObjectOfSize {
        byte[] values;

        AllocationTestObjectOfSize(int size) {
            values = new byte[size];
            for (int i = 0; i < values.length; ++i) {
                values[i] = (byte) (i & 0xFF);
            }
        }
    }

    private class AllocateObjectsTask extends MemoryTask {
        private int myObjectCount;
        private int myObjectSize;
        private SleepControl mySleepControl;

        AllocateObjectsTask(SleepControl sleepControl) {
            this(DELTA_OBJECT_COUNT, 1, sleepControl);
        }

        AllocateObjectsTask(int objectCount, int objectSize, SleepControl sleepControl) {
            myObjectCount = objectCount;
            myObjectSize = objectSize;
            mySleepControl = sleepControl;
        }

        @Override
        protected String memoryExecute() throws Exception {
            Object[][] objects = new Object[ITERATION_COUNT][];
            long totalAllocationTiming = 0;
            for (int k = 0; k < ITERATION_COUNT; ++k) {
                objects[k] = new Object[myObjectCount];
                long start = System.currentTimeMillis();
                for (int i = 0; i < objects[k].length; ++i) {
                    objects[k][i] = new AllocationTestObjectOfSize(myObjectSize);
                }
                long end = System.currentTimeMillis();
                totalAllocationTiming += (end - start);

                mySleepControl.sleepIfAllowed(
                        TimeUnit.MILLISECONDS,
                        PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }

            return String.format(
                    "Allocation took %d milliseconds on average",
                    totalAllocationTiming / ITERATION_COUNT);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Object Allocation";
        }
    }

    private class AllocateManyObjectsTask extends AllocateObjectsTask {
        AllocateManyObjectsTask(SleepControl sleepControl) {
            super(TEST_OBJECT_COUNT_MANY, TEST_OBJECT_SIZE_SMALL, sleepControl);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Many-object Allocation";
        }
    }

    private class AllocateFewObjectsTask extends AllocateObjectsTask {
        AllocateFewObjectsTask(SleepControl sleepControl) {
            super(TEST_OBJECT_COUNT_FEW, TEST_OBJECT_SIZE_LARGE, sleepControl);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Few-object Allocation";
        }
    }

    private class JniRefsTask extends MemoryTask {
        private SleepControl mySleepControl;

        JniRefsTask(SleepControl sleepControl) {
            mySleepControl = sleepControl;
        }

        @Override
        protected String memoryExecute() throws Exception {
            long[] refs = new long[DELTA_OBJECT_COUNT / 10];
            for (int k = 0; k < ITERATION_COUNT; ++k) {
                long start = System.currentTimeMillis();
                for (int i = 0; i < refs.length; ++i) {
                    refs[i] = allocateJniRef(new AllocationTestObjectOfSize(4));
                }
                Runtime.getRuntime().gc();
                for (long ref : refs) {
                    freeJniRef(ref);
                }
                mySleepControl.sleepIfAllowed(
                        TimeUnit.MILLISECONDS,
                        PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }

            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "JNI References Allocation";
        }
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Memory";
    }

    private static abstract class MemoryTask extends Task {
        @Override
        protected final String execute() throws Exception {
            Runtime.getRuntime().gc();
            String result = memoryExecute();
            Runtime.getRuntime().gc();
            return result;
        }

        protected abstract String memoryExecute() throws Exception;
    }
}
