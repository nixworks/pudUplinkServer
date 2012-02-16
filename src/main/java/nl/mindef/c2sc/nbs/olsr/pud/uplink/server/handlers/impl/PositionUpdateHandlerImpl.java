package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.impl;

import java.net.InetAddress;

import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.Nodes;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.PositionUpdateMsgs;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.Senders;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.Node;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.PositionUpdateMsg;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.dao.domainmodel.Sender;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.handlers.PositionUpdateHandler;
import nl.mindef.c2sc.nbs.olsr.pud.uplink.server.util.TimeZoneUtil;

import org.olsr.plugin.pud.PositionUpdate;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PositionUpdateHandlerImpl implements PositionUpdateHandler {
	/** the PositionUpdateMsgs handler */
	private PositionUpdateMsgs positionUpdateMsgs;

	/**
	 * @param positionUpdateMsgs
	 *          the positionUpdateMsgs to set
	 */
	@Required
	public final void setPositions(PositionUpdateMsgs positionUpdateMsgs) {
		this.positionUpdateMsgs = positionUpdateMsgs;
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

	/** the Node handler */
	private Nodes nodes;

	/**
	 * @param nodes
	 *          the nodes to set
	 */
	@Required
	public final void setNodes(Nodes nodes) {
		this.nodes = nodes;
	}

	/** the wire format checker */
	private WireFormatChecker wireFormatChecker;

	/**
	 * @param wireFormatChecker
	 *          the wireFormatChecker to set
	 */
	@Required
	public final void setWireFormatChecker(WireFormatChecker wireFormatChecker) {
		this.wireFormatChecker = wireFormatChecker;
	}

	@Override
	@Transactional
	public boolean handlePositionMessage(Sender sender, long utcTimestamp, PositionUpdate puMsg) {
		assert (puMsg != null);
		assert (sender != null);

		if (!this.wireFormatChecker.checkUplinkMessageWireFormat(sender, puMsg)) {
			return false;
		}

		if (sender.getId() == null) {
			this.senders.saveSender(sender);
		}

		InetAddress originator = puMsg.getOlsrMessageOriginator();

		/* retrieve the node that sent the position update */
		Node originatorNode = this.nodes.getNode(originator);
		if (originatorNode == null) {
			/* new node */
			originatorNode = new Node(originator, sender);
			this.nodes.saveNode(originatorNode);
		}

		/* link the node to the sender from which it was received */
		originatorNode.setSender(sender);

		/* get the position update of the node */
		PositionUpdateMsg storedPositionUpdate = originatorNode.getPositionUpdateMsg();
		if (storedPositionUpdate == null) {
			/* new position update */
			storedPositionUpdate = new PositionUpdateMsg(originatorNode, puMsg);
			this.positionUpdateMsgs.savePositionUpdateMsg(storedPositionUpdate);
		} else {
			/* check that received timestamp not earlier than the stored timestamp */
			if (storedPositionUpdate.getPositionUpdateMsg() != null) {
				long storedTimestamp = storedPositionUpdate.getPositionUpdateMsg().getPositionUpdateTime(utcTimestamp,
						TimeZoneUtil.getTimezoneOffset());

				/* get the received timestamp */
				long receivedTimeStamp = puMsg.getPositionUpdateTime(utcTimestamp, TimeZoneUtil.getTimezoneOffset());

				if (receivedTimeStamp < storedTimestamp) {
					/*
					 * we have stored a position with a more recent timestamp already, so skip this one. If the timestamp is the
					 * same however, then we just process the position update
					 */
					return false;
				}
			}
		}

		/* fill in the position update */
		storedPositionUpdate.setPositionUpdateMsg(puMsg);
		storedPositionUpdate.setReceptionTime(utcTimestamp);
		storedPositionUpdate.setValidityTime(puMsg.getPositionUpdateValidityTime() * 1000);

		/* link the position update to the node */
		originatorNode.setPositionUpdateMsg(storedPositionUpdate);

		/* save the node and position. explicitly saving the originatorNode is not needed since that is cascaded */
		this.positionUpdateMsgs.savePositionUpdateMsg(storedPositionUpdate);

		return true;
	}
}
