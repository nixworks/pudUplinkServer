/*
 *  Copyright (C) 2012 Royal Dutch Army
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 *  MA  02110-1301, USA.
 */
package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.impl;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.Senders;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.RelayServer;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.Sender;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.util.TxChecker;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.ClusterLeaderHandler;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.PacketHandler;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.PositionUpdateHandler;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.impl.debug.Faker;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.util.DumpUtil;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.olsr.plugin.pud.ClusterLeader;
import org.olsr.plugin.pud.PositionUpdate;
import org.olsr.plugin.pud.UplinkMessage;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PacketHandlerImpl implements PacketHandler {
	private Logger logger = Logger.getLogger(this.getClass().getName());

	private PositionUpdateHandler positionUpdateHandler;

	/**
	 * @param positionUpdateHandler
	 *          the positionUpdateHandler to set
	 */
	@Required
	public final void setPositionUpdateHandler(PositionUpdateHandler positionUpdateHandler) {
		this.positionUpdateHandler = positionUpdateHandler;
	}

	private ClusterLeaderHandler clusterLeaderHandler;

	/**
	 * @param clusterLeaderHandler
	 *          the clusterLeaderHandler to set
	 */
	@Required
	public final void setClusterLeaderHandler(ClusterLeaderHandler clusterLeaderHandler) {
		this.clusterLeaderHandler = clusterLeaderHandler;
	}

	private Senders senders = null;

	/**
	 * @param senders
	 *          the senders to set
	 */
	@Required
	public final void setSenders(Senders senders) {
		this.senders = senders;
	}

	/*
	 * Fake data
	 */

	private boolean useFaker = false;

	/**
	 * @param useFaker
	 *          the useFaker to set
	 */
	public final void setUseFaker(boolean useFaker) {
		this.useFaker = useFaker;
	}

	private Faker faker = null;

	/**
	 * @param faker
	 *          the faker to set
	 */
	@Required
	public final void setFaker(Faker faker) {
		this.faker = faker;
	}

	/*
	 * end fake
	 */

	private TxChecker txChecker;

	/**
	 * @param txChecker
	 *          the txChecker to set
	 */
	@Required
	public final void setTxChecker(TxChecker txChecker) {
		this.txChecker = txChecker;
	}

	@Override
	@Transactional
	public boolean processPacket(RelayServer relayServer, DatagramPacket packet) {
		try {
			this.txChecker.checkInTx("PacketHandler::processPacket");
		} catch (Throwable e) {
			e.printStackTrace();
		}

		long utcTimestamp = System.currentTimeMillis();
		boolean updated = false;

		if (this.logger.isDebugEnabled()) {
			this.logger.debug("[" + utcTimestamp + "] Received " + packet.getLength() + " bytes from "
					+ packet.getAddress().getHostAddress() + ":" + packet.getPort());
		}

		try {
			InetAddress srcIp = packet.getAddress();
			int srcPort = packet.getPort();

			/* get sender or create */
			Sender sender = this.senders.getSender(srcIp, srcPort);
			if (sender == null) {
				sender = new Sender(srcIp, Integer.valueOf(srcPort), relayServer);
			}

			/* make sure the sender is linked to this relayServer */
			sender.setRelayServer(relayServer);

			/* get packet data */
			byte[] packetData = packet.getData();
			int packetLength = packet.getLength();

			/* parse the uplink packet for messages */
			int messageOffset = 0;
			while (messageOffset < packetLength) {
				int messageType = UplinkMessage.getUplinkMessageType(packetData, messageOffset);
				int messageLength = UplinkMessage.getUplinkMessageHeaderLength()
						+ UplinkMessage.getUplinkMessageLength(packetData, messageOffset);

				if ((messageType != UplinkMessage.getUplinkMessageTypePosition())
						&& (messageType != UplinkMessage.getUplinkMessageTypeClusterLeader())) {
					this.logger.warn("Uplink message type " + messageType + " not supported: ignored");
				} else {
					byte[] messageData = Arrays.copyOfRange(packetData, messageOffset, messageOffset + messageLength);
					DumpUtil.dumpUplinkMessage(this.logger, Level.DEBUG, messageData, srcIp, srcPort, messageType, utcTimestamp,
							"  ");

					Object msg = null;
					boolean msgCausedUpdate = false;
					if (messageType == UplinkMessage.getUplinkMessageTypePosition()) {
						msg = new PositionUpdate(messageData, messageLength);
						msgCausedUpdate = this.positionUpdateHandler.handlePositionMessage(sender, utcTimestamp,
								(PositionUpdate) msg);
					} else /* if (messageType == UplinkMessage.getUplinkMessageTypeClusterLeader()) */{
						msg = new ClusterLeader(messageData, messageLength);
						msgCausedUpdate = this.clusterLeaderHandler.handleClusterLeaderMessage(sender, utcTimestamp,
								(ClusterLeader) msg);
					}
					if (msgCausedUpdate && this.useFaker) {
						this.faker.fakeit(sender, utcTimestamp, msg);
					}
					updated = msgCausedUpdate || updated;
				}

				messageOffset += messageLength;
			}

			return updated;
		} catch (Throwable e) {
			this.logger.error("error during packet handling", e);
			return updated;
		} finally {
			if (this.useFaker) {
				this.faker.setFirstFake(false);
			}
		}
	}
}
