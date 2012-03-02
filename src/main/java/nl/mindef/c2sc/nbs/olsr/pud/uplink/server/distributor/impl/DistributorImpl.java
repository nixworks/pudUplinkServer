package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.distributor.impl;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.distributor.Distributor;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.distributor.DistributorWorker;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

public class DistributorImpl implements Distributor {
	private Logger logger = Logger.getLogger(this.getClass().getName());

	private DistributorWorker distributorWorker;

	/**
	 * @param distributorWorker
	 *          the distributorWorker to set
	 */
	@Required
	public final void setDistributorWorker(DistributorWorker distributorWorker) {
		this.distributorWorker = distributorWorker;
	}

	private long distributionDelay = 1000;

	/**
	 * @param distributionDelay
	 *          the distributionDelay to set
	 */
	public final void setDistributionDelay(long distributionDelay) {
		this.distributionDelay = distributionDelay;
	}

	private Timer timer;

	/**
	 * @param timer
	 *          the timer to set
	 */
	@Required
	public final void setTimer(Timer timer) {
		this.timer = timer;
	}

	protected class DistributionTimerTask extends TimerTask {
		@SuppressWarnings("hiding")
		private Logger logger;

		@SuppressWarnings("hiding")
		private DistributorWorker distributorWorker;

		@SuppressWarnings("hiding")
		private AtomicBoolean signaledUpdates;

		/**
		 * @param logger
		 *          the logger
		 * @param distributorWorker
		 *          the distributorWorker
		 * @param signaledUpdates
		 *          the updates counter
		 */
		protected DistributionTimerTask(Logger logger, DistributorWorker distributorWorker, AtomicBoolean signaledUpdates) {
			super();
			this.logger = logger;
			this.distributorWorker = distributorWorker;
			this.signaledUpdates = signaledUpdates;
		}

		@Override
		public void run() {
			try {
				if (this.signaledUpdates.getAndSet(false)) {
					this.distributorWorker.distribute();
				}
			} catch (Throwable e) {
				this.logger.error("error during distribution", e);
			}
		}
	}

	private AtomicBoolean signaledUpdates = new AtomicBoolean(false);

	public void init() {
		this.timer.scheduleAtFixedRate(
				new DistributionTimerTask(this.logger, this.distributorWorker, this.signaledUpdates), this.distributionDelay,
				this.distributionDelay);
	}

	@Override
	public void signalUpdate() {
		this.signaledUpdates.set(true);
	}
}
