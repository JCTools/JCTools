package io.jaq.mpsc;

import java.util.concurrent.locks.LockSupport;

public enum BackOffStrategy {
	SPIN {
		public int backoff(int called) {
			return called++;
		}
	},
	YIELD{
		public int backoff(int called) {
			Thread.yield();
			return called++;
		}
	},
	PARK{
		public int backoff(int called) {
			LockSupport.parkNanos(1);
			return called++;
		}
	},
	SPIN_YIELD {
		public int backoff(int called) {
			if(called>1000)
				Thread.yield();
			return called++;
			
		}
	};
	public abstract int backoff(int called);
	public static BackOffStrategy getStrategy(String propertyName, BackOffStrategy defaultS){
		try{
			return BackOffStrategy.valueOf(System.getProperty(propertyName));
		}
		catch(Exception e){
			return defaultS;
		}
	}
}
