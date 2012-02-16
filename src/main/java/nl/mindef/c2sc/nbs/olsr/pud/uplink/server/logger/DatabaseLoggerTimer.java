package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.logger;

import java.util.Timer;
import java.util.TimerTask;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.signals.StopHandlerConsumer;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

public class DatabaseLoggerTimer implements StopHandlerConsumer {
	private Logger logger = Logger.getLogger(this.getClass().getName());

	private DatabaseLogger databaseLogger;

	/**
	 * @param databaseLogger
	 *          the databaseLogger to set
	 */
	@Required
	public final void setDatabaseLogger(DatabaseLogger databaseLogger) {
		this.databaseLogger = databaseLogger;
	}

	/** the expiry interval (millseconds) */
	private long interval;

	/**
	 * @param interval
	 *          the interval to set
	 */
	@Required
	public void setInterval(long interval) {
		this.interval = interval;
	}

	/**
	 * Timer task that does the actual expiry of out-of-date and empty objects
	 */
	private class ExpiryTimerTask extends TimerTask {
		@SuppressWarnings("hiding")
		private Logger logger;

		@SuppressWarnings("hiding")
		private DatabaseLogger databaseLogger;

		/**
		 * @param logger
		 *          the logger
		 * @param databaseLogger
		 *          the database logger
		 */
		public ExpiryTimerTask(Logger logger, DatabaseLogger databaseLogger) {
			super();
			this.logger = logger;
			this.databaseLogger = databaseLogger;
		}

		@Override
		public void run() {
			try {
				this.databaseLogger.logit();
			} catch (Throwable e) {
				this.logger.error("error during database logging", e);
			}
		}
	}

	/*
	 * Main
	 */

	/** the timer from which the expiry task runs */
	private Timer timer = null;

	public void init() {
		if (this.interval <= 0) {
			return;
		}

		this.timer = new Timer(this.getClass().getSimpleName());
		this.timer.scheduleAtFixedRate(new ExpiryTimerTask(this.logger, this.databaseLogger), 0, this.interval);
	}

	public void uninit() {
		if (this.timer != null) {
			this.timer.cancel();
			this.timer = null;
		}
	}

	@Override
	public void signalStop() {
		if (this.timer != null) {
			this.timer.cancel();
			this.timer = null;
		}
	}
}