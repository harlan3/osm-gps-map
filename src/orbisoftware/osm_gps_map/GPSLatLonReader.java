/**
 * Copyright (C) 2026, Harlan Murphy
 * Use of this source code is governed by a BSD Zero Clause License
 * 
 * See project license.txt for details
*/

package orbisoftware.osm_gps_map;

import com.fazecast.jSerialComm.SerialPort;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.InputStream;
import java.util.Scanner;

public class GPSLatLonReader extends Thread implements Runnable {

	private final double EARTH_RADIUS_METERS = 6371000.0;

	private static double latGenerator = 0.0;
	private static double lonGenerator = 0.0;

	private final int TILE_SIZE = 256;
	private final double MAX_LAT = 85.05112878;
	
	@Override
	public void run() {
		
		if (SharedData.comPort == null) {
			System.err.println("COM port for GPS was not specified. Pass it in as an arguement.");
			System.exit(0);
		}

		SerialPort port = SerialPort.getCommPort(SharedData.comPort);
		port.setComPortParameters(4800, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);

		port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);

		if (!port.openPort()) {
			System.err.println("Could not open GPS port");
			return;
		}

		System.out.println("GPS connected");

		while (true) {

			try (InputStream in = port.getInputStream(); Scanner scanner = new Scanner(in)) {
				
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine().trim();

					if (line.startsWith("$GPRMC"))
						parseGPRMC(line);
					else if (line.startsWith("$GNGGA"))
						parseGNGGA(line);
					else if (line.startsWith("$GPGGA"))
						parseGPGGA(line);
					
					if (SharedData.applicationClosing)
						break;
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				port.closePort();
			}
		}
	}

	public void displayPortInfo() {

		SerialPort[] ports = SerialPort.getCommPorts();
		for (SerialPort port : ports) {
			System.out.println(port.getSystemPortName() + " : " + port.getDescriptivePortName());
		}
	}

	// ===================== PARSERS =====================

	private void parseGPRMC(String nmea) {

		String[] t = nmea.split(",");

		if (t.length < 7 || !"A".equals(t[2]))
			return;

		LatLon newCoord = new LatLon(nmeaToDecimal(t[3], t[4]), nmeaToDecimal(t[5], t[6]));

		// Update previous coordinate
		if (newCoord != SharedData.getInstance().getCurrCoord())
			SharedData.getInstance().setPrevCoord(SharedData.getInstance().getCurrCoord());

		if (SharedData.incrementGPS) {
			
			latGenerator -= 0.0001;
			lonGenerator -= 0.0001;
			newCoord.latitude += latGenerator;
			newCoord.longitude += lonGenerator;
		}

		// Update current coordinate
		SharedData.getInstance().setCurrCoord(newCoord);

		if (SharedData.debug)
			System.out.printf("GPRMC -> Lat: %.6f  Lon: %.6f%n", newCoord.latitude, newCoord.longitude);
	}

	private void parseGNGGA(String nmea) {

		// Remove checksum if present
		int asterisk = nmea.indexOf('*');
		if (asterisk > 0) {
			nmea = nmea.substring(0, asterisk);
		}

		String[] t = nmea.split(",");

		// GNGGA requires at least 6 fields for lat/lon
		if (t.length < 6) {
			return;
		}

		LatLon newCoord = new LatLon(nmeaToDecimal(t[2], t[3]), nmeaToDecimal(t[4], t[5]));

		// Update previous coordinate
		if (newCoord != SharedData.getInstance().getCurrCoord())
			SharedData.getInstance().setPrevCoord(SharedData.getInstance().getCurrCoord());

		if (SharedData.incrementGPS) {

			latGenerator -= 0.0001;
			lonGenerator -= 0.0001;
			newCoord.latitude += latGenerator;
			newCoord.longitude += lonGenerator;
		}

		// Update current coordinate
		SharedData.getInstance().setCurrCoord(newCoord);

		if (SharedData.debug)
			System.out.printf("GNGGA -> Lat: %.6f  Lon: %.6f%n", newCoord.latitude, newCoord.longitude);
	}

	private void parseGPGGA(String nmea) {

		String[] t = nmea.split(",");

		if (t.length < 6 || t[2].isEmpty())
			return;

		LatLon newCoord = new LatLon(nmeaToDecimal(t[2], t[3]), nmeaToDecimal(t[4], t[5]));

		// Update previous coordinate
		if (newCoord != SharedData.getInstance().getCurrCoord())
			SharedData.getInstance().setPrevCoord(SharedData.getInstance().getCurrCoord());

		if (SharedData.incrementGPS) {
			
			latGenerator -= 0.0001;
			lonGenerator -= 0.0001;
			newCoord.latitude += latGenerator;
			newCoord.longitude += lonGenerator;
		}
		
		// Update current coordinate
		SharedData.getInstance().setCurrCoord(newCoord);

		if (SharedData.debug)
			System.out.printf("GPGGA -> Lat: %.6f  Lon: %.6f%n", newCoord.latitude, newCoord.longitude);
	}

	// ===================== CONVERSION =====================

	/**
	 * Converts NMEA ddmm.mmmm (lat) or dddmm.mmmm (lon) to decimal degrees.
	 */
	private double nmeaToDecimal(String value, String hemisphere) {

		if (value == null || value.isEmpty())
			return 0.0;

		double raw = Double.parseDouble(value);

		int degrees = (int) (raw / 100);
		double minutes = raw - (degrees * 100);

		double decimal = degrees + (minutes / 60.0);

		if ("S".equals(hemisphere) || "W".equals(hemisphere)) {
			decimal = -decimal;
		}

		return decimal;
	}

	public double bearingDegFromLoc(LatLon latLon1, LatLon latLon2) {

		if (latLon1 == null || latLon2 == null)
			return 0.0;

		double lat1 = Math.toRadians(latLon1.latitude);
		double lat2 = Math.toRadians(latLon2.latitude);
		double deltaLon = Math.toRadians(latLon2.longitude - latLon1.longitude);

		double y = Math.sin(deltaLon) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

		double bearingRad = Math.atan2(y, x);
		double bearingDeg = Math.toDegrees(bearingRad);

		// Normalize to 0–360
		return (bearingDeg + 360.0) % 360.0;
	}

	public double distanceMeters(LatLon latLon1, LatLon latLon2) {

		double latRad1 = Math.toRadians(latLon1.latitude);
		double latRad2 = Math.toRadians(latLon2.latitude);
		double deltaLat = Math.toRadians(latLon2.latitude - latLon1.latitude);
		double deltaLon = Math.toRadians(latLon2.longitude - latLon1.longitude);

		double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
				+ Math.cos(latRad1) * Math.cos(latRad2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return EARTH_RADIUS_METERS * c;
	}

	private double worldSize(int zoom) {

		return TILE_SIZE * Math.pow(2, zoom);
	}

	private Point2D.Double latLonToWorldPixel(double lat, double lon, int zoom) {

		lat = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));

		double sinLat = Math.sin(Math.toRadians(lat));
		double worldSize = worldSize(zoom);

		double x = (lon + 180.0) / 360.0 * worldSize;
		double y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSize;

		return new Point2D.Double(x, y);
	}

	private double[] worldPixelToLatLon(double x, double y, int zoom) {

		double worldSize = worldSize(zoom);

		double lon = x / worldSize * 360.0 - 180.0;

		double n = Math.PI - 2.0 * Math.PI * y / worldSize;
		double lat = Math.toDegrees(Math.atan(Math.sinh(n)));

		return new double[] { lat, lon };
	}

	double[] screenToLatLon(int screenX, int screenY, int viewWidth, int viewHeight, double centerLat, double centerLon,
			int zoom) {

		Point2D.Double centerWorld = latLonToWorldPixel(centerLat, centerLon, zoom);

		double worldX = centerWorld.x + (screenX - viewWidth / 2.0);
		double worldY = centerWorld.y + (screenY - viewHeight / 2.0);

		return worldPixelToLatLon(worldX, worldY, zoom);
	}

	Point latLonToScreen(double lat, double lon, int viewWidth, int viewHeight, double centerLat, double centerLon,
			int zoom) {

		Point2D.Double world = latLonToWorldPixel(lat, lon, zoom);
		Point2D.Double centerWorld = latLonToWorldPixel(centerLat, centerLon, zoom);

		int screenX = (int) Math.round((world.x - centerWorld.x) + viewWidth / 2.0);
		int screenY = (int) Math.round((world.y - centerWorld.y) + viewHeight / 2.0);

		return new Point(screenX, screenY);
	}
}
