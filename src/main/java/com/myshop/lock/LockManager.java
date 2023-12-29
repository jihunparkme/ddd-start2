package com.myshop.lock;

public interface LockManager {
    /**
     *
     * @param type 잠글 대상 타입 (domain.Article)
     * @param id 식별자 (10)
     * @return
     * @throws LockException
     */
    LockId tryLock(String type, String id) throws LockException;

    void checkLock(LockId lockId) throws LockException;

    void releaseLock(LockId lockId) throws LockException;

    void extendLockExpiration(LockId lockId, long inc) throws LockException;
}
