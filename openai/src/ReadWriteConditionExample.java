import java.util.concurrent.locks.*;
import java.util.*;

public class ReadWriteConditionExample {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock writeLock = rwLock.writeLock();
    private final Condition queueEmpty = writeLock.newCondition();

    private final Queue<String> queue = new LinkedList<>();

    // 读线程
    public void read() {
        rwLock.readLock().lock();
        try {
            System.out.println(Thread.currentThread().getName() + " 读取 queue: " + queue);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // 写线程（等待队列为空才能写）
    public void write(String item) throws InterruptedException {
        writeLock.lock();
        try {
            while (!queue.isEmpty()) {
                System.out.println(Thread.currentThread().getName() + " 等待队列清空...");
                queueEmpty.await(); // 等待条件满足
            }
            queue.add(item);
            System.out.println(Thread.currentThread().getName() + " 写入: " + item);
        } finally {
            writeLock.unlock();
        }
    }

    // 清空队列，并唤醒写线程
    public void clearQueue() {
        writeLock.lock();
        try {
            queue.clear();
            System.out.println("清空队列，唤醒写线程");
            queueEmpty.signal(); // 唤醒等待的写线程
        } finally {
            writeLock.unlock();
        }
    }

    // 测试入口
    public static void main(String[] args) throws InterruptedException {
        ReadWriteConditionExample example = new ReadWriteConditionExample();

        // 先加点内容进去让写线程阻塞
        example.queue.add("初始数据");

        // 写线程（会等待队列为空）
        new Thread(() -> {
            try {
                example.write("新数据");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Writer").start();

        Thread.sleep(1000); // 等待写线程卡住

        // 读线程（可以并发）
        new Thread(() -> example.read(), "Reader1").start();
        new Thread(() -> example.read(), "Reader2").start();

        Thread.sleep(1000);

        // 触发清空队列，让写线程能继续
        example.clearQueue();
    }
}