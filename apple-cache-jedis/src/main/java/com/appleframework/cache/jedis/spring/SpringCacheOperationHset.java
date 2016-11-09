package com.appleframework.cache.jedis.spring;

import org.apache.log4j.Logger;

import com.appleframework.cache.core.CacheObject;
import com.appleframework.cache.core.CacheObjectImpl;
import com.appleframework.cache.core.config.CacheConfig;
import com.appleframework.cache.core.spring.CacheOperation;
import com.appleframework.cache.core.utils.SerializeUtility;
import com.appleframework.cache.jedis.factory.PoolFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@SuppressWarnings("deprecation")
public class SpringCacheOperationHset implements CacheOperation {

	private static Logger logger = Logger.getLogger(SpringCacheOperationHset.class);

	private String name;
	private int expireTime = 0;
	private PoolFactory poolFactory;

	public SpringCacheOperationHset(String name, int expireTime, PoolFactory poolFactory) {
		this.name = name;
		this.expireTime = expireTime;
		this.poolFactory = poolFactory;
	}
	
	private byte[] getNameKey() {
		return (CacheConfig.getCacheKeyPrefix() + name).getBytes();
	}
	
	public Object get(String key) {
		Object object = null;
		JedisPool jedisPool = poolFactory.getReadPool();
		Jedis jedis = jedisPool.getResource();
		try {
			byte[] cacheValue = jedis.hget(getNameKey(), key.getBytes());
			if (null != cacheValue) {
				CacheObject cache = (CacheObject) SerializeUtility.unserialize(cacheValue);
				if (null != cache) {
					if (cache.isExpired()) {
						this.resetCacheObject(key, cache);
					} else {
						object = cache.getObject();
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Cache Error : ", e);
		} finally {
			jedisPool.returnResource(jedis);
		}
		return object;
	}
	
	private void resetCacheObject(String key, CacheObject cache) {
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			cache.setExpiredTime(getExpiredTime());
			byte[] byteKey = getNameKey();
			byte[] byteValue = SerializeUtility.serialize(cache);
			jedis.hset(byteKey, key.getBytes(), byteValue);
			if(expireTime > 0)
				jedis.expire(byteKey, expireTime * 2);
		} catch (Exception e) {
			logger.warn("Cache Error : ", e);
		} finally {
			jedisPool.returnResource(jedis);
		}
	}
	
	public void put(String key, Object value) {
		if (value == null)
			this.delete(key);
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			Object cache = new CacheObjectImpl(value, getExpiredTime());
			byte[] byteKey = getNameKey();
			byte[] byteValue = SerializeUtility.serialize(cache);
			jedis.hset(byteKey, key.getBytes(), byteValue);
			if(expireTime > 0)
				jedis.expire(byteKey, expireTime * 2);
		} catch (Exception e) {
			logger.warn("Cache Error : ", e);
		} finally {
			jedisPool.returnResource(jedis);
		}
	}

	public void clear() {
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			jedis.del(getNameKey());
		} catch (Exception e) {
			logger.warn("Cache Error : ", e);
		} finally {
			jedisPool.returnResource(jedis);
		}
	}

	public void delete(String key) {
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			jedis.hdel(getNameKey(), key.getBytes());
		} catch (Exception e) {
			logger.warn("Cache Error : ", e);
		}
	}

	public int getExpireTime() {
		return expireTime;
	}
	
	private long getExpiredTime() {
		long lastTime = 2592000000L;
		if (expireTime > 0) {
			lastTime = expireTime * 1000;
		}
		return System.currentTimeMillis() + lastTime;
	}
}
