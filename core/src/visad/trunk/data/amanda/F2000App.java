/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 2002 Bill Hibbard, Curtis Rueden, Tom
Rink, Dave Glowacki, Steve Emmerson, Tom Whittaker, Don Murray, and
Tommy Jasmin.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.

You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
MA 02111-1307, USA
*/

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.IOException;

import java.net.URL;

import java.rmi.RemoteException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import visad.CellImpl;
import visad.Data;
import visad.DataReferenceImpl;
import visad.Display;
import visad.DisplayImpl;
import visad.DisplayRenderer;
import visad.FieldImpl;
import visad.GraphicsModeControl;
import visad.Integer1DSet;
import visad.Real;
import visad.ScalarMap;
import visad.ShapeControl;
import visad.Tuple;
import visad.VisADException;

import visad.data.amanda.AmandaFile;
import visad.data.amanda.EventWidget;
import visad.data.amanda.F2000Util;
import visad.data.amanda.TrackWidget;

import visad.java3d.DisplayImplJ3D;

import visad.util.LabeledColorWidget;
import visad.util.VisADSlider;

class DisplayFrame
  extends WindowAdapter
{
  private Display display;

  DisplayFrame(String title, Display display, JPanel panel)
    throws VisADException, RemoteException
  {
    this.display = display;

    JFrame frame = new JFrame(title);

    frame.addWindowListener(this);
    frame.getContentPane().add(panel);
    frame.pack();
    panel.invalidate();

    Dimension fSize = frame.getSize();

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setLocation((screenSize.width - fSize.width)/2,
                      (screenSize.height - fSize.height)/2);

    frame.setVisible(true);
  }

  public void windowClosing(WindowEvent evt)
  {
    try { display.destroy(); } catch (Exception e) { }
    System.exit(0);
  }
}

class DisplayMaps
{
  ScalarMap trackmap, shapemap, letmap;

  DisplayMaps(AmandaFile file, Display display)
    throws RemoteException, VisADException
  {
    // compute x, y and z ranges with unity aspect ratios
    final double xrange = file.getXMax() - file.getXMin();
    final double yrange = file.getYMax() - file.getYMin();
    final double zrange = file.getZMax() - file.getZMin();
    final double halfrange = -0.5 * Math.max(xrange, Math.max(yrange, zrange));
    final double xmid = 0.5 * (file.getXMax() + file.getXMin());
    final double ymid = 0.5 * (file.getYMax() + file.getYMin());
    final double zmid = 0.5 * (file.getZMax() + file.getZMin());
    final double xmin = xmid - halfrange;
    final double xmax = xmid + halfrange;
    final double ymin = ymid - halfrange;
    final double ymax = ymid + halfrange;
    final double zmin = zmid - halfrange;
    final double zmax = zmid + halfrange;

    ScalarMap xmap = new ScalarMap(AmandaFile.getXType(), Display.XAxis);
    display.addMap(xmap);
    xmap.setRange(xmin, xmax);

    ScalarMap ymap = new ScalarMap(AmandaFile.getYType(), Display.YAxis);
    display.addMap(ymap);
    ymap.setRange(ymin, ymax);

    ScalarMap zmap = new ScalarMap(AmandaFile.getZType(), Display.ZAxis);
    display.addMap(zmap);
    zmap.setRange(zmin, zmax);

    // ScalarMap eventmap = new ScalarMap(file.getEventIndex(),
    //                                    Display.SelectValue);
    // display.addMap(eventmap);

    this.trackmap = new ScalarMap(AmandaFile.getTrackIndexType(),
                                  Display.SelectValue);
    display.addMap(this.trackmap);

    // ScalarMap energymap = new ScalarMap(energy, Display.RGB);
    // display.addMap(energymap);

    this.shapemap = new ScalarMap(AmandaFile.getAmplitudeType(),
                                  Display.Shape);
    display.addMap(this.shapemap);

    ScalarMap shapeScalemap = new ScalarMap(AmandaFile.getAmplitudeType(),
                                             Display.ShapeScale);
    display.addMap(shapeScalemap);
    shapeScalemap.setRange(-20.0, 50.0);

    this.letmap = new ScalarMap(AmandaFile.getLeadEdgeTimeType(),
                                Display.RGB);
    display.addMap(this.letmap);
  }
}

/** run 'java F2000App in_file' to display data.<br>
 *  try 'java F2000App 100events.r'
 */
public class F2000App
{
  public static void main(String args[])
         throws VisADException, RemoteException, IOException
  {
    if (args == null || args.length != 1) {
      System.out.println("to test read an F2000 file, run:");
      System.out.println("  'java F2000App in_file'");
      System.exit(1);
      return;
    }

    AmandaFile file;
    if (args[0].startsWith("http://")) {
      // with "ftp://" this throws "sun.net.ftp.FtpProtocolException: RETR ..."
      file = new AmandaFile(new URL(args[0]));
    } else {
      file = new AmandaFile(args[0]);
    }

    Data temp = file.makeData();

    final FieldImpl amanda = (FieldImpl )((Tuple )temp).getComponent(0);
    final FieldImpl modules = (FieldImpl )((Tuple )temp).getComponent(1);

    DisplayImpl display = new DisplayImplJ3D("amanda");

    DisplayMaps maps = new DisplayMaps(file, display);

    // GraphicsModeControl mode = display.getGraphicsModeControl();
    // mode.setScaleEnable(true);
    DisplayRenderer displayRenderer = display.getDisplayRenderer();
    displayRenderer.setBoxOn(false);

    ShapeControl scontrol = (ShapeControl )maps.shapemap.getControl();
    scontrol.setShapeSet(new Integer1DSet(AmandaFile.getAmplitudeType(), 1));
    scontrol.setShapes(F2000Util.getCubeArray());

    final int nevents = amanda.getLength();

    // fixes track display?
    // SelectValue bug?
    // amanda = ((FieldImpl )amanda).getSample(99);

    final DataReferenceImpl amandaRef = new DataReferenceImpl("amanda");
    // data set by eventWidget below
    display.addReference(amandaRef);

    final DataReferenceImpl modulesRef = new DataReferenceImpl("modules");
    modulesRef.setData(modules);
    display.addReference(modulesRef);

/*
    LabeledColorWidget energyWidget = new LabeledColorWidget(energymap);
    widgetPanel.add(energyWidget);
*/

    LabeledColorWidget letWidget = new LabeledColorWidget(maps.letmap);
    // align along left side, to match VisADSlider alignment
    //   (if we don't left-align, BoxLayout hoses everything)
    letWidget.setAlignmentX(Component.LEFT_ALIGNMENT);

    EventWidget eventWidget = new EventWidget(file, amanda, amandaRef,
                                              maps.trackmap);

    JPanel widgetPanel = new JPanel();
    widgetPanel.setLayout(new BoxLayout(widgetPanel, BoxLayout.Y_AXIS));
    widgetPanel.setMaximumSize(new Dimension(400, 600));

    widgetPanel.add(letWidget);
    // widgetPanel.add(new VisADSlider(eventmap));
    widgetPanel.add(eventWidget);
    widgetPanel.add(Box.createHorizontalGlue());

    JPanel displayPanel = (JPanel )display.getComponent();
    Dimension dim = new Dimension(800, 800);
    displayPanel.setPreferredSize(dim);
    displayPanel.setMinimumSize(dim);

    // if widgetPanel alignment doesn't match
    //  displayPanel alignment, BoxLayout will freak out
    widgetPanel.setAlignmentX(displayPanel.getAlignmentX());
    widgetPanel.setAlignmentY(displayPanel.getAlignmentY());

    // create JPanel in frame
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

    panel.add(widgetPanel);
    panel.add(displayPanel);

    new DisplayFrame("VisAD AMANDA Viewer", display, panel);
  }
}
