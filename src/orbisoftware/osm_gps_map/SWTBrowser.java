/**
 * Copyright (C) 2026, Harlan Murphy
 * Use of this source code is governed by a BSD Zero Clause License
 * 
 * See project license.txt for details
*/

package orbisoftware.osm_gps_map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import java.util.Locale;

public class SWTBrowser {

	private Browser browser;
	private GPSLatLonReader gpsLatLonReader;

	public static void main(String[] args) {

		if (args.length == 1)
			SharedData.comPort = args[0];
		
		SWTBrowser browser = new SWTBrowser();
		browser.mainApplication();
	}
	
	private int countNumberPoints(String url) {

		int count = StringUtils.countMatches(url, "&loc=");

		return count;
	}

	private void mainApplication() {

		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setText("Open Street Map GPS Router");
		shell.setSize(1000, 700);
		shell.setLayout(new FillLayout());

		gpsLatLonReader = new GPSLatLonReader();
		gpsLatLonReader.displayPortInfo();
		gpsLatLonReader.start();

		browser = new Browser(shell, SWT.EDGE);
		String targetUrl = "https://map.project-osrm.org/?z=16&center=28.527353%2C-81.242641&hl=en&alt=0&srv=1";
		browser.setUrl(targetUrl);

		// Add a listener for the SWT.Close event
        shell.addListener(SWT.Close, new Listener() {
            @Override
            public void handleEvent(Event event) {

            	SharedData.applicationClosing = true;
            }
        });

		browser.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {

				String carIconUrl = "https://github.com/harlan3/webhost/blob/main/img/small_car.png?raw=true";

                String setupScript = String.format(Locale.US,
                		
                        "// Create fixed overlay container%n" +
                        "let container = document.getElementById('swt-overlay-container');%n" +
                        "if (!container) {%n" +
                        "    container = document.createElement('div');%n" +
                        "    container.id = 'swt-overlay-container';%n" +
                        "    container.style.position = 'fixed';%n" +
                        "    container.style.top = '0';%n" +
                        "    container.style.left = '0';%n" +
                        "    container.style.width = '100%%';%n" +
                        "    container.style.height = '100%%';%n" +
                        "    container.style.pointerEvents = 'none';%n" +
                        "    container.style.zIndex = '999999';%n" +
                        "    document.body.appendChild(container);%n" +
                        "}%n%n" +         

                        "// Create car icon (img element)%n" +
                        "let carIcon = document.getElementById('car-icon');%n" +
                        "if (!carIcon) {%n" +
                        "    carIcon = document.createElement('img');%n" +
                        "    carIcon.id = 'car-icon';%n" +
                        "    carIcon.src = '%s';%n" +
                        "    carIcon.style.position = 'absolute';%n" +
                        "    carIcon.style.width = '35px';" +
                        "    carIcon.style.height = '17px';%n" +
                        "    container.appendChild(carIcon);%n" +
                        "}%n%n" +

                        "// Function to apply rotation to car%n" +
                        "window.updateCarHeading = function(deg) {%n" +
                        "    carIcon.style.transform = `rotate(${deg}deg)`;%n" +
                        "};%n%n",
						carIconUrl);

				browser.execute(setupScript);

				// Main browser execution code
				display.timerExec(1000, new Runnable() {
					@Override
					public void run() {

						if (shell.isDisposed() || browser.isDisposed())
							return;
						
						// Execute the run method again later once a valid GPS point is available
						if (SharedData.getInstance().getCurrCoord() == null) {
							display.timerExec(1000, this);
							return;
						}

						// Extract the zoom level out of the current URL
						String currentUrl = browser.getUrl();
						int numberPoints = countNumberPoints(currentUrl);
						currentUrl = currentUrl.replaceAll("#z=", "\\?z="); // Sometime comes back incorrectly with hash
						String split1[] = currentUrl.split("\\?z=");
						String split2[] = split1[1].split("&center=");
						String split3[], split4[];
						int zoomLevel = Integer.parseInt(split2[0]);
						double centerLat, centerLon;

						if (numberPoints == 0) {
							String baseString = split2[1].replaceAll("&hl=en&alt=0&srv=1", "");
							split3 = baseString.split("%2C");
							centerLat = Double.parseDouble(split3[0]);
							centerLon = Double.parseDouble(split3[1]);
						} else {
							split3 = split2[1].split("&loc=");
							split4 = split3[0].split("%2C");
							centerLat = Double.parseDouble(split4[0]);
							centerLon = Double.parseDouble(split4[1]);
						}

						int screenWidth = shell.getSize().x;
						int screenHeight = shell.getSize().y;

						java.awt.Point carIconPoint = gpsLatLonReader.latLonToScreen(
								SharedData.getInstance().getCurrCoord().latitude,
								SharedData.getInstance().getCurrCoord().longitude, screenWidth, screenHeight, centerLat,
								centerLon, zoomLevel);

						// Offset based on car image width and height
						carIconPoint.x -= 17;
						carIconPoint.y -= 35;
						
						Display.getDefault().asyncExec(() -> {
							
							if (browser != null && !browser.isDisposed()) {
								
								final double distBetweenPoints = SharedData.getInstance().getDistBetweenPoints();
								final float carHeading = SharedData.getInstance().getHeading();

								if (SharedData.debug) {
									
									System.out.println("carIconPoint.x = " + carIconPoint.x);
									System.out.println("carIconPoint.y = " + carIconPoint.y);
									System.out.println("carHeading = " + SharedData.getInstance().getHeading());
									System.out.println("distBetweenPoints = " + distBetweenPoints);
									System.out.println();
								}
								
								// Safe: Use Locale.US for decimal separator
								String js = String.format(Locale.US,
										"window.updateCarHeading(" + carHeading + ");%n"
										+ "let carIcon = document.getElementById('car-icon');%n" 
												+ "if (carIcon) {%n"
												+ "    carIcon.style.visibility = 'visible';%n"
												+ "    carIcon.style.left = '" + carIconPoint.x + "px';%n"
												+ "    carIcon.style.top = '" + carIconPoint.y + "px';%n" + "}%n");

								try {
									browser.evaluate(js);
								} catch (Exception e ) { }
							}
						});

						display.timerExec(1000, this);
					}
				});
			}
		});

		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		
		display.dispose();
		
		try {
			Thread.sleep(1000);
			System.exit(0);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}