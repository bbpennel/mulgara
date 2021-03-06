/*
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is the Kowari Metadata Store.
 *
 * The Initial Developer of the Original Code is Plugged In Software Pty
 * Ltd (http://www.pisoftware.com, mailto:info@pisoftware.com). Portions
 * created by Plugged In Software Pty Ltd are Copyright (C) 2001,2002
 * Plugged In Software Pty Ltd. All Rights Reserved.
 *
 * Contributor(s): N/A.
 *
 * [NOTE: The text of this Exhibit A may differ slightly from the text
 * of the notices in the Source Code files of the Original Code. You
 * should use the text of this Exhibit A rather than the text found in the
 * Original Code Source Code for Your Modifications.]
 *
 */

package org.mulgara.util.conversion.html;


// Java 2 standard packages
import java.io.*;
import java.util.*;

/**
 * Tests the {@link HtmlToTextConverter}.
 *
 * @created 2002-08-01
 *
 * @author Ben Warren
 *
 * @version $Revision: 1.9 $
 *
 * @modified $Date: 2005/01/05 04:59:30 $
 *
 * @maintenanceAuthor $Author: newmana $
 *
 * @company <A href="mailto:info@PIsoftware.com">Plugged In Software</A>
 *
 * @copyright &copy;2002 <a href="http://www.pisoftware.com/">Plugged In
 *      Software Pty Ltd</a>
 *
 * @licence <a href="{@docRoot}/../../LICENCE">Mozilla Public License v1.1</a>
 */
public class HtmlToTextConverterTester {

  /**
   * Test the HtmlTextConverter.
   *
   * @param args The command line args.
   * @throws Exception on error.
   */
  public static void main(String[] args) throws Exception {

    // Use normal spaces instead of non-breaking unicode spaces
    HtmlToTextConverter.setUseNormalSpace(true);

    // Don't include titles
    HtmlToTextConverter.setIncludeTitle(false);

    // Don't include image alts
    HtmlToTextConverter.setIncludeImageAlts(false);

    // Directory
    if ("-dir".equals(args[0])) {

      String[] files = new File(args[1]).list();
      Arrays.sort(files);

      for (int i = 0; i < files.length; i++) {

        System.err.println(files[i]);

        File file = new File(args[1], files[i]);
        convertFile(file);
      }
    }

    // One file
    else {

      File file = new File(args[0]);
      convertFile(file);
    }
  }

  /**
   * Convert a HTML file to text.
   *
   * @param file The file to convert.
   * @throws Exception on error.
   */
  private static void convertFile(File file) throws Exception {

    // Convert file to a string
    StringBuffer lines = new StringBuffer();
    BufferedReader reader = new BufferedReader(new FileReader(file));

    for (String line = reader.readLine(); line != null;
        line = reader.readLine()) {

      lines.append(line + "\n");
    }

    System.out.println("\nConverting file " + file.getAbsolutePath() + "\n\n");
    System.out.println(HtmlToTextConverter.convert(lines.toString()));
    System.out.println("======================================================" +
      "=======================================================\n");
  }
}
