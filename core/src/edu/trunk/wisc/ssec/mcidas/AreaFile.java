
//
// AreaFile.java
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

package edu.wisc.ssec.mcidas;

import java.applet.Applet;
import java.io.*;
import java.lang.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/** 
 * AreaFile interface with McIDAS 'area' file format image data.
 * This will allow 'area' format data to be read from disk; the
 * navigation block is made available (see navGVAR for example).
 *
 * This implementation does not do calibration (other than
 * accounting for its presence in the data).  Also, the 'valcode'
 * is not checked on each line.
 *
 * @authors - Tom Whittaker & Tommy Jasmin at SSEC
 * 
 */

public class AreaFile {

  // indeces used by this and client classes
  public static final int AD_STATUS     = 0;
  public static final int AD_VERSION    = 1;
  public static final int AD_SENSORID   = 2;
  public static final int AD_IMGDATE    = 3;
  public static final int AD_IMGTIME    = 4;
  public static final int AD_STLINE     = 5;
  public static final int AD_STELEM     = 6;
  public static final int AD_NUMLINES   = 8;
  public static final int AD_NUMELEMS   = 9;
  public static final int AD_DATAWIDTH  = 10;
  public static final int AD_LINERES    = 11;
  public static final int AD_ELEMRES    = 12;
  public static final int AD_NUMBANDS   = 13;
  public static final int AD_PFXSIZE    = 14;
  public static final int AD_PROJNUM    = 15;
  public static final int AD_CRDATE     = 16;
  public static final int AD_CRTIME     = 17;
  public static final int AD_BANDMAP    = 18;
  public static final int AD_DATAOFFSET = 33;
  public static final int AD_NAVOFFSET  = 34;
  public static final int AD_VALCODE    = 35;
  public static final int AD_STARTSCAN  = 47;
  public static final int AD_DOCLENGTH  = 48;
  public static final int AD_CALLENGTH  = 49;
  public static final int AD_LEVLENGTH  = 50;
  public static final int AD_SRCTYPE    = 51;
  public static final int AD_CALTYPE    = 52;
  public static final int AD_AVGSMPFLAG = 53;
  public static final int AD_AUXOFFSET  = 59;
  public static final int AD_CALOFFSET  = 62;
  public static final int AD_DIRSIZE    = 64;

  // load protocol handler for ADDE URLs
  static {
    URL.setURLStreamHandlerFactory(new AddeURLStreamHandlerFactory());
  }

  private boolean flipwords;
  private boolean fileok;
  private boolean hasReadData = false;
  private DataInputStream af;
  private int status=0;
  private int navLoc, calLoc, auxLoc, datLoc;
  private int navbytes, calbytes, auxbytes;
  private int linePrefixLength, lineDataLength, lineLength, numberLines;
  private final int McMISSING = 0x80808080;
  private long position;
  private int skipByteCount;
  private long newPosition;
  private URL url;
  private int numBands;
  int[] dir;
  int[] nav;
  int[] cal;
  int[][][] data;
  final int DMSP = 0x444d5250;
  final int GVAR = 0x47564152;
  final int POES = 0x5449524f;
  
  /**
   * creates an AreaFile object that allows reading
   * of McIDAS 'area' file format image data.  allows reading
   * either from a disk file, or a server via ADDE.
   *
   * @param imageSource the file name or ADDE URL to read from
   *
   * @exception AreaFileException if file cannot be opened
   *
   */
 
  public AreaFile(String imageSource) throws AreaFileException {
 
    // try as a disk file first
    try {
      af = new DataInputStream (
        new BufferedInputStream(new FileInputStream(imageSource), 2048)
      );
    } catch (IOException eIO) {
      // if opening as a file failed, try as a URL
      URL url;
      try {
        url = new URL(imageSource);
        URLConnection urlc = url.openConnection();
        InputStream is = urlc.getInputStream();
        af = new DataInputStream(is);
      }
      catch (Exception e) {
        fileok = false;
        throw new AreaFileException("Error opening AreaFile: " + e);
      }
    }
    fileok=true;
    position = 0;
    readMetaData();
  }
 
  /**
   * creates an AreaFile object that allows reading
   * of McIDAS 'area' file format image data from an applet
   *
   * @param filename the disk filename (incl path) to read from
   * @param parent the parent applet 
   *
   * @exception AreaFileException if file cannot be opened
   *
   */

  public AreaFile(String filename, Applet parent) throws AreaFileException {

    try {
      url = new URL(parent.getDocumentBase(), filename);
    } catch (MalformedURLException e) {
      System.out.println(e);
    }

    try { 
      af = new DataInputStream(url.openStream());
    } catch (IOException e) {
	fileok = false;
	throw new AreaFileException("Error opening AreaFile:"+e);
    }
    fileok=true;
    position = 0;
    readMetaData();
 
  }

  /**
   * creates an AreaFile object that allows reading
   * of McIDAS 'area' file format image data from a URL
   *
   * @param URL - the URL to go after
   *
   * @exception AreaFileException if file cannot be opened
   *
   */

  public AreaFile(URL url) throws AreaFileException {

    try { 
      af = new DataInputStream(url.openStream());
    } catch (IOException e) {
	fileok = false;
	throw new AreaFileException("Error opening URL for AreaFile:"+e);
    }
    fileok=true;
    position = 0;
    readMetaData();
  }
    
  /** Read the metadata for an area file (directory, nav,
   *  and cal). 
   *
   * @exception AreaFileException if there is a problem
   * reading any portion of the metadata.
   *
   */

  private void readMetaData() throws AreaFileException {
    
    int i;

    if (! fileok) {
      throw new AreaFileException("Error reading AreaFile directory");
    }

    dir = new int[AD_DIRSIZE];

    for (i=0; i < AD_DIRSIZE; i++) {
      try { dir[i] = af.readInt();
      } catch (IOException e) {
	status = -1;
	throw new AreaFileException("Error reading AreaFile directory:" + e);
      }
    }
    position += AD_DIRSIZE * 4;

    // see if the directory needs to be byte-flipped

    if (dir[AD_VERSION] > 255) {
      flip(dir,0,19);
      // word 20 may contain characters -- if small integer, flip it...
      if ( (dir[20] & 0xffff) == 0) flip(dir,20,20);
      flip(dir,21,23);
      // words 24-31 contain memo field
      flip(dir,32,50);
      // words 51-2 contain cal info
      flip(dir,53,55);
      // word 56 contains original source type (ascii)
      flip(dir,57,63);
      flipwords = true;
    }

    // pull together some values needed by other methods
    navLoc = dir[AD_NAVOFFSET];
    calLoc = dir[AD_CALOFFSET];
    auxLoc = dir[AD_AUXOFFSET];
    datLoc = dir[AD_DATAOFFSET];
    numBands = dir[AD_NUMBANDS];
    linePrefixLength = 
      dir[AD_DOCLENGTH] + dir[AD_CALLENGTH] + dir[AD_LEVLENGTH];
    if (dir[AD_VALCODE] != 0) linePrefixLength = linePrefixLength + 4;
    if (linePrefixLength != dir[AD_PFXSIZE]) 
      throw new AreaFileException("Invalid line prefix length in AREA file.");
    lineDataLength = numBands * dir[AD_NUMELEMS] * dir[AD_DATAWIDTH];
    lineLength = linePrefixLength + lineDataLength;
    numberLines = dir[AD_NUMLINES];

    if (datLoc > 0 && datLoc != McMISSING) {
      navbytes = datLoc - navLoc;
      calbytes = datLoc - calLoc;
      auxbytes = datLoc - auxLoc;
    }
    if (auxLoc > 0 && auxLoc != McMISSING) {
      navbytes = auxLoc - navLoc;
      calbytes = auxLoc - calLoc;
    }

    if (calLoc > 0 && calLoc != McMISSING ) {
      navbytes = calLoc - navLoc;
    }


    // Read in nav block

    if (navLoc > 0 && navbytes > 0) {

      nav = new int[navbytes/4];

      newPosition = (long) navLoc;
      skipByteCount = (int) (newPosition - position);
      try {
        af.skipBytes(skipByteCount);
      } catch (IOException e) {
	status = -1;
	throw new AreaFileException("Error skipping AreaFile bytes: " + e);
      }

      for (i=0; i<navbytes/4; i++) {
	try { nav[i] = af.readInt();
	} catch (IOException e) {
	  status = -1;
	  throw new AreaFileException("Error reading AreaFile navigation:"+e);
	}
      }
      if (flipwords) flipnav(nav);
      position = navLoc + navbytes;
    }


    // Read in cal block

    if (calLoc > 0 && calbytes > 0) {

      cal = new int[calbytes/4];

      newPosition = (long)calLoc;
      skipByteCount = (int) (newPosition - position);
      try {
        af.skipBytes(skipByteCount);
      } catch (IOException e) {
	status = -1;
	throw new AreaFileException("Error skipping AreaFile bytes: " + e);
      }

      for (i=0; i<calbytes/4; i++) {
	try { cal[i] = af.readInt();
	} catch (IOException e) {
	  status = -1;
	  throw new AreaFileException("Error reading AreaFile calibration:"+e);
	}
      }
      // if (flipwords) flipcal(cal);
      position = calLoc + calbytes;
    }


    // now return the Dir, as requested...
    status = 1;
    return;
  }

  /** returns the directory block
   *
   * @return an integer array containing the area directory
   *
   * @exception AreaFileException if there was a problem
   * reading the directory
   *
   */

  public int[] getDir() throws AreaFileException {


    if (status <= 0) {
      throw new AreaFileException("Error reading AreaFile directory");
    }

    return dir;

  }

  /** returns the navigation block
   *
   * @return an integer array containing the nav block data
   *
   * @exception AreaFileException if there is a problem
   * reading the navigation
   *
   */

  public int[] getNav() throws AreaFileException {


    if (status <= 0) {
      throw new AreaFileException("Error reading AreaFile navigation");
    }

    if (navLoc <= 0 || navLoc == McMISSING) {
      throw new AreaFileException("Error reading AreaFile navigation");
    } 

    return nav;

  }

  /** Returns calibration block
   *
   * @return an integer array containing the nav block data
   *
   * @exception AreaFileException if there is a problem
   * reading the navigation
   *
   */

  public int[] getCal() throws AreaFileException {


    if (status <= 0) {
      throw new AreaFileException("Error reading AreaFile calibration");
    }

    if (navLoc <= 0 || navLoc == McMISSING) {
      throw new AreaFileException("Error reading AreaFile calibration");
    } 

    return cal;

  }
  /**
   * Read the AREA file and return the entire contents
   *
   * @exception AreaFileException if there is a problem
   *
   * @return int array[band][lines][elements]
   *
   */

  public int[][][] getData() throws AreaFileException {
    if (!hasReadData) readData();
    return data;
  }

  /**
   * Read the specified 2-dimensional array of
   * data values from the AREA file.  Values will always be returned
   * as int regardless of whether they are 1, 2, or 4 byte values.
   *
   * @param lineNumber the file-relative image line number that will
   * be put in array[0][j]

   * @param eleNumber the file-relative image element number that will
   * be put into array[i][0] 
   *
   * @param numLines the number of lines to return
   *
   * @param numEles the number of elements to return for each line
   *
   * @param bandNumber the spectral band to return (def=1)
   *
   * @exception AreaFileException if the is a problem reading the file
   *
   * @return int array[lines][elements] with data values.
   *
   */

  public int[][] getData(int lineNumber, int eleNumber, int
	 numLines, int numEles) throws AreaFileException {
   return getData(lineNumber, eleNumber, numLines, numEles, 1);
  }


  public int[][] getData(int lineNumber, int eleNumber, int
         numLines, int numEles, int bandNumber) throws AreaFileException {

    // note band numbers are 1-based, and data offsets are 0-based
    if (!hasReadData) readData();
    int[][] subset = new int[numLines][numEles];
    for (int i=0; i<numLines; i++) {
      int ii = i + lineNumber;
      for (int j=0; j<numEles; j++) {
	int jj = j + eleNumber;
	if (ii < 0 || ii > (dir[AD_NUMLINES] - 1) || 
            jj < 0 || jj > (dir[AD_NUMELEMS] - 1) ) {
	  subset[i][j] = 0;
	} else {
	  subset[i][j] = data[bandNumber - 1][ii][jj];
	}
      }
    }
    return subset;
  }

  private void readData() throws AreaFileException {

    int i,j,k;
    int numLines = dir[AD_NUMLINES], numEles = dir[AD_NUMELEMS];

    if (! fileok) {
      throw new AreaFileException("Error reading AreaFile data");
    }

    data = new int[numBands][numLines][numEles];

    for (i = 0; i<numLines; i++) {

      try {
        newPosition = (long) (datLoc +
	       linePrefixLength + i*lineLength) ;
        skipByteCount = (int) (newPosition - position);
        af.skipBytes(skipByteCount);
        position = newPosition;

      } catch (IOException e) {
	 for (j = 0; j<numEles; j++) {
	   for (k=0; k<numBands; k++) {data[k][i][j] = 0;}
	 }
        break;
      }

      for (j = 0; j<numEles; j++) {

	for (k=0; k<numBands; k++) {

	  if (j > lineDataLength) {
	    data[k][i][j] = 0;
	  } else {

	    try {
	      if (dir[AD_DATAWIDTH] == 1) {
		data[k][i][j] = (int) af.readByte();
		if (data[k][i][j] < 0) data[k][i][j] += 256;
		position = position + 1;
	      }
	      if (dir[AD_DATAWIDTH] == 2) {
		data[k][i][j] = (int) af.readShort();
		position = position + 2;
	      }
	      if (dir[AD_DATAWIDTH] == 4) {
		data[k][i][j] = af.readInt();
		position = position + 4;
	      }
	    } 
	    catch (IOException e) {data[k][i][j] = 0;}
	  }
	}
      }

    }

    return ;

  } // end of areaReadData method

  /**
   *  flip the bytes of an integer array
   *
   * @param array[] array of integers to be flipped
   * @param first starting element of the array
   * @param last last element of array to flip
   *
   */

  private void flip(int array[], int first, int last) {
    int i,k;
    for (i=first; i<=last; i++) {
      k = array[i];
      array[i] = ( (k >>> 24) & 0xff) | ( (k >>> 8) & 0xff00) |
		 ( (k & 0xff) << 24 )  | ( (k & 0xff00) << 8);
    }
  }

  /**
   * selectively flip the bytes of words in nav block
   *
   * @param array[] of nav parameters
   *
   */

  private void flipnav(int[] nav) {

    // first word is always the satellite id in ASCII
    // check on which type:

    if (nav[0] == GVAR) {

      flip(nav,2,126);
      flip(nav,129,254);
      flip(nav,257,382);
      flip(nav,385,510);
      flip(nav,513,638);
    }

    else if (nav[0] == DMSP) {
      flip(nav,1,43);
      flip(nav,45,51);
    }

    else if (nav[0] == POES) {
      flip(nav,1,119);
    }

    else {
      flip(nav,1,nav.length-1);
    }

    return;
  }



}
