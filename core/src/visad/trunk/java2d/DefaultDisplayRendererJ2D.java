
//
// DefaultDisplayRendererJ2D.java
//

/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 1998 Bill Hibbard, Curtis Rueden, Tom
Rink and Dave Glowacki.
 
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 1, or (at your option)
any later version.
 
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License in file NOTICE for more details.
 
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package visad.java2d;

import visad.*;

import java.awt.event.*;

import javax.media.j3d.*;
import javax.vecmath.*;

import java.util.*;


/**
   DefaultDisplayRendererJ2D is the VisAD class for the default background
   and metadata rendering algorithm under Java3D.<P>
*/
public class DefaultDisplayRendererJ2D extends DisplayRendererJ2D {

  /** color of box  */
  ColoringAttributes box_color = null; // J2D
  MouseBehaviorJ2D mouse = null; // Behavior for mouse interactions

  public DefaultDisplayRendererJ2D () {
    super();
  }

  /** create scene graph root, if none exists, with Transform
      and direct manipulation root;
      create 3-D box, lights and MouseBehaviorJ2D for
      embedded user interface */
  public BranchGroup createSceneGraph(View v, Canvas3D c) { // J2D
    BranchGroup root = createBasicSceneGraph(v, c); // J2D
    if (mouse != null) return root;
    TransformGroup trans = getTrans(); // J2D

    // create the box containing data depictions
    LineArray box_geometry = new LineArray(24, LineArray.COORDINATES); // J2D
    box_geometry.setCoordinates(0, box_verts);
    Appearance box_appearance = new Appearance();
    box_color = new ColoringAttributes();
    box_color.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
    box_color.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
    box_color.setColor(1.0f, 1.0f, 1.0f);
    box_appearance.setColoringAttributes(box_color);
    Shape3D box = new Shape3D(box_geometry, box_appearance);
    // first child of trans
    trans.addChild(box);

    BranchGroup cursor_on = getCursorOnBranch(); // J2D
    LineArray cursor_geometry = new LineArray(6, LineArray.COORDINATES); // J2D
    cursor_geometry.setCoordinates(0, cursor_verts);
    Shape3D cursor = new Shape3D(cursor_geometry, box_appearance); // J2D
    cursor_on.addChild(cursor);
 
    // create the Behavior for mouse interactions
    ProjectionControl proj = getDisplay().getProjectionControl();

    // create MouseBehaviorJ2D
    mouse = new MouseBehaviorJ2D(this);
    BoundingSphere bounds =
      new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0); // J2D
    mouse.setSchedulingBounds(bounds); // J2D
    trans.addChild(mouse); // J2D

    // create ambient light, directly under root (not transformed)
/* WLH 27 Jan 98
    Color3f color = new Color3f(0.4f, 0.4f, 0.4f);
*/
    Color3f color = new Color3f(0.6f, 0.6f, 0.6f);
    AmbientLight light = new AmbientLight(color);
    light.setInfluencingBounds(bounds);
    root.addChild(light);
 
    // create directional lights, directly under root (not transformed)
    Color3f dcolor = new Color3f(0.9f, 0.9f, 0.9f);
    Vector3f direction1 = new Vector3f(0.0f, 0.0f, 1.0f);
    Vector3f direction2 = new Vector3f(0.0f, 0.0f, -1.0f);
    DirectionalLight light1 =
      new DirectionalLight(true, dcolor, direction1);
    light1.setInfluencingBounds(bounds);
    DirectionalLight light2 =
      new DirectionalLight(true, dcolor, direction2);
    light2.setInfluencingBounds(bounds);
    root.addChild(light1);
    root.addChild(light2);
 
    return root;
  }

  private static final float[] box_verts = {
     // front face
         -1.0f, -1.0f,  1.0f,                       -1.0f,  1.0f,  1.0f,
         -1.0f,  1.0f,  1.0f,                        1.0f,  1.0f,  1.0f,
          1.0f,  1.0f,  1.0f,                        1.0f, -1.0f,  1.0f,
          1.0f, -1.0f,  1.0f,                       -1.0f, -1.0f,  1.0f,
     // back face
         -1.0f, -1.0f, -1.0f,                       -1.0f,  1.0f, -1.0f,
         -1.0f,  1.0f, -1.0f,                        1.0f,  1.0f, -1.0f,
          1.0f,  1.0f, -1.0f,                        1.0f, -1.0f, -1.0f,
          1.0f, -1.0f, -1.0f,                       -1.0f, -1.0f, -1.0f,
     // connectors
         -1.0f, -1.0f,  1.0f,                       -1.0f, -1.0f, -1.0f,
         -1.0f,  1.0f,  1.0f,                       -1.0f,  1.0f, -1.0f,
          1.0f,  1.0f,  1.0f,                        1.0f,  1.0f, -1.0f,
          1.0f, -1.0f,  1.0f,                        1.0f, -1.0f, -1.0f
  };

  private static final float[] cursor_verts = {
          0.0f,  0.0f,  0.1f,                        0.0f,  0.0f, -0.1f,
          0.0f,  0.1f,  0.0f,                        0.0f, -0.1f,  0.0f,
          0.1f,  0.0f,  0.0f,                       -0.1f,  0.0f,  0.0f
  };

}

