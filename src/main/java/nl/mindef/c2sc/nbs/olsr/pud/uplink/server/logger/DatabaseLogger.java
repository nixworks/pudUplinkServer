package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Timer;
import java.util.TimerTask;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.ClusterLeaderMsgs;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.Gateways;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.Nodes;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.PositionUpdateMsgs;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.RelayServers;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

public class DatabaseLogger {
	Logger logger = Logger.getLogger(this.getClass().getName());

	private int updateIntervalMs = 0;

	/**
	 * @param updateIntervalMs
	 *          the updateIntervalMs to set
	 */
	@Required
	public final void setUpdateIntervalMs(int updateIntervalMs) {
		this.updateIntervalMs = updateIntervalMs;
	}

	private String databaseLogFile = null;

	/**
	 * @param databaseLogFile
	 *          the databaseLogFile to set
	 */
	@Required
	public final void setDatabaseLogFile(String databaseLogFile) {
		this.databaseLogFile = databaseLogFile;
	}

	/** the Node handler */
	Nodes nodes;

	/**
	 * @param nodes
	 *          the nodes to set
	 */
	@Required
	public final void setNodes(Nodes nodes) {
		this.nodes = nodes;
	}

	/** the PositionUpdateMsgs handler */
	PositionUpdateMsgs positionUpdateMsgs;

	/**
	 * @param positionUpdateMsgs
	 *          the positionUpdateMsgs to set
	 */
	@Required
	public final void setPositions(PositionUpdateMsgs positionUpdateMsgs) {
		this.positionUpdateMsgs = positionUpdateMsgs;
	}

	/** the ClusterLeaderMsgs handler */
	ClusterLeaderMsgs clusterLeaderMsgs;

	/**
	 * @param clusterLeaderMsgs
	 *          the clusterLeaderMsgs to set
	 */
	@Required
	public final void setClusterLeaderMsgs(ClusterLeaderMsgs clusterLeaderMsgs) {
		this.clusterLeaderMsgs = clusterLeaderMsgs;
	}

	RelayServers relayServers;

	/**
	 * @param relayServers
	 *          the relayServers to set
	 */
	@Required
	public final void setRelayServers(RelayServers relayServers) {
		this.relayServers = relayServers;
	}

	Gateways gateways;

	/**
	 * @param gateways
	 *          the gateways to set
	 */
	@Required
	public final void setGateways(Gateways gateways) {
		this.gateways = gateways;
	}

	/*
	 * Main
	 */

	private Timer timer = null;
	private TimerTask task = null;
	FileOutputStream fos = null;
	static final byte[] eol = "\n".getBytes();

	public void init() throws FileNotFoundException {
		if (this.updateIntervalMs <= 0) {
			return;
		}

		this.fos = new FileOutputStream(this.databaseLogFile, false);

		this.timer = new Timer(this.getClass().getName() + "-Timer");
		this.task = new TimerTask() {
			@Override
			public void run() {
				try {
					FileChannel channel = DatabaseLogger.this.fos.getChannel();
					channel.position(0);

					DatabaseLogger.this.logger.debug("Writing database logfile");

					DatabaseLogger.this.relayServers.print(DatabaseLogger.this.fos);
					DatabaseLogger.this.fos.write(eol);
					DatabaseLogger.this.gateways.print(DatabaseLogger.this.fos);
					DatabaseLogger.this.fos.write(eol);
					DatabaseLogger.this.nodes.print(DatabaseLogger.this.fos);
					DatabaseLogger.this.fos.write(eol);
					DatabaseLogger.this.positionUpdateMsgs.print(DatabaseLogger.this.fos);
					DatabaseLogger.this.fos.write(eol);
					DatabaseLogger.this.clusterLeaderMsgs.print(DatabaseLogger.this.fos);

					channel.truncate(channel.position());
					DatabaseLogger.this.fos.flush();
				} catch (Throwable t) {
					DatabaseLogger.this.logger.error("Error while logging database", t);
				}
			}
		};

		this.timer.scheduleAtFixedRate(this.task, 0, this.updateIntervalMs);
	}

	public void destroy() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
		this.timer.cancel();

		if (this.fos != null) {
			try {
				this.fos.close();
			} catch (IOException e) {
				/* ignore */
			}
			this.fos = null;
		}
	}

	public void log(Logger logger, Level level) {
		this.relayServers.log(logger, level);
		this.gateways.log(logger, level);
		this.nodes.log(logger, level);
		this.positionUpdateMsgs.log(logger, level);
		this.clusterLeaderMsgs.log(logger, level);
	}
}
