/**
 * Copyright (C) 2026, Harlan Murphy
 * Use of this source code is governed by a BSD Zero Clause License
 * 
 * See project license.txt for details
*/

package orbisoftware.osm_gps_map;

public class SharedData {

	public static boolean debug = false;
	public static boolean incrementGPS = false;
	public static boolean applicationClosing = false;
	public static String comPort = null;
	
	private float prevHeading = 0.0f;
	private LatLon prevCoord = null;
	private LatLon currCoord = null;
	private double distance = 0.0;
	
	private static final SharedData instance = new SharedData();

	private SharedData() {
	}

	public static SharedData getInstance() {
		return instance;
	}

	public synchronized void setPrevCoord(LatLon prevCoord) {

		this.prevCoord = prevCoord;
	}

	public synchronized LatLon getPrevCoord() {

		return prevCoord;
	}

	public synchronized void setCurrCoord(LatLon currCoord) {

		this.currCoord = currCoord;
	}

	public synchronized LatLon getCurrCoord() {

		return currCoord;
	}
	
	public synchronized void setHeading(float prevHeading) {
		
		this.prevHeading = prevHeading;
	}
	
	public synchronized float getHeading() {
		
		return prevHeading;
	}
	
	public synchronized void setDistBetweenPoints(double distance) {
		
		this.distance = distance;
	}
	
	public synchronized double getDistBetweenPoints() {
		
		return distance;
	}
}
