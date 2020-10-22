package ink.anur.timewheel;

import ink.anur.io.common.ShutDownHooker;

/**
 * Created by Anur IjuoKaruKas on 2018/10/16
 * <p>
 * 需要延迟执行的任务，放在槽 {@link Bucket} 里面
 */
public class TimedTask {

    /**
     * 延迟多久执行时间
     */
    private long delayMs;

    /**
     * 过期时间戳
     */
    private long expireTimestamp;

    /**
     * 任务
     */
    private Runnable task;

    /**
     * 是否被取消
     */
    protected volatile boolean cancel;

    protected Bucket bucket;

    protected TimedTask next;

    protected TimedTask pre;

    public String desc;

    private ShutDownHooker sdh = new ShutDownHooker();

    public TimedTask(long delayMs, Runnable task) {
        this.delayMs = delayMs;
        this.task = task;
        this.bucket = null;
        this.next = null;
        this.pre = null;
        this.expireTimestamp = System.currentTimeMillis() + delayMs;
        this.cancel = false;
    }

    public void cancel() {
        sdh.shutdown();
        cancel = true;
    }

    protected void setExpireTimestamp(long t) {
        this.expireTimestamp = t;
    }

    public boolean isCancel() {
        return cancel;
    }

    public Runnable getTask() {
        return task;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public long getExpireTimestamp() {
        return expireTimestamp;
    }

    public ShutDownHooker getSdh() {
        return sdh;
    }

    @Override
    public String toString() {
        return desc;
    }
}