
//
// TestCluster.java
//

/*
VisAD system for interactive analysis and visualization of numerical
data.  Copyright (C) 1996 - 2000 Bill Hibbard, Curtis Rueden, Tom
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


/* Cluster Design Ideas

Everything is via RMI - no 'local' Impl

a Data object is partitioned if any Field in it has a
partitioned domain

interfaces:
  Thing
    Data
      RemoteData (extends Remote, Data, RemoteThing)
        RemoteClusterData
          RemoteClientData
            RemoteClientTuple (extends RemoteTupleIface)
            RemoteClientField (extends RemoteField)
            RemoteClientPartitionedField (extends RemoteField)
          RemoteNodeData
            RemoteNodeTuple (extends RemoteTupleIface)
            RemoteNodeField (extends RemoteField)
            RemoteNodePartitionedField (extends RemoteField)

classes:
  UnicastRemoteObject
    RemoteThingImpl
      RemoteDataImpl
        RemoteClusterDataImpl
          RemoteClientDataImpl
            RemoteClientTupleImpl
            RemoteClientFieldImpl
            RemoteClientPartitionedFieldImpl
          RemoteNodeDataImpl
            RemoteNodeTupleImpl
            RemoteNodeFieldImpl
            RemoteNodePartitionedFieldImpl


RemoteClientPartitionedFieldImpl.getDomainSet() return UnionSet
of getDomainSet() returns from each node

add TupleIface extends Data (Tuple implements TupleIface)
and RemoteTupleIface extends TupleIface

a non-partitioned Data object is local on the client
  that is, a DataImpl

a partitioned Data object is a RemoteClientDataImpl on the
cient connected to RemodeNodeDataImpl's on the nodes

NodeAgent, Serializable class sent from client to each node
gets a Thread on arrival at node, return value from send of
NodeAgent is RemoteAgentContact (and Impl)
values from NodeAgent back declared Serializable

  abstract class NodeAgent implements Serializable, Runnable
    void sendToClient(Serializable message)
      invokes RemoteClientAgent.sendToClient(message)
    RemoteAgentContactImpl getRemoteAgentContact()
  interface RemoteAgentContact extends Remote
  class RemoteAgentContactImpl implements RemoteAgentContact
  interface RemoteClientAgent extends Remote
    void sendToClient(Serializable message)
  abstract class RemoteClientAgentImpl implements RemoteClientAgent
  class DefaultNodeRendererAgent extends NodeAgent
    void run()

  interface RemoteNodeData
    RemoteAgentContact sendAgent(NodeAgent agent)

  NodeRendererJ3D(NodeAgent agent)
  NodeRendererJ3D.doTransform()
    invokes agent.sendToClient(VisADGroup branch)


see page 60 of Java Enterprise in a Nutshell
no easy way to load RMI classes - security issues


partitioned data on client has similar data trees on
client and nodes
  leaf node on client is:
    DataImpl
    Field with partitioned domain (RemoteClientPartitionedFieldImpl)
  non-leaf node on client is:
    Tuple (RemoteClientTupleImpl)
    Field with non-partitioned domain (RemoteClientFieldImpl)
  leaf tree-nodes on cluster-node is:
    Field with partitioned domain
      (RemoteNodePartitionedFieldImpl adapting FlatField)
  non-leaf tree-nodes on cluster-node is:
    Tuple (RemoteNodeTupleImpl)
    Field with non-partitioned domain (RemoteNodeFieldImpl)
    Field with partitioned domain
      (RemoteNodePartitionedFieldImpl adapting FieldImpl)

every object in data tree on client connects to objects
in data trees on nodes

may use DisplayImplJ3D on nodes for graphics, with api = TRANSFORM_ONLY
  and DisplayRenderer = NodeDisplayRendererJ3D (extends
  TransformOnlyDisplayRendererJ3D) doesn't render to screen
uses special DisplayImplJ3D constructor signature (conflict?)
  for cluster - modified version of collaborative Display

NodeRendererJ3D extends DefaultRendererJ3D, with
ShadowNode*TypeJ3D - addToGroup() etc to leave as Serializable
  note must replace 'Image image' in VisADAppearance
ClientRendererJ3D extends DefaultRendererJ3D, not even using
ShadowTypes, but assembling VisADSceneGraphs from nodes




may also need way for client to signal implicit resolution
reduction to nodes - custom DataRenderers with custon ShadowTypes
whose doTransforms resample down, then call super.doTransform()
with downsampled data


Control field in ScalarMap is marked transient and dglo9.txt
says it should be.  But can use the getSaveString() and
setSaveString() methods of Control to transmit Control states.


cluster design should include a native VisAD Data Model on
binary files, via serialization, for an implementation of
FileFlatField on nodes

also need to support FileFlatFields

*/

/* VisAD Data Model on various file formats

Data instance method for write
Data static method for read
a parameter to these methods is a file-format-specific
implementation of a FileIO interface, that is used for
low level I/O (should deal with missing data in
file-format-specific way)

other interfaces for constructing appropriate file-format-
specific structures for Tuple, Field, FlatField, Set, Real,
Text, RealTuple, CoordinateSystem, Unit, ErrorEstimate

get review from Steve on this

*/


package visad.cluster;

import visad.*;
import visad.java3d.*;
import visad.data.gif.GIFForm;

import java.rmi.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
   TestCluster is the class for testing the visad.cluster package.<P>
*/
public class TestCluster extends Object {

  public TestCluster() {
  }

  public static void main(String[] args)
         throws RemoteException, VisADException {
    if (args == null || args.length < 1) {
      throw new ClusterException("must specify GIF file name argument");
    }
    GIFForm gif_form = new GIFForm();
    FlatField image = (FlatField) gif_form.open(args[0]);
/*
new Integer2DSet(imageDomain, nelements, nlines));
MathType.stringToType("((ImageElement, ImageLine) -> ImageRadiance)");
*/

    int node_divide = 1;
    int number_of_nodes = node_divide * node_divide;

    FunctionType image_type = (FunctionType) image.getType();
    RealTupleType domain_type = image_type.getDomain();
    Linear2DSet domain_set = (Linear2DSet) image.getDomainSet();
    Linear1DSet x_set = domain_set.getX();
    Linear1DSet y_set = domain_set.getY();
    // getFirst, getLast, getStep, getLength:q
    int x_length = x_set.getLength();
    int y_length = y_set.getLength();
    Linear2DSet ps =
      new Linear2DSet(domain_type,
                      x_set.getFirst(), x_set.getLast(), node_divide,
                      y_set.getFirst(), y_set.getLast(), node_divide,
                      domain_set.getCoordinateSystem(),
                      domain_set.getSetUnits(), null);

    Linear2DSet[] subsets = new Linear2DSet[number_of_nodes];


    RemoteNodePartitionedFieldImpl[] node_images =
      new RemoteNodePartitionedFieldImpl[number_of_nodes];

    node_images[0] = new RemoteNodePartitionedFieldImpl(image);

    RemoteClusterData[] table =
      new RemoteClusterData[number_of_nodes + 1];
    for (int i=0; i<number_of_nodes; i++) {
      table[i] = node_images[i];
    }
    RemoteClientPartitionedFieldImpl client_image =
      new RemoteClientPartitionedFieldImpl(image_type, domain_set);
    table[number_of_nodes] = client_image;

    for (int i=0; i<number_of_nodes; i++) {
      node_images[i].setupClusterData(ps, table);
    }

    DisplayImpl display =
      new DisplayImplJ3D("display");
      // new DisplayImplJ3D("display", new ClientDisplayRendererJ3D());

    // get a list of decent mappings for this data
    MathType type = image.getType();
    ScalarMap[] maps = type.guessMaps(true);

    // add the maps to the display
    for (int i=0; i<maps.length; i++) {
      display.addMap(maps[i]);
    }

    // link data to the display
    DataReferenceImpl ref = new DataReferenceImpl("image");
    ref.setData(image);
    display.addReference(ref);

    // create JFrame (i.e., a window) for display and slider
    JFrame frame = new JFrame("test ClientRendererJ3D");
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {System.exit(0);}
    });

    // create JPanel in JFrame
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
    panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
    frame.getContentPane().add(panel);

    // add display to JPanel
    panel.add(display.getComponent());

    // set size of JFrame and make it visible
    frame.setSize(500, 500);
    frame.setVisible(true);
  }


/*
    Real r = new Real(0);
    RemoteClientTupleImpl cd = new RemoteClientTupleImpl(new Data[] {r});
    RemoteClientTupleImpl cd2 = new RemoteClientTupleImpl(new Data[] {r});
    System.out.println(cd.equals(cd)); // true
    System.out.println(cd.equals(cd2)); // false
    System.out.println(cd.clusterDataEquals(cd)); // true
    System.out.println(cd.clusterDataEquals(cd2)); // false
    System.exit(0);
*/

}

