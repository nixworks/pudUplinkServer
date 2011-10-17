package nl.mindef.c2sc.nbs.olsr.pud.uplink.server.util;

import java.net.DatagramPacket;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.olsr.plugin.pud.ClusterLeader;
import org.olsr.plugin.pud.PositionUpdate;
import org.olsr.plugin.pud.UplinkMessage;

public class Util {
	static {
		GregorianCalendar cal = new GregorianCalendar();
		timezoneOffset = -(cal.get(Calendar.ZONE_OFFSET)
		/* + cal .get(Calendar.DST_OFFSET) */);
	}

	private static final long timezoneOffset;

	/**
	 * @return the timezoneOffset
	 */
	public static final long getTimezoneOffset() {
		return timezoneOffset;
	}

	public static double nmeaDeg2Ndeg(double degrees) {
		long deg = (long) degrees;
		double fra_part = degrees - deg;
		return ((deg * 100.0) + (fra_part * 60.0));

	}

	public static void dumpUplinkMessage(Logger logger, Level level,
			byte[] data, DatagramPacket packet, int type, long utcTimestamp) {
		if (!logger.isEnabledFor(level)) {
			return;
		}

		UplinkMessage ul;
		if (type == UplinkMessage.getUplinkMessageTypePosition()) {
			ul = new PositionUpdate(data, data.length);
		} else if (type == UplinkMessage.getUplinkMessageTypeClusterLeader()) {
			ul = new ClusterLeader(data, data.length);
		} else {
			logger.log(level, "Unknown uplink message type: " + type);
			return;
		}

		StringBuilder s = new StringBuilder();

		s.append("  *** UplinkMessage ***\n");
		s.append("  data   =");
		for (int index = 0; index < data.length; index++) {
			s.append(String.format(" %02x", data[index]));
		}
		s.append("\n");
		s.append(String.format("  sender = %s\n", packet.getAddress()
				.getHostAddress()));
		s.append(String.format("  size   = %d bytes\n", data.length));

		s.append("    *** UplinkHeader ***\n");
		s.append(String.format("    type   = %d\n", type));
		s.append(String.format("    length = %d bytes\n",
				ul.getUplinkMessageLength()));
		s.append(String.format("    ipv6   = %b\n", ul.isUplinkMessageIPv6()));

		if (ul instanceof PositionUpdate) {
			PositionUpdate pu = (PositionUpdate) ul;

			s.append("      *** OLSR header ***\n");

			s.append(String.format("      originator   = %s\n", pu
					.getOlsrMessageOriginator().getHostAddress()));

			s.append("      *** PudOlsrPositionUpdate ***\n");
			s.append(String.format("      version      = %d\n",
					pu.getPositionUpdateVersion()));
			s.append(String.format("      validity     = %d sec\n",
					pu.getPositionUpdateValidityTime()));
			s.append(String.format("      smask        = 0x%02x\n",
					pu.getPositionUpdateSMask()));

			s.append("        *** GpsInfo ***\n");
			s.append(String.format("        time       = %d\n",
					pu.getPositionUpdateTime(utcTimestamp, timezoneOffset)));
			s.append(String.format("        lat        = %f\n",
					nmeaDeg2Ndeg(pu.getPositionUpdateLatitude())));
			s.append(String.format("        lon        = %f\n",
					nmeaDeg2Ndeg(pu.getPositionUpdateLongitude())));
			s.append(String.format("        alt        = %d m\n",
					pu.getPositionUpdateAltitude()));
			s.append(String.format("        speed      = %d kph\n",
					pu.getPositionUpdateSpeed()));
			s.append(String.format("        track      = %d deg\n",
					pu.getPositionUpdateTrack()));
			s.append(String.format("        hdop       = %f\n",
					pu.getPositionUpdateHdop()));

			s.append("        *** NodeInfo ***\n");
			s.append(String.format("        nodeIdType = %d\n",
					pu.getPositionUpdateNodeIdType()));
			s.append(String.format("        nodeId     = %s\n",
					pu.getPositionUpdateNodeId()));
		} else if (ul instanceof ClusterLeader) {
			ClusterLeader cl = (ClusterLeader) ul;

			s.append("      *** UplinkClusterLeader ***\n");
			s.append(String.format("      version       = %d\n",
					cl.getClusterLeaderVersion()));
			s.append(String.format("      validity      = %d sec\n",
					cl.getClusterLeaderValidityTime()));
			s.append(String.format("      downlinkPort  = %d\n",
					cl.getClusterLeaderDownlinkPort()));
			s.append(String.format("      originator    = %s\n", cl
					.getClusterLeaderOriginator().getHostAddress()));
			s.append(String.format("      clusterLeader = %s\n", cl
					.getClusterLeaderClusterLeader().getHostAddress()));
		}

		logger.log(level, s.toString());
	}
}
