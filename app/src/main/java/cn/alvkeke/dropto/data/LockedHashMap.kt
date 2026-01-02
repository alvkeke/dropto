package cn.alvkeke.dropto.data

import android.util.Log
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class LockedHashMap<K, V>  {

    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val map: HashMap<K, V> = HashMap()

    val isEmpty: Boolean
        get() {
            lock.readLock().lock()
            val ret = map.isEmpty()
            lock.readLock().unlock()
            return ret
        }

    operator fun get(key: K): V? {
        lock.readLock().lock()
        val value = map.get(key)
        lock.readLock().unlock()
        return value
    }

    fun remove(key: K): V? {
        lock.writeLock().lock()
        val value = map.remove(key)
        lock.writeLock().unlock()
        return value
    }

    fun removeAll(keys: Collection<K>): Boolean {
        Log.e(this.toString(), "removeAll")
        var ret = false
        lock.writeLock().lock()
        for (key in keys) {
            if (map.remove(key) != null)
                ret = true
        }
        lock.writeLock().unlock()

        return ret
    }

    fun putIfAbsent(key: K, value: V): V? {
        lock.writeLock().lock()
        val v = map.putIfAbsent(key, value)
        lock.writeLock().unlock()
        return v
    }

    fun clear() {
        lock.writeLock().lock()
        map.clear()
        lock.writeLock().unlock()
    }

    fun filter(predicate: (Map.Entry<K, V>) -> Boolean): List<Map.Entry<K, V>> {
        Log.e(this.toString(), "filter")
        lock.readLock().lock()
        val result = map.entries.filter(predicate)
        lock.readLock().unlock()

        return result
    }

}